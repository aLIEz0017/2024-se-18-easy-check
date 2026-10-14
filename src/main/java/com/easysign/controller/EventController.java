package com.easysign.controller;

import com.easysign.common.BizException;
import com.easysign.common.Result;
import com.easysign.context.UserContext;
import com.easysign.dto.request.ChangeEventStatusRequest;
import com.easysign.dto.request.CreateEventRequest;
import com.easysign.dto.request.UpdateEventRequest;
import com.easysign.dto.response.CreateEventResponse;
import com.easysign.dto.response.EventResponse;
import com.easysign.dto.response.PageResponse;
import com.easysign.ai.AiService;
import com.easysign.dto.response.SeatMatrixResponse;
import com.easysign.service.EventService;
import com.easysign.service.SeatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 活动管理 Controller（接口编号 4.x）
 * <p>
 * 提供活动的 CRUD、状态变更及 AI 文案生成能力。
 * 公开列表和详情页由 {@link com.easysign.config.WebMvcConfig} 放行，
 * Controller 层通过 {@link UserContext#isLoggedIn()} 做动态鉴权。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/event")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    @Autowired
    private EventService eventService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private AiService aiService;

    /**
     * 4.1 发布活动
     * <p>举办方创建新活动，初始状态为 {@code draft}。</p>
     *
     * @param req 活动信息（标题、描述、地点、时间、门票数）
     * @return 包含新活动 ID 和状态 {@code draft} 的响应
     */
    @PostMapping
    public Result<CreateEventResponse> createEvent(@Valid @RequestBody CreateEventRequest req) {
        CreateEventResponse resp = eventService.createEvent(UserContext.getUserId(), req);
        return Result.success("发布成功", resp);
    }

    /**
     * 4.2 活动列表 — scope 动态鉴权
     * <p>
     * 支持两种 scope：
     * <ul>
     *   <li>{@code public}（默认）：展示所有 {@code online} 状态的活动，按开始时间升序</li>
     *   <li>{@code organizer}：展示当前举办方（需登录 + organizer 角色）的所有活动，按更新时间降序</li>
     * </ul>
     * </p>
     */
    @GetMapping("/list")
    public Result<PageResponse<EventResponse>> listEvents(
            @RequestParam(defaultValue = "public") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if ("organizer".equals(scope)) {
            if (!UserContext.isLoggedIn()) throw new BizException(4001, "请先登录");
            if (!UserContext.hasRole("organizer")) throw new BizException(4002, "权限不足");
            return Result.success(eventService.listOrganizerEvents(UserContext.getUserId(), page, size));
        }

        // scope=public（默认）
        return Result.success(eventService.listPublicEvents(page, size, keyword));
    }

    /**
     * 4.3 活动详情
     * <p>返回单个活动的完整信息，C 端和 B 端共用此接口。</p>
     *
     * @param eventId 活动 ID
     * @return 活动详情
     */
    @GetMapping("/{eventId}")
    public Result<EventResponse> getEventById(@PathVariable Long eventId) {
        return Result.success(eventService.getEventById(eventId));
    }

    /**
     * 4.4 更新活动
     * <p>
     * 举办方更新活动信息。部分约束：
     * <ul>
     *   <li>已结束（finished）的活动不可修改</li>
     *   <li>已上线（online）的活动不可修改门票总数</li>
     *   <li>门票总数不能小于已售数量</li>
     * </ul>
     * </p>
     */
    @PutMapping("/{eventId}")
    public Result<String> updateEvent(@PathVariable Long eventId, @RequestBody UpdateEventRequest req) {
        eventService.updateEvent(UserContext.getUserId(), eventId, req);
        return Result.success("更新成功", null);
    }

    /**
     * 4.5 变更状态
     * <p>
     * 举办方变更活动状态。允许的状态流转：
     * <ul>
     *   <li>{@code draft → online}</li>
     *   <li>{@code online → offline}</li>
     *   <li>{@code offline → online}</li>
     * </ul>
     * 不允许手动变更为 {@code finished}，由定时任务 {@link com.easysign.task.EventStatusTask} 自动处理。
     * </p>
     */
    @PatchMapping("/{eventId}/status")
    public Result<String> changeStatus(@PathVariable Long eventId, @Valid @RequestBody ChangeEventStatusRequest req) {
        eventService.changeStatus(UserContext.getUserId(), eventId, req);
        return Result.success("状态变更成功", null);
    }

    /**
     * 4.6 删除活动
     * <p>
     * 举办方删除活动（仅 draft/offline 状态可删除）。
     * 删除关联的 ticket 会被置为 {@code invalid}。
     * </p>
     */
    @DeleteMapping("/{eventId}")
    public Result<String> deleteEvent(@PathVariable Long eventId) {
        eventService.deleteEvent(UserContext.getUserId(), eventId);
        return Result.success("删除成功", null);
    }

    /**
     * 获取活动座位矩阵
     * <p>返回该活动下所有座位的 10×10 矩阵，含占用状态。C 端和 B 端共用。</p>
     *
     * @param eventId 活动 ID
     * @return 座位矩阵
     */
    @GetMapping("/{eventId}/seats")
    public Result<SeatMatrixResponse> getSeats(@PathVariable Long eventId) {
        return Result.success(seatService.getSeatMatrix(eventId));
    }

    /**
     * 4.7 AI 一键生成文案 🔒 AI 禁飞区
     * <p>调用 Mock AI 服务为活动生成文案描述，暂不接入真实 LLM。</p>
     *
     * @param body 可选参数：可传入 {@code title} 字段作为生成基础文本
     * @return 包含生成文案的 Map，键为 {@code description}
     */
    @PostMapping("/{eventId}/ai-description")
    public Result<Map<String, String>> generateAiDescription(
            @PathVariable Long eventId,
            @RequestBody(required = false) Map<String, String> body) {
        String title = (body != null) ? body.get("title") : null;
        String description = eventService.generateAiDescription(eventId, title);
        String aiProvider = aiService.getClass().getSimpleName().replace("AiService", "").toLowerCase();
        log.info("AI 生成响应: provider={}, descriptionLength={}", aiProvider, description != null ? description.length() : 0);
        Map<String, String> result = new HashMap<>();
        result.put("description", description);
        result.put("aiProvider", aiProvider);
        return Result.success("生成成功", result);
    }
}