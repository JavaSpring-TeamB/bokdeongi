package com.bokdeongi.backend.extraction;

/**
 * 원문 속 날짜 하나.
 *
 * @param year     연도. 앞 날짜나 게시 연도에서 이어받았을 수 있고, 알 수 없으면 null
 * @param month    월
 * @param day      일. 월까지만 있으면 null
 * @param explicit 연도가 이 날짜 표기에 직접 적혀 있으면 true
 */
public record DateMention(Integer year, int month, Integer day, boolean explicit) {
}
