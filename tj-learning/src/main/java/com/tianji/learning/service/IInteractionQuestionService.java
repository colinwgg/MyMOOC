package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;

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

    /**
     * 修改问题
     * @param id
     * @param questionDTO
     */
    void updateQuestion(Long id, QuestionFormDTO questionDTO);

    /**
     * 分页查询互动问题
     * @param query
     * @return
     */
    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);
}
