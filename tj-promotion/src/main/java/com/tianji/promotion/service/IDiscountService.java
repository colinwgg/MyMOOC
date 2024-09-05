package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface IDiscountService {

    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses);
}
