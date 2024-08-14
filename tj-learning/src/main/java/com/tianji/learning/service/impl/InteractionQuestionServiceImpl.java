package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final InteractionReplyMapper replyMapper;
    private final UserClient userClient;

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

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        // 1.参数校验，课程id和小节id不能都为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if (courseId == null && sectionId == null) {
            throw new DbException("课程id和小节id不能都为空");
        }
        // 2.分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(InteractionQuestion::getCourseId, courseId)
                .eq(InteractionQuestion::getSectionId, sectionId)
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.根据id查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        for (InteractionQuestion q : records) {
            if (!q.getAnonymity()) {
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if (CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            }
        }
        // 根据id查询用户信息（提问者）
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 4.封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            QuestionVO vo = BeanUtils.copyBean(q, QuestionVO.class);
            vo.setUserId(null);
            voList.add(vo);
            // 封装提问者信息
            if (!q.getAnonymity()) {
                UserDTO userDTO = userMap.get(q.getUserId());
                if (userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }
            // 封装最近一次回答信息
            InteractionReply reply = replyMap.get(q.getLatestAnswerId());
            if (reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if (!reply.getAnonymity()) { // 封装回答用户信息
                    UserDTO userDTO = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(userDTO.getName());
                }
            }
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        InteractionQuestion question = getById(id);
        if (question == null || question.getHidden()) {
            return null;
        }
        UserDTO userDTO = null;
        if (!question.getAnonymity()) {
            userDTO = userClient.queryUserById(question.getUserId());
        }
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if (userDTO != null) {
            vo.setUserId(userDTO.getId());
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }
        return vo;
    }

    @Override
    public void deleteQuestionById(Long id) {
        // - 查询问题是否存在
        InteractionQuestion question = getById(id);
        if (question == null) {
            return;
        }
        // - 判断是否是当前用户提问的
        Long userId = UserContext.getUser();
        if (!question.getUserId().equals(userId)) {
            throw new BadRequestException("无权删除他人的问题");
        }
        // - 如果是则删除问题
        removeById(id);
        // - 然后删除问题下的回答及评论
        replyMapper.delete(
                new QueryWrapper<InteractionReply>().lambda().eq(InteractionReply::getQuestionId, id)
        );
    }
}
