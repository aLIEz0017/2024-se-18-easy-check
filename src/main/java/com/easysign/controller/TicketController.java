package com.easysign.controller;

import com.easysign.common.BizException;
import com.easysign.common.Result;
import com.easysign.context.UserContext;
import com.easysign.dto.request.CheckinRequest;
import com.easysign.dto.request.PurchaseRequest;
import com.easysign.dto.response.PageResponse;
import com.easysign.dto.response.TicketResponse;
import com.easysign.ratelimit.PurchaseRateLimiter;
import com.easysign.ratelimit.RateLimitResult;
import com.easysign.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 票务 Controller（接口编号 5.x）
 * <p>
 * 提供抢票、票夹、单票详情、签到及签到明细功能。
 * 所有接口均需登录（JWT 拦截器已拦截 {@code /api/v1/ticket/**}）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private PurchaseRateLimiter purchaseRateLimiter;

    /**
     * 5.1 抢票
     * <p>
     * 购买指定活动的门票。包含防超卖（原子 {@code sold_tickets + 1}）和
     * 重复购票检查（一人一票）。系统自动生成 ticketNo。
     * </p>
     *
     * @param req 包含活动 ID
     * @return 含 ticketId、ticketNo 和状态 {@code pending} 的响应；
     *         响应头中携带限流信息（X-RateLimit-Remaining、X-RateLimit-Reset）。
     */
    @PostMapping("/purchase")
    public ResponseEntity<Result<TicketResponse>> purchase(@Valid @RequestBody PurchaseRequest req) {
        // 限流检查
        RateLimitResult limitResult = purchaseRateLimiter.tryAcquire(UserContext.getUserId(), req.getEventId());

        // 构造响应头（限流信息）
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Remaining", String.valueOf(limitResult.getRemaining()));
        headers.add("X-RateLimit-Reset", String.valueOf(limitResult.getResetAfterSeconds()));
        headers.add("X-RateLimit-Limit", String.valueOf(limitResult.getWindowSeconds()));

        // 被限流
        if (!limitResult.isAllowed()) {
            throw new BizException(429, "操作过于频繁，请稍后再试");
        }

        // 正常购票
        TicketResponse resp = ticketService.purchase(UserContext.getUserId(), req);
        return ResponseEntity.ok()
                .headers(headers)
                .body(Result.success("抢票成功！请前往「我的票夹」查看", resp));
    }

    /**
     * 5.2 我的票夹
     * <p>获取当前用户的所有门票，可按 {@code status}（purchased / checked / refunded）过滤。</p>
     */
    @GetMapping("/my-tickets")
    public Result<PageResponse<TicketResponse>> getMyTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(ticketService.getMyTickets(UserContext.getUserId(), status, page, size));
    }

    /**
     * 5.3 单张票详情
     * <p>查看指定 ticket 的完整信息，仅票主本人可访问。</p>
     */
    @GetMapping("/{ticketId}")
    public Result<TicketResponse> getTicketById(@PathVariable Long ticketId) {
        return Result.success(ticketService.getTicketById(UserContext.getUserId(), ticketId));
    }

    /**
     * 5.4 签到
     * <p>
     * 举办方使用签到凭证（二维码完整格式或手动输入格式）完成签到。
     * 支持时间戳校验（完整格式限 60 秒内有效），防止凭证重放。
     * </p>
     *
     * @param req 签到凭证 code
     * @return 签到成功后的票务信息
     */
    @PostMapping("/checkin")
    public Result<TicketResponse> checkin(@Valid @RequestBody CheckinRequest req) {
        TicketResponse resp = ticketService.checkin(UserContext.getUserId(), req);
        return Result.success("签到成功", resp);
    }

    /**
     * 5.5 签到明细
     * <p>举办方查看指定活动的签到情况，可按 {@code status}（purchased / checked）过滤。</p>
     */
    @GetMapping("/event/{eventId}/checkins")
    public Result<PageResponse<TicketResponse>> getCheckins(
            @PathVariable Long eventId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(ticketService.getCheckins(UserContext.getUserId(), eventId, status, page, size));
    }
}
