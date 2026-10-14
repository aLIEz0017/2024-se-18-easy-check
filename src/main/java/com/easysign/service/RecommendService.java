package com.easysign.service;

import com.easysign.dto.response.EventResponse;
import com.easysign.dto.response.PageResponse;

/**
 * 推荐 Service 接口（接口编号 7.x）🔒 AI 禁飞区
 * <p>
 * 为当前用户生成个性化的活动推荐列表。
 * 基于用户标签与活动标签的匹配度及活动热度进行综合排序。
 * </p>
 */
public interface RecommendService {

    /**
     * 7.1 活动推荐
     * <p>
     * 根据用户标签和活动热度，返回推荐的活动列表（最多 10 条）。
     * 每个活动附带推荐原因。
     * </p>
     *
     * @param userId 当前用户 ID（未登录时为 null）
     * @param page   页码
     * @param size   每页条数
     * @return 推荐活动分页数据
     */
    PageResponse<EventResponse> recommendEvents(Long userId, int page, int size);
}
