package com.tianji.learning.service.impl;

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
}
