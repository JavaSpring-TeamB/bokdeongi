package com.bokdeongi.backend.common.exception;

/**
 * 이메일 로그인 계정 잠금 예외.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}