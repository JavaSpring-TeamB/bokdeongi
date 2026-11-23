package com.bokdeongi.backend.easytext;

import com.bokdeongi.backend.benefit.easy.EasyGenerator;
import com.bokdeongi.backend.common.privacy.PersonalInfoMasker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * EASY-02 쉬운 말 번역기. 사용자가 붙여 넣은 고지서·안내문을 쉬운 말로 바꾼다.
 *
 * <p>사용자가 쓴 글을 LLM에 보내는 기능이라 D-92 예외로 다룬다 (B 결정 11/23): 화면에서 "개인정보를 지우고 붙여 넣으세요"라고
 * 안내하고, 서버는 주민번호·휴대전화·이메일을 가린 뒤에 보낸다. 글과 결과는 저장하지 않는다. 로그에도 글을 남기지 않는다.
 */
@Service
public class EasyTranslatorService {

    public static final int MAX_TEXT = 5000;
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    static final String FAILED_SUMMARY = "쉬운 말로 바꾸지 못했어요. 원문을 확인하거나 잠시 후 다시 해 주세요.";

    private static final Logger log = LoggerFactory.getLogger(EasyTranslatorService.class);

    private final ObjectProvider<ChatModel> chatModel;
    private final EasyGenerator generator;
    private final EasyTextLimiter limiter;

    public EasyTranslatorService(ObjectProvider<ChatModel> chatModel, EasyGenerator generator, EasyTextLimiter limiter) {
        this.chatModel = chatModel;
        this.generator = generator;
        this.limiter = limiter;
    }

    /**
     * @param clientKey 횟수를 셀 열쇠 (로그인하면 "user:{id}", 아니면 "ip:{주소}")
     * @param text      붙여 넣은 글
     */
    public EasyTextDto.Response translate(String clientKey, String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) {
            throw EasyTextException.invalidInput("바꿀 글을 붙여 넣어 주세요.");
        }
        if (t.length() > MAX_TEXT) {
            throw EasyTextException.invalidInput("글은 " + MAX_TEXT + "자 이내로 붙여 넣어 주세요.");
        }
        if (chatModel.getIfAvailable() == null) {
            throw EasyTextException.unavailable(); // 키가 없으면 횟수를 쓰지 않는다
        }
        if (!limiter.tryUse(clientKey)) {
            throw EasyTextException.tooMany();
        }

        String masked = PersonalInfoMasker.mask(t);
        boolean hidden = !masked.equals(t);
        String raw;
        try {
            raw = generator.generate(EasyTextPrompt.system(), EasyTextPrompt.user(masked));
        } catch (RuntimeException e) {
            throw EasyTextException.unavailable();
        }

        EasyTextChecker.Result r = EasyTextChecker.check(raw, masked);
        int remaining = limiter.remaining(clientKey);
        if (!r.ok()) {
            log.info("쉬운 말 번역({}) 검사 탈락: {}", EasyTextPrompt.VERSION, r.reason()); // 글 내용은 남기지 않는다
            return new EasyTextDto.Response(FAILED, FAILED_SUMMARY, List.of(), hidden, remaining, EasyTextDto.NOTICE);
        }
        return new EasyTextDto.Response(DONE, r.summary(), r.sections(), hidden, remaining, EasyTextDto.NOTICE);
    }
}
