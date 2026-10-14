package com.easysign.service;

import com.easysign.dto.request.ChangeEventStatusRequest;
import com.easysign.dto.request.CreateEventRequest;
import com.easysign.dto.request.UpdateEventRequest;
import com.easysign.dto.response.CreateEventResponse;
import com.easysign.dto.response.EventResponse;
import com.easysign.dto.response.PageResponse;

/**
 * 活动管理 Service 接口
 * <p>
 * 定义活动的 CRUD、状态变更及 AI 文案生成操作。
 * 每个方法对应接口规范中的编号，与 {@link com.easysign.controller.EventController} 配合使用。
 * </p>
 */
public interface EventService {

    /**
     * 4.1 发布活动
     * <p>
     * 校验活动时间约束（开始时间 > 当前时间、结束时间 > 开始时间）、
     * 门票总数范围，插入新活动记录，初始状态为 {@code draft}。
     * </p>
     *
     * @param userId 举办方用户 ID
     * @param req    活动信息（标题、描述、地点、时间、门票数）
     * @return 包含新活动 ID 和初始状态 {@code draft}
     */
    CreateEventResponse createEvent(Long userId, CreateEventRequest req);

    /**
     * 4.2 公开列表（scope=public）
     * <p>
     * 返回所有 {@code status = online} 的活动，按开始时间升序排列。
     * 支持按活动标题模糊搜索（keyword 为 null 或空时返回全部）。
     * </p>
     */
    PageResponse<EventResponse> listPublicEvents(int page, int size, String keyword);

    /**
     * 4.2 举办方列表（scope=organizer）
     * <p>
     * 返回指定举办方的所有活动，按更新时间降序排列。
     * </p>
     */
    PageResponse<EventResponse> listOrganizerEvents(Long userId, int page, int size);

    /**
     * 4.3 活动详情
     * <p>返回单个活动的完整信息，不存在时抛出 {@code 4003} 异常。</p>
     */
    EventResponse getEventById(Long eventId);

    /**
     * 4.4 更新活动
     * <p>
     * 举办方更新活动，支持部分字段更新（传 null 表示不修改）。
     * 约束：finished 不可改、online 不可改门票数、门票数不可小于已售数。
     * </p>
     */
    void updateEvent(Long userId, Long eventId, UpdateEventRequest req);

    /**
     * 4.5 变更状态
     * <p>
     * 举办方变更活动状态。状态流转规则：
     * <ul>
     *   <li>{@code draft → online}</li>
     *   <li>{@code online → offline}</li>
     *   <li>{@code offline → online}</li>
     * </ul>
     * </p>
     */
    void changeStatus(Long userId, Long eventId, ChangeEventStatusRequest req);

    /**
     * 4.6 删除活动
     * <p>
     * 举办方删除活动（仅 draft/offline 状态可删），关联 ticket 置为 invalid。
     * 操作使用 {@code @Transactional} 保证原子性。
     * </p>
     */
    void deleteEvent(Long userId, Long eventId);

    /**
     * 4.7 AI 一键生成文案 🔒 AI 禁飞区
     * <p>
     * 调用 Mock AI 服务为活动生成描述文案。若入参 {@code title} 为 null 或空白，
     * 则从事件信息中读取原标题作为生成基础。
     * </p>
     */
    String generateAiDescription(Long eventId, String title);
}