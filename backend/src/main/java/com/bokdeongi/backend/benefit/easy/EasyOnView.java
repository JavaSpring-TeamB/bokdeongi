package com.bokdeongi.backend.benefit.easy;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 상세 화면에서 쉬운 말을 볼 때 (GET /api/benefits/{id}/easy). D-09 변경(11/23): 시연 공고만 미리 만들던 것을,
 * 저장된 글이 없으면 처음 볼 때 한 번 만들어 저장하도록 바꿨다. 다음부터는 저장된 글을 그대로 쓴다.
 *
 * <ul>
 *   <li>같은 원문으로 만든 글(또는 검사 탈락 기록)이 있으면 LLM을 부르지 않는다 → EasyTextService 캐시 규칙 그대로</li>
 *   <li>AI가 꺼져 있으면(키 없음) 만들지 않고 NOT_READY → 프론트는 원문만 보여 준다</li>
 *   <li>저장된 글은 누구나 보지만, 새로 만드는 건 로그인한 사람이 볼 때만 (아니면 NOT_READY) → 봇이 한도를 다 쓰지 않게</li>
 *   <li>새로 만드는 건 서버 전체 하루 {@value #DAILY_LIMIT}건까지 (넘으면 NOT_READY)</li>
 * </ul>
 */
@Component
public class EasyOnView {

    public static final int DAILY_LIMIT = 300;

    private final EasyTextService easyTextService;
    private final ObjectProvider<ChatModel> chatModel;
    private final Clock clock;
    private final AtomicReference<Count> count = new AtomicReference<>(new Count(null, 0));

    private record Count(LocalDate day, int used) {
    }

    @Autowired
    public EasyOnView(EasyTextService easyTextService, ObjectProvider<ChatModel> chatModel) {
        this(easyTextService, chatModel, Clock.systemDefaultZone());
    }

    EasyOnView(EasyTextService easyTextService, ObjectProvider<ChatModel> chatModel, Clock clock) {
        this.easyTextService = easyTextService;
        this.chatModel = chatModel;
        this.clock = clock;
    }

    /** @param loggedIn 로그인한 사람이 보는지. 아니면 저장된 글만 주고 새로 만들지 않는다 */
    public EasyResponse view(Benefit benefit, boolean loggedIn) {
        if (cached(benefit)) {
            return easyTextService.easy(benefit); // 저장된 글이나 FAILED를 돌려줄 뿐 LLM은 안 부른다
        }
        if (!loggedIn || chatModel.getIfAvailable() == null || !tryUse()) {
            return EasyResponse.notReady();
        }
        return easyTextService.easy(benefit);
    }

    /** 같은 원문으로 이미 만들었거나 실패한 기록이 있는지 (EasyTextService.easy와 같은 기준) */
    static boolean cached(Benefit b) {
        return b.getAiGeneratedAt() != null && Objects.equals(b.getAiSourceModifiedAt(), b.getApiModifiedAt())
                && (b.getEasyText() != null || EasyTextService.FAILED.equals(b.getAiStatus()));
    }

    private boolean tryUse() {
        LocalDate today = LocalDate.now(clock);
        boolean[] ok = {false};
        count.updateAndGet(c -> {
            Count cur = today.equals(c.day()) ? c : new Count(today, 0);
            if (cur.used() >= DAILY_LIMIT) {
                ok[0] = false;
                return cur;
            }
            ok[0] = true;
            return new Count(today, cur.used() + 1);
        });
        return ok[0];
    }
}
