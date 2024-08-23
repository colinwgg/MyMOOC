package com.tianji.learning.handler;

import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService pointsBoardService;

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
}
