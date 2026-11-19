/* DB v2.11 (2026-11-19): benefit_rules.field 길이 30 → 60
   추가 질문 규칙은 field = 'ANSWER:{질문 키}'인데 질문 키가 최대 50자라(AnswerService) 30자에 안 들어간다.
   검수 라벨 45개 질문 중 15개가 31~42자 (예: ANSWER:received_other_lifelong_edu_voucher).
   길이만 늘려서 기존 데이터는 그대로다. 여러 번 실행해도 결과가 같다. 공용 DB에는 D가 실행한다. */
SET NAMES utf8mb4;

ALTER TABLE `benefit_rules` MODIFY COLUMN `field` VARCHAR(60) NOT NULL;

-- 확인: CHARACTER_MAXIMUM_LENGTH = 60
SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'benefit_rules' AND COLUMN_NAME = 'field';
