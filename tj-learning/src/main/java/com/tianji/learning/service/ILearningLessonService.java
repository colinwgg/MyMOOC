package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-06
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    /**
     * 添加课程到课表
     * @param userId
     * @param courseIds
     */
    void addUserLessons(Long userId, List<Long> courseIds);

    /**
     * 分页查询我的课表
     * @param query
     * @return
     */
    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    /**
     * 查询我正在学习的课程
     * @return
     */
    LearningLessonVO queryMyCurrentLessons();

    /**
     * 根据courseId查询课程信息
     * @param courseId
     * @return
     */
    LearningLessonVO queryByCourseId(Long courseId);

    /**
     * 检查课程是否有效
     * @param courseId
     * @return
     */
    Long isLessonValid(Long courseId);

    /**
     * 根据courseId删除课程
     * @param courseId
     */
    void deleteCourseFromLesson(Long courseId);

    /**
     * 统计课程学习人数
     * @param courseId
     */
    Integer countLearningLessonByCourse(Long courseId);

    /**
     * 根据userId和courseId查询
     * @param userId
     * @param courseId
     * @return
     */
    LearningLesson queryByUserAndCourseId(Long userId, Long courseId);

    /**
     * 创建学习计划
     * @param courseId
     * @param freq
     */
    void createLearningPlans(Long courseId, Integer freq);
}
