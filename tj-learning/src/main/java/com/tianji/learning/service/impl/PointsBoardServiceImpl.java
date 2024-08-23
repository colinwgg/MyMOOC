package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-19
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final UserClient userClient;
    private final StringRedisTemplate redisTemplate;

    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        // 判断是否是查询当前赛季
        Long season = query.getSeason();
        boolean isCurrent = season == null || season == 0;
        // 获取Redis的Key
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 查询我的积分和排名
        PointsBoard myBoard = isCurrent ?
                queryMyCurrentBoard(key) : // 查询当前榜单（Redis）
                queryMyHistoryBoard(season); // 查询历史榜单（MySQL）
        // 查询榜单列表
        List<PointsBoard> list = isCurrent ?
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) :
                queryHistoryBoardList(query);
        PointsBoardVO vo = new PointsBoardVO();
        // 处理我的信息
        if (myBoard != null) {
            vo.setRank(myBoard.getRank());
            vo.setPoints(myBoard.getPoints());
        }
        if (CollUtils.isEmpty(list)) {
            return vo;
        }
        // 查询用户信息
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = new HashMap<>(uIds.size());
        if (CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        // 封装VO
        List<PointsBoardItemVO> items = new ArrayList<>(list.size());
        for (PointsBoard item : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            itemVO.setPoints(item.getPoints());
            itemVO.setRank(item.getRank());
            itemVO.setName(userMap.get(item.getUserId()));
            items.add(itemVO);
        }
        vo.setBoardList(items);
        return vo;
    }

    @Override
    public void createPointsBoardTableBySeason(Integer season) {
        getBaseMapper().createPointsBoardTable(POINTS_BOARD_TABLE_PREFIX + season);
    }

    private PointsBoard queryMyCurrentBoard(String key) {
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        String userId = UserContext.getUser().toString();
        Double points = ops.score(userId);
        Long rank = ops.reverseRank(userId);
        PointsBoard p = new PointsBoard();
        p.setPoints(points == null ? 0 : points.intValue());
        p.setRank(rank == null ? 0 : rank.intValue() + 1);
        return p;
    }

    private PointsBoard queryMyHistoryBoard(Long season) {
        return null;
    }

    @Override
    public List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize) {
        // 计算分页
        int from = (pageNo - 1) * pageSize;
        // 查询
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().
                reverseRangeWithScores(key, from, from + pageSize - 1);
        if (CollUtils.isEmpty(tuples)) {
            return CollUtils.emptyList();
        }
        // 封装
        List<PointsBoard> list = new ArrayList<>(tuples.size());
        int rank = from + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            PointsBoard board = new PointsBoard();
            String userId = tuple.getValue();
            Double points = tuple.getScore();
            if (userId == null || points == null) {
                continue;
            }
            board.setUserId(Long.valueOf(userId));
            board.setRank(rank++);
            board.setPoints(points.intValue());
            list.add(board);
        }
        return list;
    }

    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        return null;
    }
}
