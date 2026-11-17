/* DB v2.9 (2026-11-16): benefits.date_precision 추가 (D-66, D-84)
   날짜가 일 단위(DAY)인지 월 단위(MONTH)인지. date_status가 PARSED일 때만 채우고 나머지는 NULL.
   여러 번 실행해도 결과가 같다 (이미 있으면 건너뜀). 공용 DB에는 D가 실행한다. */
SET NAMES utf8mb4;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'benefits' AND COLUMN_NAME = 'date_precision');
SET @sql := IF(@has = 0,
  'ALTER TABLE `benefits` ADD COLUMN `date_precision` VARCHAR(5) NULL COMMENT ''DAY / MONTH. date_status = PARSED일 때만'' AFTER `date_status`',
  'SELECT ''date_precision 이미 있음'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 테스트 데이터: 날짜가 있는 PARSED 행은 일 단위로 둔다
UPDATE `benefits` SET `date_precision` = 'DAY'
WHERE `date_status` = 'PARSED' AND `apply_end` IS NOT NULL AND `date_precision` IS NULL;

-- 확인
SELECT `date_status`, `date_precision`, COUNT(*) AS n FROM `benefits` GROUP BY `date_status`, `date_precision`;
