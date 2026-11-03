package org.openexchange.common.web.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // System errors
    SUCCESS(0, "success"),
    SYSTEM_ERROR(30001, "System maintenance"),
    SERVICE_UNAVAILABLE(30002, "Service temporarily unavailable"),

    // Parameter errors
    INVALID_PARAMETER(10001, "Invalid parameter"),
    AUTH_REQUIRED(10002, "Authentication required"),
    INSUFFICIENT_PERMISSIONS(10003, "Insufficient permissions"),
    RESOURCE_NOT_FOUND(10004, "Resource not found"),
    RATE_LIMIT_EXCEEDED(10005, "Rate limit exceeded"),

    // Business errors
    INSUFFICIENT_BALANCE(20001, "Insufficient balance"),
    ORDER_QTY_TOO_SMALL(20002, "Order quantity too small"),
    PRICE_OUT_OF_RANGE(20003, "Price out of range"),
    TRADING_PAIR_SUSPENDED(20004, "Trading pair suspended"),
    WITHDRAWAL_UNDER_REVIEW(20005, "Withdrawal under review"),
    KYC_REQUIRED(20006, "KYC verification required"),
    INVALID_CREDENTIALS(20007, "Invalid email or password"),
    USER_ALREADY_EXISTS(20008, "User already exists"),
    ORDER_NOT_FOUND(20009, "Order not found"),
    WALLET_NOT_FOUND(20010, "Wallet not found"),
    DUPLICATE_TRANSACTION(20011, "Duplicate transaction"),
    INVALID_2FA_CODE(20012, "Invalid 2FA code"),
    WITHDRAWAL_LIMIT_EXCEEDED(20013, "Withdrawal limit exceeded");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
