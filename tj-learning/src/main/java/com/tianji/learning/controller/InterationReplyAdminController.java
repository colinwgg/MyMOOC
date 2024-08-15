package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
@Api(tags = "管理端回答和评论相关接口")
@RestController
@RequestMapping("/admin/replies")
@RequiredArgsConstructor
public class InterationReplyAdminController {

    private final IInteractionReplyService replyService;

    @GetMapping("/page")
    @ApiOperation("分页查询回答或评论列表")
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        return replyService.queryReplyPage(query, true);
    }
}
