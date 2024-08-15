package com.tianji.learning.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;

    @Override
    public void saveReply(ReplyDTO replyDTO) {
        Long userId = UserContext.getUser();
        // 新增回答
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);
        // 判断是回答或评论
        boolean isAnswer = replyDTO.getAnswerId() == null;
        if (!isAnswer) {
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, replyDTO.getAnswerId())
                    .update();
        }
        // 更新问题表
        questionService.lambdaUpdate()
                .set(isAnswer, InteractionQuestion::getLatestAnswerId, reply.getAnswerId())
                .setSql(isAnswer, "answer_times = answer_times + 1")
                .set(replyDTO.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK.getValue())
                .eq(InteractionQuestion::getId, replyDTO.getQuestionId())
                .update();
    }
}
