package com.bokdeongi.backend.common.exception;

/**
 * 이미 가입된 이메일로 회원가입을 요청한 경우 발생하는 예외
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("이미 사용 중인 이메일입니다.");
    }
}