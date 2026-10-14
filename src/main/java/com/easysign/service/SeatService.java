package com.easysign.service;

import com.easysign.dto.request.SeatSelectRequest;
import com.easysign.dto.response.SeatMatrixResponse;
import com.easysign.dto.response.SeatResponse;

import java.util.List;

/**
 * 座位管理 Service
 * <p>
 * 提供座位初始化、查询、删除、选座四大核心能力。
 * 选座操作的原子性由 {@link com.easysign.mapper.SeatMapper#occupySeat} 保证。
 * </p>
 */
public interface SeatService {

    /**
     * 获取活动的所有座位（按排号升序、列号升序排列）。
     *
     * @param eventId 活动ID
     * @return 座位列表（含状态信息）
     */
    List<SeatResponse> getSeatsByEventId(Long eventId);

    /**
     * 获取活动的座位矩阵（10×10 二维视图，含占用状态）。
     * 相比 getSeatsByEventId，返回结构化的 SeatMatrixResponse 便于前端渲染网格。
     *
     * @param eventId 活动ID
     * @return 座位矩阵，含 rows×cols 和 seats[][] 信息
     */
    SeatMatrixResponse getSeatMatrix(Long eventId);

    /**
     * 为活动批量初始化固定模板座位（默认 10 行 × 10 列 = 100 座）。
     * 座位标签格式为 "{row}-{col}"，如 "1-1"、"10-10"。
     *
     * @param eventId 活动ID
     */
    void initSeats(Long eventId);

    /**
     * 删除活动的所有座位记录。
     * 调用前需确保该活动下无已售座位，否则应拒绝删除。
     *
     * @param eventId 活动ID
     */
    void deleteSeats(Long eventId);

    /**
     * 判断活动是否有已售出的座位。
     *
     * @param eventId 活动ID
     * @return true 如果存在 status='sold' 的座位
     */
    boolean hasSoldSeats(Long eventId);

    /**
     * 原子占座：将指定座位从 available 改为 sold，
     * 同时更新当前用户的 ticket.seat_id。
     *
     * @param userId 当前用户ID
     * @param req    包含 eventId 和 seatId
     * @throws com.easysign.common.BizException 4004 座位已被抢
     * @throws com.easysign.common.BizException 4100 座位不存在或已售出
     */
    void selectSeat(Long userId, SeatSelectRequest req);
}