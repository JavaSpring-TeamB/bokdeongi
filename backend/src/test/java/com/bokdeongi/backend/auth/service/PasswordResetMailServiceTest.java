package com.bokdeongi.backend.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 비밀번호 재설정 이메일 발송 서비스 단위 테스트.
 *
 * 이메일 발송 정보, 인증번호 안내 내용 및
 * 이메일 발송 실패 시 예외 처리를 검증한다.
 */
class PasswordResetMailServiceTest {

    private static final String SENDER_EMAIL =
            "bokdeongi@example.com";

    private static final String RECIPIENT_EMAIL =
            "user@example.com";

    private static final String CODE = "012345";

    private static final long EXPIRATION_SECONDS = 300L;

    private JavaMailSender mailSender;
    private PasswordResetMailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);

        service = new PasswordResetMailService(
                mailSender,
                SENDER_EMAIL
        );
    }

    /**
     * 발송 요청된 이메일 내용을 가져온다.
     */
    private SimpleMailMessage captureMessage() {
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        return captor.getValue();
    }

    /**
     * 정상적으로 이메일 발송을 요청한다.
     */
    @Test
    void shouldSendResetCode() {
        assertDoesNotThrow(() ->
                service.sendResetCode(
                        RECIPIENT_EMAIL,
                        CODE,
                        EXPIRATION_SECONDS
                )
        );

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
    }

    /**
     * 발신자와 수신자 이메일이 올바르게 설정된다.
     */
    @Test
    void shouldSetCorrectSenderAndRecipient() {
        service.sendResetCode(
                RECIPIENT_EMAIL,
                CODE,
                EXPIRATION_SECONDS
        );

        SimpleMailMessage message = captureMessage();

        assertEquals(
                SENDER_EMAIL,
                message.getFrom()
        );

        assertArrayEquals(
                new String[]{RECIPIENT_EMAIL},
                message.getTo()
        );
    }

    /**
     * 비밀번호 재설정 이메일 제목이 올바르게 설정된다.
     */
    @Test
    void shouldSetCorrectSubject() {
        service.sendResetCode(
                RECIPIENT_EMAIL,
                CODE,
                EXPIRATION_SECONDS
        );

        SimpleMailMessage message = captureMessage();

        assertEquals(
                "[복덩이] 비밀번호 재설정 인증번호",
                message.getSubject()
        );
    }

    /**
     * 이메일 본문에 인증번호와 유효기간 및
     * 보안 안내 문구가 포함된다.
     */
    @Test
    void shouldIncludeCodeAndExpirationInBody() {
        service.sendResetCode(
                RECIPIENT_EMAIL,
                CODE,
                EXPIRATION_SECONDS
        );

        SimpleMailMessage message = captureMessage();

        assertNotNull(message.getText());

        assertTrue(
                message.getText().contains("인증번호: " + CODE)
        );

        assertTrue(
                message.getText().contains("유효기간: 300초")
        );

        assertTrue(
                message.getText().contains(
                        "인증번호는 다른 사람에게 공유하지 마세요."
                )
        );
    }

    /**
     * 발신 이메일이 설정되지 않은 경우 발송을 제한한다.
     */
    @Test
    void shouldRejectMissingSenderEmail() {
        PasswordResetMailService invalidService =
                new PasswordResetMailService(
                        mailSender,
                        null
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> invalidService.sendResetCode(
                        RECIPIENT_EMAIL,
                        CODE,
                        EXPIRATION_SECONDS
                )
        );

        assertEquals(
                "이메일 발송 계정이 설정되지 않았습니다.",
                exception.getMessage()
        );

        verifyNoInteractions(mailSender);
    }

    /**
     * 발신 이메일이 공백인 경우 발송을 제한한다.
     */
    @Test
    void shouldRejectBlankSenderEmail() {
        PasswordResetMailService invalidService =
                new PasswordResetMailService(
                        mailSender,
                        " "
                );

        assertThrows(
                IllegalStateException.class,
                () -> invalidService.sendResetCode(
                        RECIPIENT_EMAIL,
                        CODE,
                        EXPIRATION_SECONDS
                )
        );

        verifyNoInteractions(mailSender);
    }

    /**
     * SMTP 발송 실패 시 서비스 예외로 변환한다.
     */
    @Test
    void shouldThrowExceptionWhenMailSendingFails() {
        MailSendException mailException =
                new MailSendException("SMTP 발송 실패");

        doThrow(mailException)
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sendResetCode(
                        RECIPIENT_EMAIL,
                        CODE,
                        EXPIRATION_SECONDS
                )
        );

        assertEquals(
                "인증번호 이메일 발송에 실패했습니다.",
                exception.getMessage()
        );

        assertSame(
                mailException,
                exception.getCause()
        );

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
    }
}