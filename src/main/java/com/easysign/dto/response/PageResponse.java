package com.easysign.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页响应体
 * <p>
 * 由另一成员维护，本模块仅引用。
 * 可通过 {@link #PageResponse(List, long, int, int)} 构造器自动计算 {@link #pages}。
 * </p>
 */
@Data
@NoArgsConstructor
public class PageResponse<T> {

    /** 当前页数据记录列表 */
    private List<T> records;

    /** 符合条件的总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页大小 */
    private int size;

    /** 总页数（由构造器自动计算：{@code (total + size - 1) / size}） */
    private long pages;

    public PageResponse(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = (total + size - 1) / size;
    }
}