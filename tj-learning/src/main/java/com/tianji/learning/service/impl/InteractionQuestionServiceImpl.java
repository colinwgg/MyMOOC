package com.tianji.learning.service.impl;

import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
@Service
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        Long userId = UserContext.getUser();
        question.setUserId(userId);
        save(question);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionDTO) {
        Long userId = UserContext.getUser();
        InteractionQuestion oldQuestion = getById(id);
        if (oldQuestion == null) {
            throw new DbException("问题不存在");
        }
        if (!oldQuestion.getUserId().equals(userId)) {
            throw new DbException("无权修改他人的问题");
        }
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setId(id);
        updateById(question);
    }
}
