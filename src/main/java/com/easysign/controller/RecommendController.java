package com.easysign.controller;

import com.easysign.common.Result;
import com.easysign.context.UserContext;
import com.easysign.dto.response.EventResponse;
import com.easysign.dto.response.PageResponse;
import com.easysign.service.RecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.easysign.dto.response.PageResponse;
import com.easysign.dto.response.EventResponse;

import java.util.List;

/**
 * 活动推荐 Controller（接口编号 7.x）
 * <p>
 * 为已登录用户提供个性化活动推荐列表。
 * 基于用户标签与活动标签的匹配度、活动热度及历史参会标签匹配度进行综合排序。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/recommend")
public class RecommendController {

    @Autowired
    private RecommendService recommendService;

    /**
     * 7.1 活动推荐 🔒 AI 禁飞区
     * <p>
     * 返回推荐的 Top N 活动列表，每个活动附带推荐原因（{@link EventResponse#reason} 字段）。
     * 未登录用户按热度排序推荐。
     * </p>
     *
     * @param page 页码（保留分页扩展，实际忽略）
     * @param size 返回条数，默认 3（Top N）
     * @return 推荐活动分页数据
     */
    @GetMapping("/events")
    public Result<PageResponse<EventResponse>> recommendEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int size) {
        PageResponse<EventResponse> result = recommendService.recommendEvents(
                UserContext.getUserId(), page, size);
        return Result.success(result);
    }
}