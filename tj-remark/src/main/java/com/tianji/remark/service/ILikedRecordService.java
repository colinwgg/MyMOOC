package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-16
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    /**
     * 点赞或取消点赞
     * @param recordDTO
     */
    void addLikeRecord(LikeRecordFormDTO recordDTO);

    /**
     * 查询指定业务id的点赞状态
     * @param bizIds
     * @return
     */
    Set<Long> isBizLiked(List<Long> bizIds);
}
