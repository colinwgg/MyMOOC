package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-24
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;
    private final IExchangeCodeService codeSerive;
    private final CategoryCache categoryCache;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveCoupon(CouponFormDTO dto) {
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);
        // 限定范围
        if (!coupon.getSpecific()) {
            return;
        }
        Long couponId = coupon.getId();
        List<Long> scopes = dto.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空");
        }
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId))
                .collect(Collectors.toList());
        scopeService.saveBatch(list);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        String name = query.getName(); // 模糊查询
        Integer status = query.getStatus();
        Integer type = query.getType();
        Page<Coupon> page = lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, list);
    }

    @Override
    public void beginIssue(CouponIssueFormDTO dto) {
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 状态必须为待发放or暂停
        if (coupon.getStatus() != DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException("优惠券状态错误！");
        }
        // 是否立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isNow = issueBeginTime == null || now.isAfter(issueBeginTime);
        // 更新优惠券
        Coupon c = new Coupon();
        if (isNow) {
            c.setIssueBeginTime(now);
            c.setStatus(CouponStatus.ISSUING);
        } else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        updateById(c);

        // 添加缓存
        if (isNow) {
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        // 判断是否需要生成兑换码，优惠券类型必须是兑换码，优惠券状态必须是待发放
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeSerive.asyncGenerateCode(coupon);
        }
    }

    private void cacheCouponInfo(Coupon coupon) {
        // 1.组织数据
        Map<String, String> map = new HashMap<>(4);
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum", String.valueOf(coupon.getTotalNum()));
        map.put("userLimit", String.valueOf(coupon.getUserLimit()));
        // 2.写缓存
        redisTemplate.opsForHash().putAll(PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId(), map);
    }

    @Override
    public void deleteById(Long id) {
        Coupon coupon = getById(id);
        if (coupon == null || coupon.getStatus() != DRAFT) {
            throw new BadRequestException("优惠券不存在或者优惠券正在使用中");
        }
        boolean success = remove(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getId, id)
                .eq(Coupon::getStatus, DRAFT));
        if (!success) {
            throw new BadRequestException("优惠券不存在或者优惠券正在使用中");
        }
        if (!coupon.getSpecific()) {
            return;
        }
        scopeService.remove(new LambdaQueryWrapper<CouponScope>().eq(CouponScope::getCouponId, id));
    }

    @Override
    public CouponDetailVO queryCouponById(Long id) {
        Coupon coupon = getById(id);
        CouponDetailVO vo = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        if (vo == null || !coupon.getSpecific()) {
            return vo;
        }
        List<CouponScope> scopes = scopeService.lambdaQuery()
                .eq(CouponScope::getCouponId, id).list();
        if (CollUtils.isEmpty(scopes)) {
            return vo;
        }
        List<CouponScopeVO> scopeVOS = scopes.stream()
                .map(CouponScope::getBizId)
                .map(cateId -> new CouponScopeVO(cateId, categoryCache.getNameByLv3Id(cateId)))
                .collect(Collectors.toList());
        vo.setScopes(scopeVOS);
        return vo;
    }

    @Override
    public void pauseIssue(Long id) {
        // 查询旧优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 当前券状态必须是未开始或进行中
        CouponStatus status = coupon.getStatus();
        if (status != UN_ISSUE && status != ISSUING) {
            return;
        }
        // 更新状态
        boolean success = lambdaUpdate()
                .set(Coupon::getStatus, PAUSE)
                .eq(Coupon::getId, id)
                .in(Coupon::getStatus, UN_ISSUE, ISSUING)
                .update();
        if (!success) {
            log.error("重复暂停优惠券");
        }
        // 删除redis缓存
        redisTemplate.delete(PromotionConstants.COUPON_CACHE_KEY_PREFIX + id);
    }
}
