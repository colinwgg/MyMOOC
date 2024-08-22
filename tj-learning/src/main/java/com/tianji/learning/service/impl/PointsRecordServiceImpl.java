package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-19
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    public final StringRedisTemplate redisTemplate;

    @Override
    public void addPointsRecord(Long userId, Integer points, PointsRecordType type) {
        // 查询积分上限
        int maxPoints = type.getMaxPoints();
        LocalDateTime now = LocalDateTime.now();
        int realPoints = points;
        if (maxPoints > 0) {
            // 判断是否超出上限
            LocalDateTime begin = DateUtils.getDayStartTime(now);
            LocalDateTime end = DateUtils.getDayEndTime(now);
            int currentPoints = queryUserPointsByTypeAndDate(userId, type, begin, end);
            if (currentPoints >= maxPoints) {
                return;
            }
            if (currentPoints + points > maxPoints) {
                realPoints = maxPoints - currentPoints;
            }
        }
        PointsRecord p = new PointsRecord();
        p.setUserId(userId);
        p.setPoints(realPoints);
        p.setType(type);
        save(p);
        // 更新总积分到redis
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), realPoints);
    }

    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        Long userId = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        // 构建查询条件
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .between(PointsRecord::getCreateTime, begin, end);
        List<PointsRecord> list = getBaseMapper().queryUserPointsByDate(wrapper);
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 封装vo
        List<PointsStatisticsVO> voList = new ArrayList<>(list.size());
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());
            vo.setPoints(p.getPoints());
            vo.setMaxPoints(p.getType().getMaxPoints());
            voList.add(vo);
        }
        return voList;
    }

    private int queryUserPointsByTypeAndDate(Long userId, PointsRecordType type, LocalDateTime begin, LocalDateTime end) {
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .eq(type != null, PointsRecord::getType, type)
                .between(begin != null && end != null, PointsRecord::getCreateTime, begin, end);
        Integer points = getBaseMapper().queryUserPointsByTypeAndDate(wrapper);
        return points == null ? 0 : points;
    }
}
