package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;

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

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 1.处理课程名称，得到课程id
        String courseName = query.getCourseName();
        List<Long> courseIds = null;
        if (StringUtils.isNotBlank(courseName)) {
           courseIds = searchClient.queryCoursesIdByName(courseName);
           if (CollUtils.isEmpty(courseIds)) {
               return PageDTO.empty(0L, 0L);
           }
        }
        // 2.分页查询
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.准备VO需要的数据：用户数据、课程数据、章节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 3.1.获取各种数据的id集合
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }
        // 3.2.根据id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 3.3.根据id查询课程
        List<CourseSimpleInfoDTO> courses = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> courseMap = new HashMap<>(courses.size());
        if (CollUtils.isNotEmpty(courses)) {
            courseMap = courses.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }
        // 3.4.根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        // 4.封装VO
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            voList.add(vo);
            // 用户信息
            UserDTO user = userMap.get(q.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
            }
            // 课程信息 分类信息
            CourseSimpleInfoDTO course = courseMap.get(q.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(course.getCategoryIds()));
            }
            // 章节信息
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void hiddenQuestion(Long id, Boolean hidden) {
        InteractionQuestion question = new InteractionQuestion();
        question.setId(id);
        question.setHidden(hidden);
        updateById(question);
    }

    @Override
    @Transactional
    public QuestionAdminVO queryQuestionByIdAdmin(Long id) {
        InteractionQuestion question = getById(id);
        if (question == null) {
            return null;
        }
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        // 提问者信息
        UserDTO user = userClient.queryUserById(question.getUserId());
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        // 课程信息
        CourseFullInfoDTO course = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        if (course != null) {
            // 课程名称
            vo.setCourseName(course.getName());
            // 分类信息
            vo.setCategoryName(categoryCache.getCategoryNames(course.getCategoryIds()));
            // 教师信息
            List<Long> teacherIds = course.getTeacherIds();
            List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
            if (CollUtils.isNotEmpty(teachers)) {
                vo.setTeacherName(teachers.stream().map(UserDTO::getName).collect(Collectors.joining("/")));
            }
        }
        // 章节信息
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(List.of(question.getChapterId(), question.getSectionId()));
        Map<Long, String> cataMap = new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        vo.setChapterName(cataMap.getOrDefault(question.getChapterId(), ""));
        vo.setSectionName(cataMap.getOrDefault(question.getSectionId(), ""));
        // 更新问题表状态为已查看
        lambdaUpdate()
                .set(InteractionQuestion::getStatus, QuestionStatus.CHECKED.getValue())
                .eq(InteractionQuestion::getId, id)
                .update();
        return vo;
    }
}
