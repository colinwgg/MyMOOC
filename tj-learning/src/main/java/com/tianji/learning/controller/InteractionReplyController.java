package com.tianji.learning.controller;


import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
@Api(tags = "回答和评论相关接口")
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @PostMapping
    @ApiOperation("新增回答或评论")
    public void saveReply(ReplyDTO replyDTO) {
        replyService.saveReply(replyDTO);
    }
}
