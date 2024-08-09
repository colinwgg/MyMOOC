package com.tianji.learning.service.impl;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
