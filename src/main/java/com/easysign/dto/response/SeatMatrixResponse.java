package com.easysign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 座位矩阵响应 DTO
 * <p>
 * 返回活动的座位布局信息，供前端 Canvas 渲染座位图。
 * </p>
 */
@Data
@AllArgsConstructor
public class SeatMatrixResponse {

    /** 活动是否开启选座 */
    private Boolean hasSeats;

    /** 总排数 */
    private Integer rows;

    /** 总列数 */
    private Integer cols;

    /** 所有座位的状态列表 */
    private List<SeatResponse> seats;
}