package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author colinwang
 * @since 2024-08-19
 */
@Api(tags = "积分相关接口")
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class PointsBoardController {

    private final IPointsBoardService pointsBoardService;
    private final IPointsBoardSeasonService seasonService;

    @ApiOperation("查询赛季信息列表")
    @GetMapping("/seasons/list")
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons(PointsBoardQuery query) {
        return seasonService.queryPointsBoardSeasons(query);
    }

    @GetMapping
    @ApiOperation("分页查询指定赛季的积分排行榜")
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        return pointsBoardService.queryPointsBoardBySeason(query);
    }
}
