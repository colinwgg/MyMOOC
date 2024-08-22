package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-19
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query);
}
