function isDevelopmentHost() {
  return window.location.protocol === 'file:' || ['localhost', '127.0.0.1'].includes(window.location.hostname);
}

function isDevelopmentPreviewEnabled() {
  return document.documentElement.dataset.enableMockPreview === 'true' && isDevelopmentHost();
}

const AUTH_STATES = new Set(['checking', 'authenticated', 'unauthenticated', 'error']);
const authState = { status: 'checking' };
const homeSummaryState = { status: 'idle', data: null, requestId: 0 };
const homeActivityState = { status: 'idle', data: null, requestId: 0 };
const weeklyViewState = { sourceIds: '', visibleIds: [], activeDetail: 'new' };
const developmentPreview = { enabled: isDevelopmentPreviewEnabled(), mockLogin: false, dashboardState: 'ready' };
const oauthParameters = new URLSearchParams(window.location.search);
const oauthResult = {
  kakaoSignupRequested: oauthParameters.get('login') !== 'failed' && oauthParameters.get('kakaoSignup') === 'true',
  kakaoSignupHandled: false,
  kakaoReconsentRequested: oauthParameters.get('login') !== 'failed' && oauthParameters.get('kakaoReconsent') === 'true',
  kakaoReconsentHandled: false,
  profileOnboardingRequested: oauthParameters.get('profileOnboarding') === 'true',
  profileOnboardingHandled: false
};

function formatDateKey(year, monthIndex, day) {
  return `${year}-${String(monthIndex + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function getSeoulDateParts(date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(date);
  return Object.fromEntries(parts.filter((part) => ['year', 'month', 'day'].includes(part.type)).map((part) => [part.type, Number(part.value)]));
}

const initialCalendarDate = getSeoulDateParts();
const calendarState = {
  displayedDate: new Date(initialCalendarDate.year, initialCalendarDate.month - 1, 1),
  selectedDate: formatDateKey(initialCalendarDate.year, initialCalendarDate.month - 1, initialCalendarDate.day),
  events: [], alwaysCount: 0, fallbackUsed: false, status: 'idle', requestId: 0
};
const stateMessages = {
  support: { loading: '지원금을 불러오는 중이에요.', empty: '현재 표시할 지원금이 없어요.', error: '지원금 정보를 불러오지 못했어요.' },
  benefits: { loading: '맞춤 혜택을 불러오는 중이에요.', empty: '아직 맞춤 혜택 결과가 없어요.', error: '맞춤 혜택 정보를 불러오지 못했어요.' },
  weekly: { loading: '이번 주 혜택 소식을 불러오는 중이에요.', empty: '이번 주 새로운 혜택 소식이 없어요.', error: '혜택 소식을 불러오지 못했어요.' },
  applications: { loading: '신청 현황을 불러오는 중이에요.', empty: '진행 중인 신청 혜택이 없어요.', error: '신청 현황을 불러오지 못했어요.' },
  calendar: { loading: '신청 일정을 불러오는 중이에요.', empty: '표시할 신청 일정이 없어요.', error: '신청 일정을 불러오지 못했어요.' }
};

function formatCurrency(amount) { return `${Number(amount).toLocaleString('ko-KR')}원`; }
function getLoginRequiredMarkup(message, actionLabel) { return `<div class="login-required"><p>${message}</p><a class="btn btn-secondary" href="login.html" data-login-required>${actionLabel}</a></div>`; }
function getAuthCheckingMarkup() { return '<div class="dashboard-state dashboard-state--loading" role="status"><p>인증 상태를 확인하는 중이에요.</p></div>'; }
function getAuthErrorMarkup() { return '<div class="dashboard-state dashboard-state--error" role="status"><p>인증 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.</p><button class="btn btn-secondary dashboard-state__retry" type="button" data-auth-retry>인증 상태 다시 확인</button></div>'; }
function getStateMarkup(section, state) {
  const retry = state === 'error' ? `<button class="btn btn-secondary dashboard-state__retry" type="button" data-retry-section="${section}">다시 시도</button>` : '';
  return `<div class="dashboard-state dashboard-state--${state}" role="status"><p>${stateMessages[section][state]}</p>${retry}</div>`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function isNullableFiniteNumber(value) {
  return value === null || (typeof value === 'number' && Number.isFinite(value));
}

function isNullableInteger(value) {
  return value === null || Number.isInteger(value);
}

function isNullableString(value) {
  return value === null || typeof value === 'string';
}

function isDateOnly(value) {
  return typeof value === 'string'
    && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && !Number.isNaN(Date.parse(value))
    && new Date(value).toISOString().slice(0, 10) === value;
}

function isHomeActivityResponse(payload) {
  if (!payload || typeof payload !== 'object'
    || typeof payload.firstVisit !== 'boolean'
    || !Array.isArray(payload.newEligibleBenefits)
    || !payload.weeklySummary || typeof payload.weeklySummary !== 'object') return false;

  const summary = payload.weeklySummary;
  return payload.newEligibleBenefits.every((item) => item
    && Number.isSafeInteger(item.benefitId) && item.benefitId > 0
    && typeof item.title === 'string'
    && typeof item.newBadge === 'boolean')
    && typeof summary.visible === 'boolean'
    && Number.isInteger(summary.newEligibleCount) && summary.newEligibleCount >= 0
    && Number.isInteger(summary.closingSoonCount) && summary.closingSoonCount >= 0
    && Number.isInteger(summary.changedBenefitCount) && summary.changedBenefitCount >= 0
    && isDateOnly(summary.periodStart)
    && isDateOnly(summary.periodEnd);
}

function isHomeCalendarResponse(payload, expectedYear, expectedMonth) {
  if (!payload || typeof payload !== 'object'
    || !Number.isInteger(payload.year) || payload.year !== expectedYear
    || !Number.isInteger(payload.month) || payload.month !== expectedMonth
    || !Array.isArray(payload.events)
    || !Number.isInteger(payload.alwaysCount) || payload.alwaysCount < 0
    || typeof payload.fallbackUsed !== 'boolean') return false;

  const monthPrefix = `${String(expectedYear).padStart(4, '0')}-${String(expectedMonth).padStart(2, '0')}-`;
  const eventTypes = new Set(['APPLY_START', 'APPLY_END', 'RESULT']);
  return payload.events.every((event) => event
    && Number.isSafeInteger(event.benefitId) && event.benefitId > 0
    && typeof event.title === 'string'
    && isDateOnly(event.date) && event.date.startsWith(monthPrefix)
    && eventTypes.has(event.eventType));
}

function isBenefitItem(item) {
  return item && Number.isInteger(item.benefitId) && typeof item.title === 'string';
}

function isHomeSummaryResponse(payload) {
  if (!payload || typeof payload !== 'object'
    || typeof payload.profileCompleted !== 'boolean'
    || typeof payload.eligibilityUpdating !== 'boolean'
    || !Array.isArray(payload.closingSoon)
    || !Array.isArray(payload.recommendations)
    || !Array.isArray(payload.nearMissBenefits)
    || !payload.amountSummary
    || !payload.applicationSummary
    || !Array.isArray(payload.applicationSummary.items)
    || !isNullableInteger(payload.profileStep)
    || !Number.isInteger(payload.totalCount) || payload.totalCount < 0
    || !Number.isInteger(payload.eligibleCount) || payload.eligibleCount < 0
    || !Number.isInteger(payload.conditionalCount) || payload.conditionalCount < 0
    || !Number.isInteger(payload.ineligibleCount) || payload.ineligibleCount < 0
    || !isNullableString(payload.benefitDataUpdatedAt)) return false;

  const amount = payload.amountSummary;
  const applications = payload.applicationSummary;
  return isNullableFiniteNumber(amount.estimatedAmount)
    && isNullableFiniteNumber(amount.maxAmount)
    && isNullableFiniteNumber(amount.annualizedAmount)
    && Number.isInteger(amount.unknownAmountCount)
    && typeof amount.duplicateExclusionApplied === 'boolean'
    && Number.isInteger(applications.inProgressCount)
    && Number.isInteger(applications.completedCount)
    && payload.closingSoon.every((item) => isBenefitItem(item)
      && typeof item.applyEnd === 'string'
      && Number.isInteger(item.dDay)
      && typeof item.eligibilityStatus === 'string')
    && payload.recommendations.every((item) => isBenefitItem(item)
      && typeof item.eligibilityStatus === 'string'
      && isNullableString(item.reason))
    && payload.nearMissBenefits.every((item) => isBenefitItem(item)
      && typeof item.eligibilityStatus === 'string'
      && isNullableString(item.reason))
    && applications.items.every((item) => isBenefitItem(item)
      && typeof item.appStatus === 'string'
      && isNullableInteger(item.step)
      && isNullableInteger(item.progressPercent));
}

function profileStepLabel(profileStep) {
  return Number.isInteger(profileStep) && profileStep > 0
    ? `프로필 ${profileStep}단계까지 입력했어요.`
    : '프로필 정보를 입력해 주세요.';
}

function renderSupportSummary(data) {
  const target = document.querySelector('#support-summary');
  if (!target) return;
  if (authState.status === 'checking') {
    target.innerHTML = getAuthCheckingMarkup();
    return;
  }
  if (authState.status === 'error') {
    target.innerHTML = getAuthErrorMarkup();
    return;
  }
  if (authState.status === 'unauthenticated') {
    target.innerHTML = getLoginRequiredMarkup('로그인하면 나에게 맞는 지원금을 확인할 수 있어요.', '로그인하고 지원금 확인하기');
    return;
  }
  if (homeSummaryState.status === 'loading' || homeSummaryState.status === 'idle') {
    target.innerHTML = getStateMarkup('support', 'loading');
    return;
  }
  if (homeSummaryState.status === 'error' || !data) {
    target.innerHTML = getStateMarkup('support', 'error');
    return;
  }
  if (!data.profileCompleted) {
    target.innerHTML = `<div class="dashboard-state dashboard-state--empty" role="status"><p>${profileStepLabel(data.profileStep)} 프로필을 완료하면 지원금을 확인할 수 있어요.</p><a class="btn btn-secondary" href="signup-profile.html">프로필 입력하기</a></div>`;
    return;
  }

  const amount = data.amountSummary;
  const estimated = amount.estimatedAmount === null ? '산정 준비 중' : formatCurrency(amount.estimatedAmount);
  const maximum = amount.maxAmount === null ? '산정 준비 중' : formatCurrency(amount.maxAmount);
  const notes = [];
  if (amount.annualizedAmount === null) notes.push('연간 환산 참고 금액은 아직 확인할 수 없어요.');
  else notes.push(`연간 환산 참고 금액 ${formatCurrency(amount.annualizedAmount)}`);
  if (amount.unknownAmountCount > 0) notes.push(`금액 정보 미확인 혜택 ${amount.unknownAmountCount}건`);
  if (amount.duplicateExclusionApplied) notes.push('중복 수급 제외 규칙을 반영했어요.');
  else notes.push('중복 수급 가능 여부는 혜택별로 확인해 주세요.');
  target.innerHTML = `<div class="support-item"><p class="support-item__label">예상 수령액</p><p class="support-item__amount">${estimated}</p></div><div class="support-item"><p class="support-item__label">최대 수령액</p><p class="support-item__amount">${maximum}</p></div><p class="support-item__note">${escapeHtml(notes.join(' · '))}</p>`;
}

function benefitLink(item, description) {
  if (!item || !Number.isInteger(item.benefitId) || typeof item.title !== 'string') return '';
  return `<a class="benefit-summary__link" href="benefit-detail.html?id=${encodeURIComponent(String(item.benefitId))}">${escapeHtml(item.title)}${description ? `<span>${escapeHtml(description)}</span>` : ''}</a>`;
}

function eligibilityCountValue(summary, field) {
  const value = summary && summary[field];
  return Number.isInteger(value) && value >= 0 ? `${value}건` : '—';
}

function eligibilityStatisticsMarkup(summary) {
  const statistics = [
    ['전체', 'totalCount'],
    ['해당', 'eligibleCount'],
    ['조건부', 'conditionalCount'],
    ['해당 없음', 'ineligibleCount']
  ];
  return `<div class="benefit-tabs" aria-label="맞춤 혜택 통계">${statistics.map(([label, field], index) => `<span class="benefit-tab${index === 0 ? ' is-current' : ''}"><span class="benefit-tab__label">${label}</span><strong class="benefit-tab__value">${eligibilityCountValue(summary, field)}</strong></span>`).join('')}</div>`;
}

function benefitSummaryMode(data) {
  if (!data.profileCompleted) return 'profile';
  if (data.eligibilityUpdating) return 'updating';
  return 'ready';
}

function daysInMonth(year, month) {
  if (month === 2) return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28;
  return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

function formatBenefitDataUpdatedAt(value) {
  if (value === null) return '정상 동기화 기록이 없습니다.';
  if (typeof value !== 'string') return '갱신 시각 형식을 확인할 수 없습니다.';
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(\.\d{1,9})?)?(Z|[+-](?:(?:0\d|1[0-7]):[0-5]\d|18:00))$/.exec(value);
  if (!match) return '갱신 시각 형식을 확인할 수 없습니다.';
  const [year, month, day, hour, minute, second] = match.slice(1, 7).map((part) => part === undefined ? undefined : Number(part));
  if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month) || hour > 23 || minute > 59 || (second !== undefined && second > 59)) {
    return '갱신 시각 형식을 확인할 수 없습니다.';
  }
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) return '갱신 시각 형식을 확인할 수 없습니다.';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(instant).reduce((values, part) => {
    if (part.type !== 'literal') values[part.type] = part.value;
    return values;
  }, {});
  return `${parts.year}년 ${parts.month}월 ${parts.day}일 ${parts.hour}:${parts.minute}:${parts.second} (KST)`;
}

function benefitDataUpdatedAtText(data, authStatus = authState.status, summaryStatus = homeSummaryState.status) {
  if (authStatus === 'checking') return '인증 상태를 확인하는 중이에요.';
  if (authStatus === 'unauthenticated') return '로그인 후 확인할 수 있어요.';
  if (authStatus === 'error' || summaryStatus === 'error' || !data) return '갱신 시각을 불러오지 못했어요.';
  if (summaryStatus === 'idle' || summaryStatus === 'loading') return '불러오는 중이에요.';
  return formatBenefitDataUpdatedAt(data.benefitDataUpdatedAt);
}

function renderBenefitSummary(data) {
  const target = document.querySelector('#benefit-summary');
  if (!target) return;
  if (authState.status === 'checking') {
    target.innerHTML = getAuthCheckingMarkup();
    return;
  }
  if (authState.status === 'error') {
    target.innerHTML = getAuthErrorMarkup();
    return;
  }
  if (authState.status === 'unauthenticated') {
    target.innerHTML = getLoginRequiredMarkup('로그인하면 맞춤 혜택을 확인할 수 있어요.', '로그인하고 혜택 확인하기');
    return;
  }
  if (homeSummaryState.status === 'loading' || homeSummaryState.status === 'idle') {
    target.innerHTML = getStateMarkup('benefits', 'loading');
    return;
  }
  if (homeSummaryState.status === 'error' || !data) {
    target.innerHTML = getStateMarkup('benefits', 'error');
    return;
  }

  const mode = benefitSummaryMode(data);
  if (mode === 'profile') {
    target.innerHTML = `<p class="benefit-description">${profileStepLabel(data.profileStep)} 프로필을 완료하면 맞춤 혜택을 알려드려요.</p><a class="btn btn-secondary" href="signup-profile.html">프로필 입력하기</a>`;
    return;
  }
  if (mode === 'updating') {
    target.innerHTML = '<div class="dashboard-state dashboard-state--loading" role="status"><p>자격 판정 결과를 분석하고 있어요. 최신 결과가 준비되면 맞춤 혜택을 보여드릴게요.</p></div>';
    return;
  }

  const statistics = eligibilityStatisticsMarkup(data);
  const recommendations = data.recommendations.slice(0, 5);
  const nearMiss = data.nearMissBenefits.slice(0, 5);
  const closingSoon = data.closingSoon.slice(0, 5);
  const sections = [];
  if (recommendations.length) {
    sections.push(`<section class="benefit-summary__section"><p class="benefit-description">맞춤 추천</p>${recommendations.map((item) => benefitLink(item, item.reason)).join('')}</section>`);
  } else if (nearMiss.length) {
    sections.push(`<section class="benefit-summary__section"><p class="benefit-description">조금만 더 확인하면 신청할 수 있어요.</p>${nearMiss.map((item) => benefitLink(item, item.reason)).join('')}</section>`);
  }
  if (closingSoon.length) {
    sections.push(`<section class="benefit-summary__section"><p class="benefit-description">마감 임박 혜택</p>${closingSoon.map((item) => benefitLink(item, item.applyEnd ? `${item.applyEnd} · D-${item.dDay}` : '')).join('')}</section>`);
  }
  target.innerHTML = sections.length
    ? `${statistics}${sections.join('')}`
    : `${statistics}<p class="benefit-description">현재 표시할 맞춤 혜택이 없어요.</p>`;
}

function weeklyActivityMarkup(data) {
  const summary = data.weeklySummary;
  const sourceIds = data.newEligibleBenefits.map((item) => String(item.benefitId)).join(',');
  if (weeklyViewState.sourceIds !== sourceIds) {
    weeklyViewState.sourceIds = sourceIds;
    weeklyViewState.visibleIds = chooseWeeklyBenefits(data.newEligibleBenefits).map((item) => String(item.benefitId));
    weeklyViewState.activeDetail = 'new';
  }
  const summaryData = homeSummaryState.status === 'ready' ? homeSummaryState.data : null;
  const canShowClosingDetails = summaryData && summaryData.profileCompleted
    && !summaryData.eligibilityUpdating && Array.isArray(summaryData.closingSoon);
  const deadlineCard = canShowClosingDetails
    ? `<button class="weekly-item weekly-item--deadline${weeklyViewState.activeDetail === 'closingSoon' ? ' is-current' : ''}" type="button" data-weekly-detail="closingSoon" aria-pressed="${weeklyViewState.activeDetail === 'closingSoon'}"><span>7일 이내 신청 마감</span><strong class="weekly-item__count">${summary.closingSoonCount}건</strong></button>`
    : `<div class="weekly-item weekly-item--deadline"><span>7일 이내 신청 마감</span><strong class="weekly-item__count">${summary.closingSoonCount}건</strong></div>`;
  const cards = `<div class="weekly-activity-counts" aria-label="이번 주 혜택 통계">
    <div class="weekly-item weekly-item--new"><span>이번 주 새로 해당</span><strong class="weekly-item__count">${summary.newEligibleCount}건</strong></div>
    ${deadlineCard}
    <div class="weekly-item weekly-item--result"><span>이번 주 변경 공고</span><strong class="weekly-item__count">${summary.changedBenefitCount}건</strong></div>
  </div>`;
  const detail = weeklyViewState.activeDetail === 'closingSoon'
    ? closingSoonActivityMarkup(summary)
    : newEligibleActivityMarkup(data.newEligibleBenefits);
  return `${cards}${detail}`;
}

function chooseWeeklyBenefits(items, excludeIds = [], random = Math.random, limit = 4) {
  const excluded = new Set(excludeIds.map(String));
  let candidates = items.filter((item) => !excluded.has(String(item.benefitId)));
  if (!candidates.length && items.length) candidates = items;
  const selected = [...candidates];
  for (let index = selected.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(random() * (index + 1));
    [selected[index], selected[swapIndex]] = [selected[swapIndex], selected[index]];
  }
  return selected.slice(0, Math.min(limit, items.length));
}

function activityBenefitLinks(items) {
  return items.map((item) => `<a class="benefit-summary__link weekly-new-benefit" href="benefit-detail.html?id=${encodeURIComponent(String(item.benefitId))}" title="${escapeHtml(item.title)}"><span class="weekly-new-benefit__title">${escapeHtml(item.title)}</span>${item.newBadge ? '<span>새로 해당</span>' : ''}</a>`).join('');
}

function newEligibleActivityMarkup(items) {
  const current = items.filter((item) => weeklyViewState.visibleIds.includes(String(item.benefitId)));
  const links = activityBenefitLinks(current);
  const countNote = `<p class="weekly-list-count">미확인 혜택 ${items.length}건 · 현재 ${current.length}건 표시</p>`;
  const empty = '<p class="weekly-list-empty">아직 확인하지 않은 해당 혜택이 없어요.</p>';
  const more = items.length > 4
    ? '<button type="button" class="weekly-more-button" data-weekly-more>다른 혜택 보기</button>'
    : '';
  return `<section class="weekly-new-benefits"><div class="weekly-list-heading"><p class="benefit-description">미확인 새로 해당 혜택</p><a class="weekly-more-link" href="benefit-recommend.html">맞춤 혜택 더 보기</a></div>${countNote}${links || empty}${more}</section>`;
}

function closingSoonActivityMarkup(summary) {
  const count = summary.closingSoonCount;
  const summaryData = homeSummaryState.status === 'ready' ? homeSummaryState.data : null;
  const heading = '<div class="weekly-list-heading"><p class="benefit-description">7일 이내 신청 마감</p><button class="weekly-detail-back" type="button" data-weekly-detail="new">미확인 혜택 보기</button></div>';
  if (count === 0) return `<section class="weekly-new-benefits">${heading}<p class="weekly-list-empty">7일 이내 마감되는 해당 혜택이 없어요.</p></section>`;
  if (!summaryData || !summaryData.profileCompleted || summaryData.eligibilityUpdating || !Array.isArray(summaryData.closingSoon)) {
    return `<section class="weekly-new-benefits">${heading}<p class="weekly-list-empty">마감 상세 목록을 불러올 수 없어요.</p></section>`;
  }
  const available = summaryData.closingSoon;
  const current = available.slice(0, 4);
  if (!current.length) return `<section class="weekly-new-benefits">${heading}<p class="weekly-list-empty">마감 상세 목록을 불러올 수 없어요.</p></section>`;
  const countNote = count === available.length
    ? `<p class="weekly-list-count">주간 집계 ${count}건 · 상세 ${current.length}건 표시</p>`
    : `<p class="weekly-list-count">주간 집계 ${count}건 · 서버가 제공한 상세 목록 ${available.length}건 중 ${current.length}건 표시</p>`;
  const links = available.slice(0, 4).map((item) => `<a class="benefit-summary__link weekly-new-benefit" href="benefit-detail.html?id=${encodeURIComponent(String(item.benefitId))}" title="${escapeHtml(item.title)}"><span class="weekly-new-benefit__title">${escapeHtml(item.title)}</span><span>${escapeHtml(item.applyEnd || '')}</span></a>`).join('');
  return `<section class="weekly-new-benefits">${heading}${countNote}${links}</section>`;
}

function showOtherWeeklyBenefits() {
  const data = homeActivityState.data;
  if (!data || data.newEligibleBenefits.length <= 4) return;
  const next = chooseWeeklyBenefits(data.newEligibleBenefits, weeklyViewState.visibleIds);
  weeklyViewState.visibleIds = next.map((item) => String(item.benefitId));
  weeklyViewState.activeDetail = 'new';
  renderWeeklySummary(data, homeActivityState.status);
}

function renderWeeklySummary(data, status = homeActivityState.status) {
  const target = document.querySelector('#weekly-summary');
  const period = document.querySelector('#weekly-period');
  if (!target) return;
  if (authState.status === 'checking') {
    if (period) period.textContent = '';
    target.innerHTML = getAuthCheckingMarkup();
    return;
  }
  if (authState.status === 'error') {
    if (period) period.textContent = '';
    target.innerHTML = getAuthErrorMarkup();
    return;
  }
  if (authState.status === 'unauthenticated') {
    if (period) period.textContent = '';
    target.innerHTML = getLoginRequiredMarkup('로그인하면 이번 주 혜택 소식과 새로 해당된 혜택을 확인할 수 있어요.', '로그인하기');
    return;
  }
  if (status === 'idle' || status === 'loading') {
    if (period) period.textContent = '';
    target.innerHTML = getStateMarkup('weekly', 'loading');
    return;
  }
  if (status === 'error' || !data) {
    if (period) period.textContent = '';
    target.innerHTML = getStateMarkup('weekly', 'error');
    return;
  }
  if (period) period.textContent = `${data.weeklySummary.periodStart.replaceAll('-', '.')} – ${data.weeklySummary.periodEnd.replaceAll('-', '.')}`;
  target.innerHTML = weeklyActivityMarkup(data);
}

function getApplicationIcon(iconName) {
  const icons = {
    house: '<svg class="application-item__icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="m3 11 9-7 9 7v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"></path><path d="M9 21v-6h6v6"></path></svg>',
    wallet: '<svg class="application-item__icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M20 7V6a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h15v8a2 2 0 0 1-2 2H5a3 3 0 0 1-3-3V7"></path><path d="M16 14h.01"></path></svg>'
  };
  return icons[iconName] || icons.wallet;
}

function applicationStatusLabel(status) {
  return {
    IN_PROGRESS: '진행 중',
    SUBMITTED: '신청 완료',
    SELECTED: '선정',
    REJECTED: '미선정',
    CANCELED: '취소'
  }[status] || '상태 확인 필요';
}

function renderApplications(data) {
  const target = document.querySelector('#application-list');
  if (!target) return;
  if (authState.status === 'checking') {
    target.innerHTML = getAuthCheckingMarkup();
    return;
  }
  if (authState.status === 'error') {
    target.innerHTML = getAuthErrorMarkup();
    return;
  }
  if (authState.status === 'unauthenticated') {
    target.innerHTML = getLoginRequiredMarkup('로그인하면 신청 진행 상황과 마감일을 확인할 수 있어요.', '로그인하고 신청 현황 확인하기');
    return;
  }
  if (homeSummaryState.status === 'loading' || homeSummaryState.status === 'idle') {
    target.innerHTML = getStateMarkup('applications', 'loading');
    return;
  }
  if (homeSummaryState.status === 'error' || !data) {
    target.innerHTML = getStateMarkup('applications', 'error');
    return;
  }
  const summary = data.applicationSummary;
  const items = Array.isArray(summary.items) ? summary.items.slice(0, 5) : [];
  const counts = `<p class="application-summary-count">진행 중 ${summary.inProgressCount}건 · 완료 ${summary.completedCount}건</p>`;
  if (!items.length) {
    target.innerHTML = `${counts}${getStateMarkup('applications', 'empty')}<a class="btn btn-secondary application-all-link" href="application-status.html">신청 현황 전체 보기</a>`;
    return;
  }
  const markup = items.map((item) => {
    const title = typeof item.title === 'string' ? escapeHtml(item.title) : '혜택 정보 확인 필요';
    const href = Number.isInteger(item.benefitId) ? `benefit-detail.html?id=${encodeURIComponent(String(item.benefitId))}` : 'application-status.html';
    const hasProgress = Number.isInteger(item.progressPercent) && item.progressPercent >= 0 && item.progressPercent <= 100;
    const progress = hasProgress ? `<div class="application-item__progress"><span class="progress-track"><span style="width: ${item.progressPercent}%"></span></span><small>${item.progressPercent}%</small></div>` : '';
    const step = Number.isInteger(item.step) ? `${item.step}단계 진행 중` : '현재 단계 확인 중';
    return `<a class="application-item" href="${href}">${getApplicationIcon('wallet')}<span><strong class="application-item__name">${title}</strong><span class="application-item__stage">${step}</span></span><span class="application-item__status">${applicationStatusLabel(item.appStatus)}</span>${progress}</a>`;
  }).join('');
  target.innerHTML = `${counts}${markup}<a class="btn btn-secondary application-all-link" href="application-status.html">신청 현황 전체 보기</a>`;
}

function getSchedulesForDate(dateKey) { return calendarState.events.filter((event) => event.date === dateKey); }

function calendarEventInfo(eventType) {
  return {
    APPLY_START: { label: '신청 시작', tone: 'start' },
    APPLY_END: { label: '신청 마감', tone: 'deadline' },
    RESULT: { label: '결과 발표', tone: 'result' }
  }[eventType] || { label: '일정', tone: 'start' };
}

function renderSelectedSchedules() {
  const title = document.querySelector('#selected-date-title');
  const target = document.querySelector('#schedule-list');
  if (!title || !target) return;
  const [, month, day] = calendarState.selectedDate.split('-').map(Number);
  title.textContent = `${month}월 ${day}일 일정`;
  const schedules = getSchedulesForDate(calendarState.selectedDate);
  target.innerHTML = schedules.length ? schedules.map((event) => {
    const info = calendarEventInfo(event.eventType);
    const contents = `<span class="schedule-item__name"><i class="schedule-dot schedule-dot--${info.tone}"></i>${escapeHtml(event.title)}</span><span class="schedule-item__type">${info.label}</span>`;
    return `<a class="schedule-item" href="benefit-detail.html?id=${encodeURIComponent(String(event.benefitId))}">${contents}</a>`;
  }).join('') : '<p class="schedule-empty">선택한 날짜에는 등록된 일정이 없어요.</p>';
}

function renderDateNotices() {
  const target = document.querySelector('#calendar-date-notices');
  if (!target) return;
  if (calendarState.status !== 'ready') {
    target.innerHTML = '';
    return;
  }
  const notices = [];
  if (calendarState.alwaysCount > 0) {
    notices.push(`<p><strong>상시 신청</strong><span>날짜가 정해지지 않은 상시 신청 혜택 ${calendarState.alwaysCount}건이 있어요.</span></p>`);
  }
  if (calendarState.fallbackUsed) {
    notices.push('<p><strong>마감 임박 혜택</strong><span>관심 혜택이나 진행 중인 신청이 없어 선택한 달의 마감 임박 혜택을 대신 표시하고 있어요.</span></p>');
  }
  target.innerHTML = notices.join('');
}

function getEventTone(dateKey) {
  const schedules = getSchedulesForDate(dateKey);
  if (schedules.some((event) => event.eventType === 'APPLY_END')) return 'deadline';
  if (schedules.some((event) => event.eventType === 'RESULT')) return 'result';
  return schedules.length ? 'start' : '';
}

function renderCalendar() {
  const daysTarget = document.querySelector('#calendar-days');
  const monthTitle = document.querySelector('#calendar-month');
  const scheduleTarget = document.querySelector('#schedule-list');
  const scheduleTitle = document.querySelector('#selected-date-title');
  const navigation = document.querySelectorAll('[data-calendar-nav]');
  if (!daysTarget || !monthTitle || !scheduleTarget || !scheduleTitle) return;
  const year = calendarState.displayedDate.getFullYear();
  const month = calendarState.displayedDate.getMonth();
  monthTitle.textContent = `${year}년 ${month + 1}월`;
  navigation.forEach((button) => { button.disabled = authState.status !== 'authenticated'; });

  let statusMarkup = '';
  if (authState.status === 'checking') statusMarkup = getAuthCheckingMarkup();
  else if (authState.status === 'error') statusMarkup = getAuthErrorMarkup();
  else if (authState.status === 'unauthenticated') statusMarkup = getLoginRequiredMarkup('로그인하면 관심 혜택과 신청 중인 혜택의 일정을 확인할 수 있어요.', '로그인하기');
  else if (calendarState.status === 'idle' || calendarState.status === 'loading') statusMarkup = getStateMarkup('calendar', 'loading');
  else if (calendarState.status === 'error') statusMarkup = getStateMarkup('calendar', 'error');

  if (statusMarkup) {
    daysTarget.classList.toggle('calendar-days--state', true);
    daysTarget.innerHTML = statusMarkup;
    scheduleTitle.textContent = '일정 안내';
    scheduleTarget.innerHTML = '';
    renderDateNotices();
    return;
  }

  daysTarget.classList.toggle('calendar-days--state', false);
  const firstDay = new Date(year, month, 1).getDay();
  const lastDate = new Date(year, month + 1, 0).getDate();
  const todayParts = getSeoulDateParts();
  const todayKey = formatDateKey(todayParts.year, todayParts.month - 1, todayParts.day);
  const blankDays = Array.from({ length: firstDay }, () => '<span class="calendar-date calendar-date--empty" aria-hidden="true"></span>');
  const dateButtons = Array.from({ length: lastDate }, (_, index) => {
    const day = index + 1;
    const dateKey = formatDateKey(year, month, day);
    const isToday = dateKey === todayKey;
    const eventTone = getEventTone(dateKey);
    const classes = ['calendar-date'];
    if (isToday) classes.push('calendar-date--today');
    if (dateKey === calendarState.selectedDate) classes.push('is-selected');
    if (eventTone) classes.push('has-event', `has-event--${eventTone}`);
    return `<button class="${classes.join(' ')}" type="button" data-calendar-date="${dateKey}" aria-label="${month + 1}월 ${day}일${eventTone ? ', 일정 있음' : ''}" aria-pressed="${dateKey === calendarState.selectedDate}">${day}</button>`;
  });
  daysTarget.innerHTML = [...blankDays, ...dateButtons].join('');
  renderSelectedSchedules();
  renderDateNotices();
}

function changeCalendarMonth(direction) {
  calendarState.displayedDate = new Date(calendarState.displayedDate.getFullYear(), calendarState.displayedDate.getMonth() + direction, 1);
  const year = calendarState.displayedDate.getFullYear();
  const month = calendarState.displayedDate.getMonth();
  calendarState.selectedDate = formatDateKey(year, month, 1);
  calendarState.events = [];
  calendarState.alwaysCount = 0;
  calendarState.fallbackUsed = false;
  return loadHomeCalendar({ force: true });
}

async function loadHomeCalendar({ force = false } = {}) {
  if (!window.BokdeongiAuth || authState.status !== 'authenticated') return;
  if (!force && (calendarState.status === 'loading' || calendarState.status === 'ready')) return;

  const year = calendarState.displayedDate.getFullYear();
  const month = calendarState.displayedDate.getMonth() + 1;
  const requestId = ++calendarState.requestId;
  calendarState.status = 'loading';
  renderCalendar();

  try {
    const response = await window.BokdeongiAuth.authenticatedFetch(`/api/home/calendar?year=${year}&month=${month}`);
    if (requestId !== calendarState.requestId) return;
    if (!response.ok) throw new Error(`HOME-03 request failed: ${response.status}`);

    const payload = await response.json().catch(() => null);
    if (!isHomeCalendarResponse(payload, year, month)) throw new Error('Invalid HOME-03 response');

    calendarState.events = payload.events;
    calendarState.alwaysCount = payload.alwaysCount;
    calendarState.fallbackUsed = payload.fallbackUsed;
    calendarState.status = 'ready';
  } catch {
    if (requestId !== calendarState.requestId) return;
    calendarState.events = [];
    calendarState.alwaysCount = 0;
    calendarState.fallbackUsed = false;
    calendarState.status = 'error';
  }
  renderCalendar();
}

function showToast(message) {
  const toast = document.querySelector('#toast');
  if (!toast) return;
  toast.textContent = message;
  toast.hidden = false;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 3000);
}

function showOAuthResultNotice() {
  const parameters = new URLSearchParams(window.location.search);

  if (parameters.get('login') === 'failed') {
    showToast('카카오 로그인을 완료하지 못했습니다. 다시 시도해 주세요.');
    window.history.replaceState(null, document.title, `${window.location.pathname}${window.location.hash}`);
  }
}

function handleKakaoSignupRedirect() {
  if (!oauthResult.kakaoSignupRequested || oauthResult.kakaoSignupHandled) return;
  if (authState.status === 'checking') return;

  oauthResult.kakaoSignupHandled = true;

  if (authState.status === 'authenticated') {
    showToast('로그인된 계정이 있어 카카오 가입 절차를 시작하지 않았습니다.');
    window.history.replaceState(null, document.title, `${window.location.pathname}${window.location.hash}`);
    return;
  }

  if (authState.status === 'unauthenticated') {
    window.location.replace('signup-account.html?kakaoSignup=true');
    return;
  }

  showToast('인증 상태를 확인하지 못해 카카오 가입 절차를 시작하지 않았습니다. 다시 로그인해 주세요.');
  window.history.replaceState(null, document.title, `${window.location.pathname}${window.location.hash}`);
}

function handleKakaoReconsentRedirect() {
  if (!oauthResult.kakaoReconsentRequested || oauthResult.kakaoReconsentHandled) return;
  if (authState.status === 'checking') return;
  oauthResult.kakaoReconsentHandled = true;
  if (authState.status === 'unauthenticated') {
    window.location.replace('signup-account.html?kakaoReconsent=true');
    return;
  }
  showToast(authState.status === 'authenticated'
    ? '현재 로그인된 계정이 있어 필수 약관 재동의 절차를 시작하지 않았습니다.'
    : '재동의 인증 상태를 확인하지 못했습니다. 카카오 로그인을 다시 시도해 주세요.');
  clearOAuthQuery();
}

function clearOAuthQuery() {
  window.history.replaceState(null, document.title, `${window.location.pathname}${window.location.hash}`);
}

function isProfileOnboardingResponse(payload) {
  return payload
    && typeof payload.profileCompleted === 'boolean'
    && Number.isInteger(Number(payload.profileStep))
    && Number(payload.profileStep) >= 0
    && Number(payload.profileStep) <= 3;
}

async function handleProfileOnboardingRedirect() {
  if (!oauthResult.profileOnboardingRequested || oauthResult.profileOnboardingHandled) return;
  if (authState.status === 'checking') return;

  oauthResult.profileOnboardingHandled = true;

  if (authState.status !== 'authenticated' || !window.BokdeongiAuth) {
    showToast('로그인 상태를 확인하지 못해 프로필 입력을 시작하지 않았습니다. 다시 로그인해 주세요.');
    clearOAuthQuery();
    return;
  }

  try {
    const response = await window.BokdeongiAuth.authenticatedFetch('/api/me/profile');
    const payload = await response.json().catch(() => null);

    if (!response.ok || !isProfileOnboardingResponse(payload)) {
      showToast('프로필 상태를 확인하지 못해 입력을 시작하지 않았습니다. 다시 로그인해 주세요.');
      clearOAuthQuery();
      return;
    }

    if (payload.profileCompleted) {
      clearOAuthQuery();
      return;
    }

    window.location.replace('signup-account.html?profileOnboarding=true');
  } catch {
    showToast('프로필 상태를 확인하지 못해 입력을 시작하지 않았습니다. 다시 로그인해 주세요.');
    clearOAuthQuery();
  }
}

function renderHome() {
  const data = homeSummaryState.data;
  renderSupportSummary(data);
  renderBenefitSummary(data);
  renderWeeklySummary(homeActivityState.data, homeActivityState.status);
  renderApplications(data);
  renderCalendar();
  const timestamp = document.querySelector('#data-updated-at');
  if (timestamp) timestamp.textContent = benefitDataUpdatedAtText(data);
}

async function loadHomeSummary({ force = false } = {}) {
  if (!window.BokdeongiAuth || authState.status !== 'authenticated') return;
  if (homeSummaryState.status === 'loading' || (!force && homeSummaryState.status === 'ready')) return;

  const requestId = ++homeSummaryState.requestId;
  homeSummaryState.status = 'loading';
  renderHome();

  try {
    const response = await window.BokdeongiAuth.authenticatedFetch('/api/home/summary');
    if (requestId !== homeSummaryState.requestId) return;
    if (!response.ok) throw new Error(`HOME-01 request failed: ${response.status}`);

    const payload = await response.json().catch(() => null);
    if (!isHomeSummaryResponse(payload)) throw new Error('Invalid HOME-01 response');

    homeSummaryState.data = payload;
    homeSummaryState.status = 'ready';
  } catch {
    if (requestId !== homeSummaryState.requestId) return;
    homeSummaryState.data = null;
    homeSummaryState.status = 'error';
  }
  renderHome();
}

async function loadHomeActivity({ force = false } = {}) {
  if (!window.BokdeongiAuth || authState.status !== 'authenticated') return;
  if (homeActivityState.status === 'loading' || (!force && homeActivityState.status === 'ready')) return;

  const requestId = ++homeActivityState.requestId;
  homeActivityState.status = 'loading';
  renderWeeklySummary(homeActivityState.data, homeActivityState.status);

  try {
    const response = await window.BokdeongiAuth.authenticatedFetch('/api/home/activity');
    if (requestId !== homeActivityState.requestId) return;
    if (!response.ok) throw new Error(`HOME-02 request failed: ${response.status}`);

    const payload = await response.json().catch(() => null);
    if (!isHomeActivityResponse(payload)) throw new Error('Invalid HOME-02 response');

    homeActivityState.data = payload;
    homeActivityState.status = 'ready';
  } catch {
    if (requestId !== homeActivityState.requestId) return;
    homeActivityState.data = null;
    homeActivityState.status = 'error';
  }
  renderWeeklySummary(homeActivityState.data, homeActivityState.status);
}

function renderDevelopmentPreview() {
  if (!developmentPreview.enabled) return;
  const preview = document.createElement('aside');
  preview.className = 'dev-preview';
  preview.setAttribute('aria-label', '개발용 홈 화면 미리보기');
  preview.innerHTML = '<strong>개발용 Mock 미리보기</strong><span>실제 로그인, 사용자 정보, 서버 데이터와 분리된 상태 시뮬레이터입니다.</span><div><button type="button" data-preview-login="false">비로그인 목업</button><button type="button" data-preview-login="true">로그인 목업</button></div><label>대시보드 상태 <select data-preview-state><option value="ready">정상 데이터</option><option value="loading">로딩 중</option><option value="empty">데이터 없음</option><option value="error">조회 실패</option></select></label><p data-preview-summary></p>';
  document.body.append(preview);
}

function updateDevelopmentPreview() {
  const preview = document.querySelector('.dev-preview');
  if (!preview) return;
  preview.querySelectorAll('[data-preview-login]').forEach((button) => {
    const selected = String(developmentPreview.mockLogin) === button.dataset.previewLogin;
    button.classList.toggle('is-active', selected);
    button.setAttribute('aria-pressed', String(selected));
  });
  const stateSelect = preview.querySelector('[data-preview-state]');
  if (stateSelect) stateSelect.value = developmentPreview.dashboardState;
  const summary = preview.querySelector('[data-preview-summary]');
  if (summary) summary.textContent = `${developmentPreview.mockLogin ? '로그인' : '비로그인'} 목업 · ${developmentPreview.dashboardState} 상태 (홈의 실제 인증 상태는 변경되지 않음)`;
}

function setAuthenticationStatus(status) {
  if (!AUTH_STATES.has(status)) return;
  const previousStatus = authState.status;
  authState.status = status;
  if (status !== 'authenticated') {
    homeSummaryState.requestId += 1;
    homeSummaryState.status = 'idle';
    homeSummaryState.data = null;
    homeActivityState.requestId += 1;
    homeActivityState.status = 'idle';
    homeActivityState.data = null;
    weeklyViewState.sourceIds = '';
    weeklyViewState.visibleIds = [];
    weeklyViewState.activeDetail = 'new';
    calendarState.requestId += 1;
    calendarState.status = 'idle';
    calendarState.events = [];
    calendarState.alwaysCount = 0;
    calendarState.fallbackUsed = false;
  }
  renderHome();
  handleKakaoSignupRedirect();
  handleKakaoReconsentRedirect();
  void handleProfileOnboardingRedirect();
  if (status === 'authenticated' && previousStatus !== 'authenticated') {
    void loadHomeSummary();
    void loadHomeActivity();
    void loadHomeCalendar();
  }
}

window.addEventListener('bokdeongi:auth-state-change', (event) => {
  setAuthenticationStatus(event.detail && event.detail.status);
});

function bindEvents() {
  document.addEventListener('click', (event) => {
    const loginRequired = event.target.closest('[data-login-required]');
    if (loginRequired) {
      if (authState.status === 'authenticated') return;
      event.preventDefault();
      if (authState.status === 'checking') showToast('인증 상태를 확인하는 중이에요.');
      else window.location.assign('login.html');
      return;
    }
    const authRetry = event.target.closest('[data-auth-retry]');
    if (authRetry) {
      if (window.BokdeongiAuth) window.BokdeongiAuth.refreshAuthentication();
      else setAuthenticationStatus('error');
      return;
    }
    const calendarDate = event.target.closest('[data-calendar-date]');
    if (calendarDate) { calendarState.selectedDate = calendarDate.dataset.calendarDate; renderCalendar(); return; }
    const calendarNavigation = event.target.closest('[data-calendar-nav]');
    if (calendarNavigation) { changeCalendarMonth(calendarNavigation.dataset.calendarNav === 'next' ? 1 : -1); return; }
    const weeklyDetail = event.target.closest('[data-weekly-detail]');
    if (weeklyDetail) { weeklyViewState.activeDetail = weeklyDetail.dataset.weeklyDetail; renderWeeklySummary(homeActivityState.data, homeActivityState.status); return; }
    const weeklyMore = event.target.closest('[data-weekly-more]');
    if (weeklyMore) { showOtherWeeklyBenefits(); return; }
    const category = event.target.closest('[data-benefit-category]');
    if (category) { document.querySelectorAll('.benefit-tab').forEach((tab) => tab.classList.toggle('is-current', tab === category)); return; }
    const retry = event.target.closest('[data-retry-section]');
    if (retry) {
      if (retry.dataset.retrySection === 'weekly') void loadHomeActivity({ force: true });
      else if (retry.dataset.retrySection === 'calendar') void loadHomeCalendar({ force: true });
      else void loadHomeSummary({ force: true });
      return;
    }
    const previewLogin = event.target.closest('[data-preview-login]');
    if (previewLogin && developmentPreview.enabled) { developmentPreview.mockLogin = previewLogin.dataset.previewLogin === 'true'; updateDevelopmentPreview(); return; }
  });
  document.addEventListener('change', (event) => {
    if (event.target.matches('[data-preview-state]') && developmentPreview.enabled) {
      developmentPreview.dashboardState = event.target.value;
      updateDevelopmentPreview();
    }
  });
}

function initializeHome() {
  renderDevelopmentPreview();
  renderHome();
  updateDevelopmentPreview();
  bindEvents();
  showOAuthResultNotice();
  if (!window.BokdeongiAuth) {
    setAuthenticationStatus('error');
    return;
  }
  setAuthenticationStatus(window.BokdeongiAuth.getAuthenticationState());
  window.BokdeongiAuth.refreshAuthentication();
}

document.addEventListener('DOMContentLoaded', initializeHome);
