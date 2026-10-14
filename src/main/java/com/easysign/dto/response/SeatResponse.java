package com.easysign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 座位响应 DTO
 * <p>
 * 返回单个座位的行列信息及当前状态，供前端渲染座位网格。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {

    /** 座位ID（主键） */
    private Long seatId;

    /** 排号 */
    private Integer seatRow;

    /** 列号 */
    private Integer seatCol;

    /** 座位标签，如"1-1" */
    private String seatLabel;

    /** 座位状态：available=可选, sold=已售出 */
    private String status;
}