package com.easysign.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用响应体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(BizCode.SUCCESS.getCode(), BizCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(BizCode.SUCCESS.getCode(), msg, data);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(BizCode bizCode) {
        return new Result<>(bizCode.getCode(), bizCode.getMsg(), null);
    }

    public static <T> Result<T> error(BizCode bizCode, String msg) {
        return new Result<>(bizCode.getCode(), msg, null);
    }
}