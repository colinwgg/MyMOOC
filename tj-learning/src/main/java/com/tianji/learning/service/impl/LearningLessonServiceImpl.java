package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-06
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOS = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseSimpleInfoDTOS)) {
            throw new DbException("课程信息不存在，无法添加到课表");
        }

        List<LearningLesson> list = new ArrayList<>(courseSimpleInfoDTOS.size());
        for (CourseSimpleInfoDTO course : courseSimpleInfoDTOS) {
            LearningLesson learningLesson = new LearningLesson();
            Integer validDuration = course.getValidDuration();
            learningLesson.setExpireTime(LocalDateTime.now().plusMonths(validDuration));
            learningLesson.setCourseId(course.getId());
            learningLesson.setUserId(userId);
            list.add(learningLesson);
        }

        saveBatch(list);
    }
}
