package com.easysign.service;

import com.easysign.dto.request.CheckinRequest;
import com.easysign.dto.request.PurchaseRequest;
import com.easysign.dto.response.PageResponse;
import com.easysign.dto.response.TicketResponse;

/**
 * 票务 Service 接口
 * <p>
 * 定义抢票、票夹、单票详情、签到及签到明细操作。
 * 每个方法对应接口规范中的编号，与 {@link com.easysign.controller.TicketController} 配合使用。
 * </p>
 */
public interface TicketService {

    /**
     * 5.1 抢票
     * <p>
     * 使用乐观锁（{@code sold_tickets + 1} 原子更新）防超卖，
     * 通过数据库唯一索引防重复购票。
     * </p>
     *
     * @param userId 当前登录用户 ID
     * @param req    包含活动 ID
     * @return 抢票结果（ticketId、ticketNo、状态）
     */
    TicketResponse purchase(Long userId, PurchaseRequest req);

    /**
     * 5.2 我的票夹
     * <p>
     * 获取当前用户的所有门票列表，支持按 {@code status} 过滤。
     * 查询时关联 event 表获取活动基本信息。
     * </p>
     *
     * @param userId 当前登录用户 ID
     * @param status 可选过滤条件：purchased / checked / refunded
     */
    PageResponse<TicketResponse> getMyTickets(Long userId, String status, int page, int size);

    /**
     * 5.3 单张票详情
     * <p>查看指定 ticketId 的详细票务信息，仅票主本人可访问。</p>
     */
    TicketResponse getTicketById(Long userId, Long ticketId);

    /**
     * 5.4 签到
     * <p>
     * 举办方通过签到凭证（二维码完整格式或手动输入格式）为用户签到。
     * 完整格式含时间戳，限 60 秒内有效；手动输入格式不限时。
     * 调用前已鉴权当前用户为活动举办方。
     * </p>
     *
     * @param userId 举办方用户 ID
     * @param req    签到凭证 code
     * @return 签到成功后的票务摘要信息
     */
    TicketResponse checkin(Long userId, CheckinRequest req);

    /**
     * 5.5 签到明细
     * <p>
     * 举办方查看指定活动下所有已购票用户的签到情况，
     * 可按 {@code status}（purchased / checked）过滤。
     * </p>
     */
    PageResponse<TicketResponse> getCheckins(Long userId, Long eventId, String status, int page, int size);
}