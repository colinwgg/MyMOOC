package com.tianji.learning.service;

import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    /**
     * 新增提问
     * @param questionDTO
     */
    void saveQuestion(QuestionFormDTO questionDTO);
}
