package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-08
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 获取当前登录用户id
        Long userId = UserContext.getUser();
        // 根据courseId和userId查询LearningLesson
        LearningLesson lesson = lessonService.queryByUserAndCourseId(userId, courseId);
        // 判断是否存在或者是否过期，如果不存在或过期直接返回空，如果存在并且未过期，则继续
        if (lesson == null || lesson.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        // 查询lesson对应的所有学习记录
        List<LearningRecord> list = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId()).list();
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(list, LearningRecordDTO.class));
        return dto;
    }

    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO formDTO) {
        Long userId = UserContext.getUser();
        // 处理学习记录
        boolean finished = false;
        if (formDTO.getSectionType() == SectionType.EXAM) {
            // 处理考试
            finished = handleExam(userId, formDTO);
        } else {
            // 处理视频
            finished = handleVideo(userId, formDTO);
        }
        // 处理课表
        handleLesson(finished, formDTO);
    }

    private boolean handleExam(Long userId, LearningRecordFormDTO formDTO) {
        // 创建record对象
        LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
        // 设置属性(userId, 是否完成，完成时间)
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(formDTO.getCommitTime());
        // 保存到数据库
        boolean saved = save(record);
        if (!saved) {
            throw new DbException("保存考试记录失败");
        }
        return true;
    }

    private boolean handleVideo(Long userId, LearningRecordFormDTO formDTO) {
        // 查询旧的学习记录
        LearningRecord old = lambdaQuery()
                .eq(LearningRecord::getLessonId, formDTO.getLessonId())
                .eq(LearningRecord::getSectionId, formDTO.getSectionId())
                .one();
        // 判断是否存在
        if (old == null) {
            // 不存在 新增
            LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
            record.setUserId(userId);
            boolean saved = save(record);
            if (!saved) {
                throw new DbException("新增学习记录失败!");
            }
            return false;
        }
        // 存在 更新
        boolean finished = !old.getFinished() && formDTO.getMoment() * 2 > formDTO.getDuration();
        boolean updated = lambdaUpdate()
                .set(LearningRecord::getMoment, formDTO.getMoment())
                .set(finished, LearningRecord::getFinished, true)
                .set(finished, LearningRecord::getFinishTime, formDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId()).update();
        if (!updated) {
            throw new DbException("更新学习记录失败!");
        }
        return finished;
    }

    private void handleLesson(boolean finished, LearningRecordFormDTO formDTO) {
        // 根据id查询课表
        LearningLesson lesson = lessonService.getById(formDTO.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 是否学完，已学完section数+1
        boolean allFinished = false; // 全部学完
        Integer sections = 0;
        if (finished) {
            sections = lesson.getLearnedSections();
            CourseFullInfoDTO course = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (course == null) {
                throw new BizIllegalException("课程不存在，无法更新数据！");
            }
            allFinished = sections + 1 >= course.getSectionNum();
        }
        // 更新课表 （学习section数，课表状态，最近学习小节，最近学习时间）
        boolean updated = lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .set(LearningLesson::getLatestSectionId, formDTO.getSectionId())
                .set(LearningLesson::getLatestLearnTime, formDTO.getCommitTime())
                .set(finished, LearningLesson::getLearnedSections, sections + 1)
                .eq(LearningLesson::getId, lesson.getId()).update();
        if (!updated) {
            throw new DbException("更新失败");
        }
    }
}
