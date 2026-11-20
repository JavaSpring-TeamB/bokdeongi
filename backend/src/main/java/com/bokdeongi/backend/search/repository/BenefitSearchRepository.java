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
    @Query(
            value = """
                SELECT b.*
                FROM benefits b
                WHERE b.status = 'ACTIVE'
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
                ORDER BY
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
                WHERE b.status = 'ACTIVE'
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
                """,
            nativeQuery = true
    )
    Page<Benefit> findBenefits(
            @Param("request") BenefitListRequest request,
            Pageable pageable
    );
}