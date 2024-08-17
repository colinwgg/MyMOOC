package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.Constant;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.aspectj.weaver.ast.Or;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    private final UserClient userClient;
    private final RemarkClient remarkClient;

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

    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean forAdmin) {
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题或回答id不能都为空");
        }
        // 分页查询
        Boolean isQueryAnswer = questionId != null;
        Page<InteractionReply> page = lambdaQuery()
                .eq(isQueryAnswer, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, isQueryAnswer ? 0L : answerId)
                .eq(!forAdmin, InteractionReply::getHidden, false)
                .page(query.toMpPage(
                        new OrderItem(Constant.DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(Constant.DATA_FIELD_NAME_CREATE_TIME, false)
                ));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 数据处理 提问者信息 目标回复 回复用户
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();
        for (InteractionReply r : records) {
            if (!r.getAnonymity() || forAdmin) {
                userIds.add(r.getUserId());
            }
            answerIds.add(r.getAnswerId());
            targetReplyIds.add(r.getTargetReplyId());
        }
        // 查询目标回复 若不匿名or管理端 查询目标用户
        targetReplyIds.remove(0L);
        targetReplyIds.remove(null);
        if (!targetReplyIds.isEmpty()) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity).or(r -> forAdmin))
                    .map(InteractionReply::getId)
                    .collect(Collectors.toSet());
            userIds.addAll(targetUserIds);
        }
        // 查询用户
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (!userIds.isEmpty()) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);
        // 处理VO
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            ReplyVO vo = BeanUtils.copyBean(r, ReplyVO.class);
            voList.add(vo);
            // 回复人信息
            if (!r.getAnonymity() || forAdmin) {
                UserDTO user = userMap.get(r.getUserId());
                if (user != null) {
                    vo.setUserIcon(user.getIcon());
                    vo.setUserName(user.getName());
                    vo.setUserType(user.getType());
                }
            }
            // 目标用户信息
            if (r.getTargetUserId() != null) {
                UserDTO user = userMap.get(r.getTargetUserId());
                if (user != null) {
                    vo.setTargetUserName(user.getName());
                }
            }
            vo.setLiked(bizLiked.contains(r.getId()));
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void hiddenReply(Long id, Boolean hidden) {
        InteractionReply old = getById(id);
        if (old == null) {
            return;
        }
        // 更新回答
        InteractionReply reply = new InteractionReply();
        reply.setHidden(hidden);
        reply.setId(id);
        updateById(reply);
        // 判断是否为回答 如果是则更新下属评论hidden状态
        if (old.getAnswerId() != null && old.getAnswerId() != 0) {
            return;
        }
        lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getAnswerId, id)
                .update();
    }
}
