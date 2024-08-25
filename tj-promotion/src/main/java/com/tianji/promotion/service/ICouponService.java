package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-24
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO dto);

    PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query);

    void beginIssue(CouponIssueFormDTO dto);
}
