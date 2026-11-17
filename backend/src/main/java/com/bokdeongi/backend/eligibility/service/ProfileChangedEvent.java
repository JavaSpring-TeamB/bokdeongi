package com.bokdeongi.backend.eligibility.service;

/**
 * 회원 프로필(또는 추가 질문 답)이 저장됐다는 알림. 프로필을 저장하는 쪽(PROF-01·04·05·06, BEN-04)이
 * {@code ApplicationEventPublisher.publishEvent(new ProfileChangedEvent(userId))}로 보내면 커밋 뒤 재판정한다.
 */
public record ProfileChangedEvent(long userId) {
}
