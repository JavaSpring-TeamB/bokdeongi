package com.bokdeongi.backend.benefit.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public class ApplicationProgressCalculator {

    private ApplicationProgressCalculator() {
    }

    public static Progress calculate(
            String appStatus,
            Integer step,
            JsonNode appAnswers,
            JsonNode docChecks,
            int documentCount,
            Set<String> validKeys
    ) {
        int total = 2 + (documentCount * 2);
        int completed = 0;

        // ① 자격 확인
        if (isTrue(appAnswers, "eligibilityConfirmed")) {
            completed++;
        }

        // ② 서류 준비 및 ③ 서류 검토
        JsonNode documents = docChecks == null
                ? null
                : docChecks.path("documents");

        if (documents != null && documents.isObject()) {
            var fields = documents.fields();

            while (fields.hasNext()) {
                var entry = fields.next();

                // 실제 존재하는 서류만 진행률에 반영
                if (!validKeys.contains(entry.getKey())) {
                    continue;
                }

                JsonNode document = entry.getValue();

                boolean prepared = isTrue(document, "prepared");
                boolean reviewed = isTrue(document, "reviewed");

                if (prepared) {
                    completed++;

                    if (reviewed) {
                        completed++;
                    }
                }
            }
        }

        // ④ 실제 신청 완료
        boolean submitted =
                "SUBMITTED".equals(appStatus)
                        || "SELECTED".equals(appStatus)
                        || "REJECTED".equals(appStatus);

        if (submitted) {
            completed++;
        }

        completed = Math.min(completed, total);

        int percent = total == 0
                ? 0
                : (int) Math.round(completed * 100.0 / total);


        // 실제 제출 서류의 준비 및 검토 완료 여부 확인
        boolean allDocumentsCompleted = documentCount == validKeys.size();

        for (String key : validKeys) {
            JsonNode document = documents == null
                    ? null
                    : documents.path(key);

            if (!isTrue(document, "prepared")
                    || !isTrue(document, "reviewed")) {
                allDocumentsCompleted = false;
                break;
            }
        }

        boolean eligibilityConfirmed =
                isTrue(appAnswers, "eligibilityConfirmed");

        boolean hasPreparedDocument = false;

        for (String key : validKeys) {
            JsonNode document = documents == null
                    ? null
                    : documents.path(key);

            if (isTrue(document, "prepared")) {
                hasPreparedDocument = true;
                break;
            }
        }

        String stage;

        if ("SELECTED".equals(appStatus)
                || "REJECTED".equals(appStatus)) {
            stage = "결과";
        } else if (submitted) {
            stage = "신청 완료";
        } else if ("CANCELED".equals(appStatus)) {
            stage = "취소";
        } else if (eligibilityConfirmed
                && allDocumentsCompleted) {
            stage = "서류 준비 완료";
        } else if (step != null && step >= 3 && hasPreparedDocument) {
            stage = "서류 검토 중";
        }else {
            stage = "준비 중";
        }

        return new Progress(
                stage,
                completed,
                total,
                percent
        );
    }

    private static boolean isTrue(JsonNode node, String field) {
        return node != null
                && node.path(field).asBoolean(false);
    }

    public record Progress(
            String stage,
            int completedItems,
            int totalItems,
            int progressPercent
    ) {
    }
}