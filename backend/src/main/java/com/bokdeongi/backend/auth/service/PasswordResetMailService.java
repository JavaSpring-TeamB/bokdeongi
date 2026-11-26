package com.bokdeongi.backend.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 재설정 인증번호 이메일 발송 서비스.
 *
 * 회원이 가입한 이메일 주소로 인증번호와 유효기간을 전달한다.
 * SMTP 인증 정보는 서버 환경변수를 통해 관리한다.
 */
@Service
public class PasswordResetMailService {

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public PasswordResetMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String senderEmail
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
    }

    /**
     * 비밀번호 재설정 인증번호를 회원 이메일로 발송한다.
     *
     * @param email 수신자 이메일
     * @param code 비밀번호 재설정 인증번호
     * @param expirationSeconds 인증번호 유효기간(초)
     */
    public void sendResetCode(
            String email,
            String code,
            long expirationSeconds
    ) {
        // 발신용 이메일 설정 여부 확인
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalStateException(
                    "이메일 발송 계정이 설정되지 않았습니다."
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(email);
        message.setSubject("[복덩이] 비밀번호 재설정 인증번호");

        message.setText("""
                안녕하세요. 복덩이입니다.

                비밀번호 재설정을 위한 인증번호를 안내드립니다.

                인증번호: %s
                유효기간: %d초

                인증번호는 다른 사람에게 공유하지 마세요.

                본인이 요청하지 않은 경우 이 이메일을 무시해 주세요.

                감사합니다.
                복덩이 드림
                """.formatted(code, expirationSeconds));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new IllegalStateException(
                    "인증번호 이메일 발송에 실패했습니다.",
                    e
            );
        }
    }
}