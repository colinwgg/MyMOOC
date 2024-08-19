package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.enums.PointsRecordType;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-19
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(Long userId, Integer points, PointsRecordType pointsRecordType);
}
