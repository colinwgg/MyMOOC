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
}
