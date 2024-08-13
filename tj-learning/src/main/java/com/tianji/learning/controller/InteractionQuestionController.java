package com.tianji.learning.controller;


import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
@Api(tags = "互动提问相关接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @PostMapping
    @ApiOperation("新增提问")
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionDTO) {
        questionService.saveQuestion(questionDTO);
    }
}
