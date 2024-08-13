package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author colinwang
 * @since 2024-08-13
 */
@Api(tags = "互动问答相关接口")
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

    @PutMapping("/{id}")
    @ApiOperation("修改问题")
    public void updateQuestion(@ApiParam("要修改的问题id") @PathVariable("id") Long id, @RequestBody QuestionFormDTO questionDTO) {
        questionService.updateQuestion(id, questionDTO);
    }

    @ApiOperation("分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        return questionService.queryQuestionPage(query);
    }
}
