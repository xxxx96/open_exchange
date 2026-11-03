package org.openexchange.common.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.openexchange.common.web.exception.ErrorCode;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String msg;

    @Builder.Default
    private long timestamp = Instant.now().toEpochMilli();

    private T data;
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .msg(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .msg(ErrorCode.SUCCESS.getMessage())
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        return ApiResponse.<T>builder()
                .code(code)
                .msg(msg)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String msg, T data) {
        return ApiResponse.<T>builder()
                .code(code)
                .msg(msg)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .msg(errorCode.getMessage())
                .build();
    }
}

