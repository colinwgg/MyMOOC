package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.task.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate redisTemplate;

    // @Scheduled(cron = "0 0 3 1 * ?") // 每月1号，凌晨3点执行
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        // 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 获取赛季id
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            return;
        }
        pointsBoardService.createPointsBoardTableBySeason(season);
    }

    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        // 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 查询赛季信息
        Integer season = seasonService.querySeasonByTime(time);
        // 将动态表名存入ThreadLocal
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + season);
        // 查询榜单数据
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1; // 起始页，就是分片序号+1
        int pageSize = 10;
        while (true) {
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            // 持久化到数据库
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            pointsBoardService.saveBatch(boardList);
            pageNo += total;
        }
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        // 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.unlink(key);
    }
}
