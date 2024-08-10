package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.User;
import org.checkerframework.checker.index.qual.LTEqLengthOf;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;

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

    @Override
    public LearningLessonVO queryMyCurrentLessons() {
        Long userId = UserContext.getUser();
        // 查询正在学习的课程 select * from xx where user_id = #{userId} AND status = 1 order by latest_learn_time limit 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }

        // 拷贝PO基础属性到VO
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);

        // 查询课程信息
        CourseFullInfoDTO courseFullInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (courseFullInfo == null) {
            throw new DbException("课程不存在");
        }
        vo.setCourseName(courseFullInfo.getName());
        vo.setCourseCoverUrl(courseFullInfo.getCoverUrl());
        vo.setSections(courseFullInfo.getSectionNum());

        // 统计课表中的课程数量 select count(1) from xxx where user_id = #{userId}
        Integer amount = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(amount);

        // 查询小节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
            vo.setLatestSectionName(cataSimpleInfoDTO.getName());
            vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());
        }
        return vo;
    }

    @Override
    public LearningLessonVO queryByCourseId(Long courseId) {
        // 查询learning_lesson
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }

        // 根据courseId查询课程信息
        CourseFullInfoDTO course = courseClient.getCourseInfoById(courseId, false, false);
        if (course == null) {
            throw new DbException("课程信息不存在");
        }

        // 封装vo
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(course.getName());
        vo.setCourseCoverUrl(course.getCoverUrl());
        vo.setSections(course.getSectionNum());
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        CourseFullInfoDTO course = courseClient.getCourseInfoById(courseId, false, false);
        if (LocalDateTime.now().isAfter(course.getPurchaseEndTime())) {
            return null;
        }
        return lesson.getId();
    }

    @Override
    public void deleteCourseFromLesson(Long courseId) {
        Long userId = UserContext.getUser();
        lambdaUpdate()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .remove();
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.LEARNING.getValue(),
                        LessonStatus.NOT_BEGIN.getValue(),
                        LessonStatus.FINISHED.getValue())
                .count();
    }

    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {
        return getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    @Override
    public void createLearningPlans(Long courseId, Integer freq) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = queryByUserAndCourseId(userId, courseId);
        if (lesson == null) {
            throw new DbException("课表信息不存在");
        }
        LearningLesson updateLesson = new LearningLesson();
        updateLesson.setId(lesson.getId());
        updateLesson.setWeekFreq(freq);
        if (lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            updateLesson.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(updateLesson);
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO vo = new LearningPlanPageVO();
        Long userId = UserContext.getUser();
        // 获得本周开启和结束时间
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);

        // 查询本周计划统计
        // -本周已学完小节总数
        Integer weekFinishedCount = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getFinished, true)
                .eq(LearningRecord::getUserId, userId)
                .gt(LearningRecord::getFinishTime, weekBeginTime)
                .lt(LearningRecord::getFinishTime, weekEndTime));
        vo.setWeekFinished(weekFinishedCount);
        // -本周计划学习小节总数
        Integer weekTotalPlan = getBaseMapper().queryWeekTotalPlan(userId);
        vo.setWeekTotalPlan(weekTotalPlan);

        // 查询分页数据
        // 1-查询当前用户课表
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> lessonList = page.getRecords();
        if (CollUtils.isEmpty(lessonList)) {
            return vo.emptyPage(page);
        }

        // 2-根据courseId查询课程信息
        List<Long> courseIds = lessonList.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        List<CourseSimpleInfoDTO> courseList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseList)) {
            throw new DbException("课程信息不存在");
        }
        Map<Long, CourseSimpleInfoDTO> courseMap = courseList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        // 3-查询某个课程本周学习的小节数
        List<IdAndNumDTO> idAndNumList = recordMapper.countLearnedSections(userId, weekBeginTime, weekEndTime);
        Map<Long, Integer> idAndNumMap = IdAndNumDTO.toMap(idAndNumList);

        // 4-把课表list封装到voList
        List<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson lesson : lessonList) {
            LearningPlanVO planVO = BeanUtils.copyBean(lesson, LearningPlanVO.class);
            // 封装课程信息
            CourseSimpleInfoDTO course = courseMap.get(planVO.getCourseId());
            if (course != null) {
                planVO.setCourseName(course.getName());
                planVO.setSections(course.getSectionNum());
            }
            // 封装已经学习的小节数据
            Integer courseWeekLearnedSections = idAndNumMap.getOrDefault(planVO.getId(), 0);
            planVO.setWeekLearnedSections(courseWeekLearnedSections);
            voList.add(planVO);
        }

        return vo.pageInfo(page.getTotal(), page.getPages(), voList);
    }

    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId) {
        return new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
    }
}
