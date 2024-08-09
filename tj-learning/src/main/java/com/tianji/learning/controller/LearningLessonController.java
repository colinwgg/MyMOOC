package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author colinwang
 * @since 2024-08-06
 */
@Api(tags = "我的课表相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @ApiOperation("分页查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(@Validated PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @ApiOperation("查询我正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLessons() {
        return lessonService.queryMyCurrentLessons();
    }

    @GetMapping("/{courseId}")
    @ApiOperation("根据courseId查询课程信息")
    public LearningLessonVO queryByCourseId(@PathVariable("courseId") Long courseId) {
        return lessonService.queryByCourseId(courseId);
    }

    @GetMapping("/lessons/{courseId}/valid")
    @ApiOperation("检查课程是否有效")
    public Long isLessonValid(@PathVariable("courseId") Long courseId) {
        return lessonService.isLessonValid(courseId);
    }

    @DeleteMapping("/{courseId}")
    @ApiOperation("根据courseId删除课程")
    public void deleteCourseFromLesson(@PathVariable("courseId") Long courseId) {
        lessonService.deleteCourseFromLesson(courseId);
    }

    @GetMapping("/{courseId}/count")
    @ApiOperation("统计课程学习人数")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId) {
        return lessonService.countLearningLessonByCourse(courseId);
    }

    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO) {
        lessonService.createLearningPlans(planDTO.getCourseId(), planDTO.getFreq());
    }
}
