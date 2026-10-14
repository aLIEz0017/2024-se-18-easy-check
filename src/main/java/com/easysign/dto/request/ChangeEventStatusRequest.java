package com.easysign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 4.5 变更活动状态请求体
 * <p>
 * 仅允许变更为 {@code online} 或 {@code offline}。
 * 不允许手动变更为 {@code draft}（新建即是 draft）或 {@code finished}（由定时任务自动推进）。
 * </p>
 */
@Data
public class ChangeEventStatusRequest {

    /** 目标状态：{@code online} 或 {@code offline} */
    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "online|offline", message = "目标状态仅允许 online 或 offline")
    private String status;
}