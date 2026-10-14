package com.easysign.common;

import lombok.Getter;

/**
 * 自定义业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final String msg;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public BizException(BizCode bizCode) {
        this(bizCode.getCode(), bizCode.getMsg());
    }

    public BizException(BizCode bizCode, String msg) {
        this(bizCode.getCode(), msg);
    }
}