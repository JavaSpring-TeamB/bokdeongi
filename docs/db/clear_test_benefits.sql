-- 공용 DB의 테스트 혜택 정리 (DATA-01 첫 적재 전에 한 번만).
-- 지우는 것: 혜택(benefits)과 거기에 딸린 판정 규칙·판정 결과(CASCADE), 혜택을 가리키는 관심·신청 기록, 동기화 기록.
-- 그대로 두는 것: regions, document_types, hashtags, users, user_consents, median_income_standards.
-- user_benefits → benefits 외래키는 CASCADE가 아니라서 먼저 지운다.
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM user_benefits;
DELETE FROM benefits;      -- benefit_rules, eligibility_results는 ON DELETE CASCADE
DELETE FROM sync_logs;
COMMIT;
