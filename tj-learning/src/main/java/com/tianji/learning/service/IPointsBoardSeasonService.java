package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-19
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    List<PointsBoardSeasonVO> queryPointsBoardSeasons(PointsBoardQuery query);

    Integer querySeasonByTime(LocalDateTime time);
}