package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-30
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService codeService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
    private static final RedisScript<Long> RECEIVE_COUPON_SCRIPT;
    private static final RedisScript<String> EXCHANGE_COUPON_SCRIPT;

    static {
        RECEIVE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/receive_coupon.lua"), Long.class);
        EXCHANGE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/exchange_coupon.lua"), String.class);
    }

    @Override
    @Transactional
    @Lock(name = "lock:coupon:#{userId}")
    public void receiveCoupon(Long couponId) {
        // 执行LUA脚本
        // 准备参数
        String key1 = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        String key2 = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long userId = UserContext.getUser();
        // 执行脚本
        Long r = redisTemplate.execute(RECEIVE_COUPON_SCRIPT, List.of(key1, key2), userId);
        int result = NumberUtils.null2Zero(r).intValue();
        if (result != 0) {
            // 结果大于0，说明出现异常
            throw new BizIllegalException(PromotionConstants.RECEIVE_COUPON_ERROR_MSG[result - 1]);
        }
        // 发送MQ消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在！");
        }
        // 更新优惠券的已经发放的数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }
        // 新增一个用户券
        saveUserCoupon(coupon, uc.getUserId());
        // 更新兑换码状态
        if (uc.getSerialNum() != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, uc.getUserId())
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, uc.getSerialNum())
                    .update();
        }
    }

    @Override
    public PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query) {
        Long userId = UserContext.getUser();
        Integer status = query.getStatus();
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(status != null, UserCoupon::getStatus, status)
                .page(query.toMpPage(new OrderItem("term_end_time", true)));
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);
        return PageDTO.of(page, BeanUtils.copyList(coupons, CouponVO.class));
    }

    @Override
    @Lock(name = "lock:coupon:#{T(com.tianji.common.utils.UserContext).getUser()}")
    @Transactional
    public void exchangeCoupon(String code) {
        // 解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 执行LUA脚本
        Long userId = UserContext.getUser();
        String r = redisTemplate.execute(
                EXCHANGE_COUPON_SCRIPT,
                List.of(PromotionConstants.COUPON_CODE_MAP_KEY, PromotionConstants.COUPON_RANGE_KEY),
                String.valueOf(serialNum), String.valueOf(serialNum + 5000), userId.toString());
        long result = NumberUtils.parseLong(r);
        if (result < 10) {
            // 异常结果应该是在1~5之间
            throw new BizIllegalException(PromotionConstants.EXCHANGE_COUPON_ERROR_MSG[(int) (result - 1)]);
        }
        // 发送MQ消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setCouponId(result);
        uc.setUserId(userId);
        uc.setSerialNum((int) serialNum);
        mqHelper.send(
                MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                uc
        );
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        userCoupon.setTermBeginTime(termBeginTime);
        userCoupon.setTermEndTime(termEndTime);
        save(userCoupon);
    }

    private Coupon queryCouponByCache(Long couponId) {
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if (objMap.isEmpty()) {
            return null;
        }
        return BeanUtils.mapToBean(objMap, Coupon.class, false, CopyOptions.create());
    }
}
