

package com.example.agent.common;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 响应数据
 */
@Setter
@Getter
@Accessors(chain = true)
@Schema(title = "响应")
public class Result<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 编码：0表示成功，其他值表示失败
     */
    @Schema(title = "编码：0表示成功，其他值表示失败")
    private int code = 0;
    /**
     * 消息内容
     */
    @Schema(title = "消息内容")
    private String msg = "success";
    /**
     * 响应数据
     */
    @Schema(title = "响应数据")
    private T data;

    @Schema(title = "原因")
    private String reason;

    @Schema(title = "追踪ID")
    private String traceId;

    public static Result<Void> ok() {
        return new Result<>();
    }

    public static <T> Result<T> ok(String msg) {
        return new Result<T>().setMsg(msg);
    }

    public static <T> Result<T> okData(T t) {
        return new Result<T>().setData(t);
    }


    public static <T> Result<T> error() {
        int code = ErrorCode.INTERNAL_SERVER_ERROR;
        return new Result<T>().setCode(code).setMsg("系统开小差了，请稍后再试");
    }

    public static <T> Result<T> unauthorized(String msg) {
        int code = ErrorCode.UNAUTHORIZED;
        if (msg == null || msg.isBlank()) {
            msg = "无权限";
        }
        return new Result<T>().setCode(code).setMsg(msg);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<T>().setCode(code).setMsg(msg);
    }

    public static <T> Result<T> error(int code, String msg, T data) {
        return new Result<T>().setCode(code).setMsg(msg).setData(data);
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    @JsonIgnore
    public boolean isSuccess() {
        return code == 0;
    }

    @JsonIgnore
    public boolean isSuccessAndNotNull() {
        return code == 0 && data != null;
    }
}
