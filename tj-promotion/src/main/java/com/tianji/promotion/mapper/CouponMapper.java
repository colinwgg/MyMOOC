package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author colinwang
 * @since 2024-08-24
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("update coupon set issue_num = issue_num + 1 where id = #{id} and issue_num < total_num")
    int incrIssueNum(Long id);

    int incrUsedNum(List<Long> couponIds, int amount);
}
