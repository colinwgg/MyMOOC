package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
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
import java.util.Map;
import java.util.stream.Collectors;

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

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // 分页查询当前用户的课表信息
        Long userId = UserContext.getUser();
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<LearningLesson> lessonList = page.getRecords();
        if (CollUtils.isEmpty(lessonList)) {
            return PageDTO.empty(page);
        }

        // 根据courseId查询课程信息
        List<Long> courseIds = lessonList.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        List<CourseSimpleInfoDTO> list = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(list)) {
            throw new DbException("课程信息不存在");
        }
        Map<Long, CourseSimpleInfoDTO> courseMap = list.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c->c));

        // 遍历课表list, 封装learningLessonVO
        List<LearningLessonVO> lessonVOS = new ArrayList<>();
        for (LearningLesson lesson : lessonList) {
            LearningLessonVO lessonVO = BeanUtils.copyBean(lesson, LearningLessonVO.class);
            CourseSimpleInfoDTO dto = courseMap.get(lesson.getCourseId());
            lessonVO.setCourseName(dto.getName());
            lessonVO.setCourseCoverUrl(dto.getCoverUrl());
            lessonVO.setSections(dto.getSectionNum());
            lessonVOS.add(lessonVO);
        }

        // 封装pageDTO
        return PageDTO.of(page, lessonVOS);
    }
}
