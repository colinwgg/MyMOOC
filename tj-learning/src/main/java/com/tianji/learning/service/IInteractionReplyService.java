package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    /**
     * 新增回答或评论
     * @param replyDTO
     */
    void saveReply(ReplyDTO replyDTO);

    /**
     * 分页查询回答或评论列表
     * @param query
     * @param forAdmin
     * @return
     */
    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean forAdmin);
}
