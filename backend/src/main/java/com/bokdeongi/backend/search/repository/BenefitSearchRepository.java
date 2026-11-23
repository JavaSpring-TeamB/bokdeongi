package com.bokdeongi.backend.search.repository;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.dto.request.BenefitListRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenefitSearchRepository extends JpaRepository<Benefit, Long> {

    // 기존 키워드 검색 API
    @Query(
            value = """
                SELECT b.*
                FROM benefits b
                WHERE b.status = 'ACTIVE'
                  AND (b.target_scope IS NULL OR b.target_scope <> 'CORP')
                  AND (
                      LOCATE(LOWER(:keyword), LOWER(COALESCE(b.title, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_who, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_what, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_how, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_when, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.org_name, ''))) > 0
                      OR EXISTS (
                          SELECT 1
                          FROM JSON_TABLE(
                              COALESCE(b.hashtags, JSON_ARRAY()),
                              '$[*]' COLUMNS (
                                  tag_name VARCHAR(255) PATH '$'
                              )
                          ) jt
                          WHERE LOCATE(
                              LOWER(:keyword),
                              LOWER(jt.tag_name)
                          ) > 0
                      )
                  )
                ORDER BY
                    CASE
                        WHEN LOWER(b.title) = LOWER(:keyword) THEN 100
                        WHEN LOCATE(
                            LOWER(:keyword),
                            LOWER(COALESCE(b.title, ''))
                        ) = 1 THEN 90
                        WHEN LOCATE(
                            LOWER(:keyword),
                            LOWER(COALESCE(b.title, ''))
                        ) > 0 THEN 80
                        WHEN EXISTS (
                            SELECT 1
                            FROM JSON_TABLE(
                                COALESCE(b.hashtags, JSON_ARRAY()),
                                '$[*]' COLUMNS (
                                    tag_name VARCHAR(255) PATH '$'
                                )
                            ) jt
                            WHERE LOCATE(
                                LOWER(:keyword),
                                LOWER(jt.tag_name)
                            ) > 0
                        ) THEN 70
                        WHEN LOCATE(
                            LOWER(:keyword),
                            LOWER(COALESCE(b.org_name, ''))
                        ) > 0 THEN 60
                        ELSE 50
                    END DESC,
                    b.id DESC
                """,
            countQuery = """
                SELECT COUNT(*)
                FROM benefits b
                WHERE b.status = 'ACTIVE'
                  AND (b.target_scope IS NULL OR b.target_scope <> 'CORP')
                  AND (
                      LOCATE(LOWER(:keyword), LOWER(COALESCE(b.title, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_who, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_what, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_how, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.summary_when, ''))) > 0
                      OR LOCATE(LOWER(:keyword), LOWER(COALESCE(b.org_name, ''))) > 0
                      OR EXISTS (
                          SELECT 1
                          FROM JSON_TABLE(
                              COALESCE(b.hashtags, JSON_ARRAY()),
                              '$[*]' COLUMNS (
                                  tag_name VARCHAR(255) PATH '$'
                              )
                          ) jt
                          WHERE LOCATE(
                              LOWER(:keyword),
                              LOWER(jt.tag_name)
                          ) > 0
                      )
                  )
                """,
            nativeQuery = true
    )
    Page<Benefit> searchByKeyword(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 통합 혜택 목록 API
    // 신청 상태: 가능 = ACTIVE 중 아직 시작 전·기간 지남이 아닌 것(상시·기관별·공고 확인·신청 불필요 포함),
    //           예정 = 시작일이 오늘 뒤이거나 매년 반복인데 올해 기간이 지난 것(다음 회차), 마감 = EXPIRED (D-85)
    // 분야·대상: 해시태그(GET /api/hashtags의 category 분야·대상) 중 하나라도 있으면
    // 정렬(BEN-02): deadline = 마감 임박(지난 것·마감일 없음은 뒤), amount = 연간(없으면 최대) 금액 큰 순(미확인은 뒤),
    //              easy = 온라인 신청 먼저 → 내가 낼 서류 적은 순. 같은 값이면 관련도 → id 순
    @Query(
            value = """
                SELECT b.*
                FROM benefits b
                WHERE b.status IN ('ACTIVE', 'EXPIRED')
                  AND (
                      (:#{#request.statusOpen()} = TRUE AND b.status = 'ACTIVE'
                          AND NOT (b.date_status = 'PARSED'
                              AND (COALESCE(b.apply_start > :#{#request.today()}, FALSE)
                                   OR COALESCE(b.apply_end < :#{#request.today()}, FALSE))))
                      OR (:#{#request.statusUpcoming()} = TRUE AND b.status = 'ACTIVE'
                          AND b.date_status = 'PARSED'
                          AND (COALESCE(b.apply_start > :#{#request.today()}, FALSE)
                               OR (COALESCE(b.recurring_yearly, FALSE) = TRUE
                                   AND COALESCE(b.apply_end < :#{#request.today()}, FALSE))))
                      OR (:#{#request.statusClosed()} = TRUE
                          AND (b.status = 'EXPIRED'
                               OR (b.status = 'ACTIVE' AND b.date_status = 'PARSED'
                                   AND COALESCE(b.apply_end < :#{#request.today()}, FALSE)
                                   AND COALESCE(b.recurring_yearly, FALSE) = FALSE)))
                  )
                  AND (b.target_scope IS NULL OR b.target_scope <> 'CORP')
                  AND (
                      :#{#request.q()} IS NULL
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.title, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_who, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_what, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_how, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_when, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.org_name, ''))
                      ) > 0
                      OR EXISTS (
                          SELECT 1
                          FROM JSON_TABLE(
                              COALESCE(b.hashtags, JSON_ARRAY()),
                              '$[*]' COLUMNS (
                                  tag_name VARCHAR(255) PATH '$'
                              )
                          ) jt
                          WHERE LOCATE(
                              LOWER(:#{#request.q()}),
                              LOWER(jt.tag_name)
                          ) > 0
                      )
                  )
                  AND (
                      :#{#request.hashtag()} IS NULL
                      OR EXISTS (
                          SELECT 1
                          FROM JSON_TABLE(
                              COALESCE(b.hashtags, JSON_ARRAY()),
                              '$[*]' COLUMNS (
                                  tag_name VARCHAR(255) PATH '$'
                              )
                          ) ht
                          WHERE LOWER(ht.tag_name) =
                                LOWER(:#{#request.hashtag()})
                      )
                  )
                  AND (
                      :#{#request.fieldsJson()} IS NULL
                      OR JSON_OVERLAPS(COALESCE(b.hashtags, JSON_ARRAY()),
                                       CAST(:#{#request.fieldsJson()} AS JSON))
                  )
                  AND (
                      :#{#request.targetsJson()} IS NULL
                      OR JSON_OVERLAPS(COALESCE(b.hashtags, JSON_ARRAY()),
                                       CAST(:#{#request.targetsJson()} AS JSON))
                  )
                ORDER BY
                    CASE WHEN :#{#request.sort()} = 'deadline'
                         THEN (b.status = 'EXPIRED' OR COALESCE(b.apply_end < :#{#request.today()}, FALSE)) END,
                    CASE WHEN :#{#request.sort()} = 'deadline' THEN (b.apply_end IS NULL) END,
                    CASE WHEN :#{#request.sort()} = 'deadline' THEN b.apply_end END ASC,
                    CASE WHEN :#{#request.sort()} = 'amount'
                         THEN (COALESCE(b.annual_amount, b.max_amount) IS NULL) END,
                    CASE WHEN :#{#request.sort()} = 'amount'
                         THEN COALESCE(b.annual_amount, b.max_amount) END DESC,
                    CASE WHEN :#{#request.sort()} = 'easy' THEN (b.online_apply IS NULL) END,
                    CASE WHEN :#{#request.sort()} = 'easy' THEN b.online_apply END DESC,
                    CASE WHEN :#{#request.sort()} = 'easy' THEN (b.self_doc_count IS NULL) END,
                    CASE WHEN :#{#request.sort()} = 'easy' THEN b.self_doc_count END ASC,
                    CASE
                        WHEN :#{#request.q()} IS NULL THEN 0
                        WHEN LOWER(b.title) =
                             LOWER(:#{#request.q()}) THEN 100
                        WHEN LOCATE(
                            LOWER(:#{#request.q()}),
                            LOWER(COALESCE(b.title, ''))
                        ) = 1 THEN 90
                        WHEN LOCATE(
                            LOWER(:#{#request.q()}),
                            LOWER(COALESCE(b.title, ''))
                        ) > 0 THEN 80
                        WHEN EXISTS (
                            SELECT 1
                            FROM JSON_TABLE(
                                COALESCE(b.hashtags, JSON_ARRAY()),
                                '$[*]' COLUMNS (
                                    tag_name VARCHAR(255) PATH '$'
                                )
                            ) jt
                            WHERE LOCATE(
                                LOWER(:#{#request.q()}),
                                LOWER(jt.tag_name)
                            ) > 0
                        ) THEN 70
                        WHEN LOCATE(
                            LOWER(:#{#request.q()}),
                            LOWER(COALESCE(b.org_name, ''))
                        ) > 0 THEN 60
                        ELSE 50
                    END DESC,
                    b.id DESC
                """,
            countQuery = """
                SELECT COUNT(*)
                FROM benefits b
                WHERE b.status IN ('ACTIVE', 'EXPIRED')
                  AND (
                      (:#{#request.statusOpen()} = TRUE AND b.status = 'ACTIVE'
                          AND NOT (b.date_status = 'PARSED'
                              AND (COALESCE(b.apply_start > :#{#request.today()}, FALSE)
                                   OR COALESCE(b.apply_end < :#{#request.today()}, FALSE))))
                      OR (:#{#request.statusUpcoming()} = TRUE AND b.status = 'ACTIVE'
                          AND b.date_status = 'PARSED'
                          AND (COALESCE(b.apply_start > :#{#request.today()}, FALSE)
                               OR (COALESCE(b.recurring_yearly, FALSE) = TRUE
                                   AND COALESCE(b.apply_end < :#{#request.today()}, FALSE))))
                      OR (:#{#request.statusClosed()} = TRUE
                          AND (b.status = 'EXPIRED'
                               OR (b.status = 'ACTIVE' AND b.date_status = 'PARSED'
                                   AND COALESCE(b.apply_end < :#{#request.today()}, FALSE)
                                   AND COALESCE(b.recurring_yearly, FALSE) = FALSE)))
                  )
                  AND (b.target_scope IS NULL OR b.target_scope <> 'CORP')
                  AND (
                      :#{#request.q()} IS NULL
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.title, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_who, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_what, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_how, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.summary_when, ''))
                      ) > 0
                      OR LOCATE(
                          LOWER(:#{#request.q()}),
                          LOWER(COALESCE(b.org_name, ''))
                      ) > 0
                      OR EXISTS (
                          SELECT 1
                          FROM JSON_TABLE(
                              COALESCE(b.hashtags, JSON_ARRAY()),
                              '$[*]' COLUMNS (
                                  tag_name VARCHAR(255) PATH '$'
                              )
                          ) jt
                          WHERE LOCATE(
                              LOWER(:#{#request.q()}),
                              LOWER(jt.tag_name)
                          ) > 0
                      )
                  )
                  AND (
                      :#{#request.hashtag()} IS NULL
                      OR EXISTS (
                          SELECT 1
                          FROM JSON_TABLE(
                              COALESCE(b.hashtags, JSON_ARRAY()),
                              '$[*]' COLUMNS (
                                  tag_name VARCHAR(255) PATH '$'
                              )
                          ) ht
                          WHERE LOWER(ht.tag_name) =
                                LOWER(:#{#request.hashtag()})
                      )
                  )
                  AND (
                      :#{#request.fieldsJson()} IS NULL
                      OR JSON_OVERLAPS(COALESCE(b.hashtags, JSON_ARRAY()),
                                       CAST(:#{#request.fieldsJson()} AS JSON))
                  )
                  AND (
                      :#{#request.targetsJson()} IS NULL
                      OR JSON_OVERLAPS(COALESCE(b.hashtags, JSON_ARRAY()),
                                       CAST(:#{#request.targetsJson()} AS JSON))
                  )
                """,
            nativeQuery = true
    )
    Page<Benefit> findBenefits(
            @Param("request") BenefitListRequest request,
            Pageable pageable
    );
}