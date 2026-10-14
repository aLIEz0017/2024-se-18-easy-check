package com.easysign.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 4.1 发布活动请求体
 * <p>
 * 包含活动标题、描述、地点、时间范围和门票总数。
 * 基础校验通过 Jakarta Validation 注解完成（如 {@code @NotBlank}、{@code @Size}），
 * 跨字段校验（如 endTime > startTime）在 {@link com.easysign.service.EventService} 中处理。
 * </p>
 */
@Data
public class CreateEventRequest {

    /** 活动标题（必填，最长 100 字符） */
    @NotBlank(message = "活动标题不能为空")
    @Size(max = 100, message = "活动标题最长100个字符")
    private String title;

    /** 活动描述（可选） */
    private String description;

    /** 活动地点（可选） */
    private String location;

    /** 开始时间（必填，格式：yyyy-MM-dd HH:mm:ss） */
    @NotNull(message = "开始时间不能为空")
    private String startTime;

    /** 结束时间（可选，格式：yyyy-MM-dd HH:mm:ss，不填则活动无固定结束时间） */
    private String endTime;

    /** 门票总数（必填，范围 1~99999） */
    @NotNull(message = "门票总数不能为空")
    @Min(value = 1, message = "门票总数至少为1")
    @Max(value = 99999, message = "门票总数最多为99999")
    private Integer totalTickets;

    /** 活动标签（可选，最多 5 个标签，每个标签最长 20 字符） */
    @Size(max = 5, message = "标签最多5个")
    private List<String> tags;

    /** 是否开启选座（可选，默认 false 不开启） */
    private Boolean hasSeats;
}
