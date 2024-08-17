package com.tianji.remark.service.impl;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-16
 */
@Service
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {

    }
}
