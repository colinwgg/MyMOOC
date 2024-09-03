package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Override
    @Transactional
    @Lock(name = "lock:coupon:#{userId}")
    public void receiveCoupon(Long couponId) {
        // 校验优惠券是否存在，不存在无法领取
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) { throw new BadRequestException("优惠券不存在"); }
        // 校验优惠券的发放时间，是不是正在发放中
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 校验优惠券剩余库存是否充足
        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("优惠券库存不足");
        }
        Long userId = UserContext.getUser();
        // 校验每人限领数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        if (count > coupon.getUserLimit()) {
            throw new BadRequestException("超出领取数量");
        }
        // 扣减优惠券库存
        redisTemplate.opsForHash().increment(
                PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId, "totalNum", -1
        );
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
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if (exchanged) {
            throw new BizIllegalException("兑换码已经被兑换过了");
        }
        try {
            // 查询兑换码对应的优惠券id
            ExchangeCode exchangeCode = codeService.getById(serialNum);
            if (exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在！");
            }
            Long couponId = exchangeCode.getExchangeTargetId();
            // 是否过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(exchangeCode.getExpiredTime())) {
                throw new BizIllegalException("兑换码已经过期");
            }
            // 校验并生成用户券
            Coupon coupon = couponMapper.selectById(couponId);
            if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime())) {
                throw new BizIllegalException("优惠券活动未开始或已经结束");
            }
            Long userId = UserContext.getUser();
            checkAndCreateUserCoupon(coupon, userId, serialNum);
        } catch (Exception e) {
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }
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
