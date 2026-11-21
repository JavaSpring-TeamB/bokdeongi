/* 홈 화면의 Mock Data는 API 명세 확정 후 데이터 조회 함수로 교체합니다. */
const initialDate = new Date();

function isDevelopmentHost() {
  return window.location.protocol === 'file:' || ['localhost', '127.0.0.1'].includes(window.location.hostname);
}

function isDevelopmentPreviewEnabled() {
  return document.documentElement.dataset.enableMockPreview === 'true' && isDevelopmentHost();
}

const AUTH_STATES = new Set(['checking', 'authenticated', 'unauthenticated', 'error']);
const authState = { status: 'checking' };
const developmentPreview = { enabled: isDevelopmentPreviewEnabled(), mockLogin: false, dashboardState: 'ready' };
const oauthParameters = new URLSearchParams(window.location.search);
const oauthResult = {
  kakaoSignupRequested: oauthParameters.get('login') !== 'failed' && oauthParameters.get('kakaoSignup') === 'true',
  kakaoSignupHandled: false,
  profileOnboardingRequested: oauthParameters.get('profileOnboarding') === 'true',
  profileOnboardingHandled: false
};

function formatDateKey(year, monthIndex, day) {
  return `${year}-${String(monthIndex + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function getDateKeyWithOffset(baseDate, offset) {
  const date = new Date(baseDate);
  date.setDate(date.getDate() + offset);
  return formatDateKey(date.getFullYear(), date.getMonth(), date.getDate());
}

function createMockSchedules(baseDate) {
  return [
    { date: getDateKeyWithOffset(baseDate, -3), name: '청년 월세 지원', type: '신청 시작', tone: 'start', benefitId: 1 },
    { date: getDateKeyWithOffset(baseDate, 0), name: '청년 생활 지원', type: '결과 발표', tone: 'result' },
    { date: getDateKeyWithOffset(baseDate, 5), name: '청년 월세 지원', type: '신청 마감 (D-5)', tone: 'deadline', benefitId: 1 },
    { date: getDateKeyWithOffset(baseDate, 5), name: '청년 문화생활 지원', type: '결과 발표', tone: 'result' }
  ];
}

function formatMockTimestamp(date) {
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')} 06:00`;
}

const mockPreviewData = {
  supportSummary: { expectedAmount: 420000, maximumAmount: 1200000, note: '중복 수혜가 불가능한 혜택은 가장 높은 1건만 반영한 예시입니다.' },
  benefitSummary: {
    categories: [{ label: '전체 12건' }, { label: '해당 6건' }, { label: '조건부 4건' }, { label: '해당 없음 2건' }],
    newBenefits: 2,
    description: '이번 주 새로 나에게 맞는 혜택이 2건 추가되었어요.'
  },
  weeklySummary: {
    period: '11.09 ~ 11.15',
    items: [
      { label: '신규 혜택', count: 2, tone: 'new', href: 'benefit-recommend.html' },
      { label: '마감 임박', count: 3, tone: 'deadline', href: 'benefit-list.html' },
      { label: '결과 발표 예정', count: 1, tone: 'result', href: 'application-status.html' },
      { label: '공고 변경', count: 1, tone: 'default', href: 'benefit-list.html' }
    ]
  },
  applications: [
    { name: '청년 월세 지원', stage: '서류 검토 중', progress: 60, status: '진행 중', icon: 'house' },
    { name: '청년 주거 지원', stage: '서류 준비 중', progress: 30, status: '준비 중', icon: 'house' },
    { name: '청년 생활 지원', stage: '신청 완료', progress: 100, status: '완료', icon: 'wallet' }
  ],
  schedules: createMockSchedules(initialDate),
  dateNotices: [
    { status: '상시 신청', description: '상시 신청 혜택은 특정 날짜 없이 별도로 안내합니다.' },
    { status: '공고 확인 필요', description: '정확한 날짜가 없는 혜택은 공고문을 확인한 뒤 표시합니다.' }
  ],
  updatedAt: formatMockTimestamp(initialDate)
};

const calendarState = { displayedDate: new Date(initialDate.getFullYear(), initialDate.getMonth(), 1), selectedDate: getDateKeyWithOffset(initialDate, 0) };
const stateMessages = {
  support: { loading: '지원금을 불러오는 중이에요.', empty: '현재 표시할 지원금이 없어요.', error: '지원금 정보를 불러오지 못했어요.' },
  benefits: { loading: '맞춤 혜택을 불러오는 중이에요.', empty: '아직 맞춤 혜택 결과가 없어요.', error: '맞춤 혜택 정보를 불러오지 못했어요.' },
  weekly: { loading: '이번 주 혜택 소식을 불러오는 중이에요.', empty: '이번 주 새로운 혜택 소식이 없어요.', error: '혜택 소식을 불러오지 못했어요.' },
  applications: { loading: '신청 현황을 불러오는 중이에요.', empty: '진행 중인 신청 혜택이 없어요.', error: '신청 현황을 불러오지 못했어요.' },
  calendar: { loading: '신청 일정을 불러오는 중이에요.', empty: '표시할 신청 일정이 없어요.', error: '신청 일정을 불러오지 못했어요.' }
};

function formatCurrency(amount) { return `${Number(amount).toLocaleString('ko-KR')}원`; }
function getSectionState() { return 'unavailable'; }
function getLoginRequiredMarkup(message, actionLabel) { return `<div class="login-required"><p>${message}</p><a class="btn btn-secondary" href="login.html" data-login-required>${actionLabel}</a></div>`; }
function getAuthCheckingMarkup() { return '<div class="dashboard-state dashboard-state--loading" role="status"><p>인증 상태를 확인하는 중이에요.</p></div>'; }
function getAuthErrorMarkup() { return '<div class="dashboard-state dashboard-state--error" role="status"><p>인증 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.</p><button class="btn btn-secondary dashboard-state__retry" type="button" data-auth-retry>인증 상태 다시 확인</button></div>'; }
function getDataUnavailableMarkup() { return '<div class="dashboard-state dashboard-state--empty" role="status"><p>실제 데이터 연동 전에는 정보를 표시하지 않아요.</p></div>'; }

function getStateMarkup(section, state) {
  const retry = state === 'error' ? `<button class="btn btn-secondary dashboard-state__retry" type="button" data-retry-section="${section}">다시 시도</button>` : '';
  return `<div class="dashboard-state dashboard-state--${state}" role="status"><p>${stateMessages[section][state]}</p>${retry}</div>`;
}

function renderSupportSummary(data, isLoggedIn, state) {
  const target = document.querySelector('#support-summary');
  if (!target || !data) return;
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
  target.innerHTML = getDataUnavailableMarkup();
}

function renderBenefitSummary(data, state) {
  const target = document.querySelector('#benefit-summary');
  if (!target || !data) return;
  target.innerHTML = getDataUnavailableMarkup();
}

function renderWeeklySummary(data, state) {
  const target = document.querySelector('#weekly-summary');
  const period = document.querySelector('#weekly-period');
  if (!target || !data) return;
  if (period) period.textContent = '';
  target.innerHTML = getDataUnavailableMarkup();
}

function getApplicationIcon(iconName) {
  const icons = {
    house: '<svg class="application-item__icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="m3 11 9-7 9 7v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"></path><path d="M9 21v-6h6v6"></path></svg>',
    wallet: '<svg class="application-item__icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M20 7V6a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h15v8a2 2 0 0 1-2 2H5a3 3 0 0 1-3-3V7"></path><path d="M16 14h.01"></path></svg>'
  };
  return icons[iconName] || icons.wallet;
}

function renderApplications(applications, isLoggedIn, state) {
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
  target.innerHTML = getDataUnavailableMarkup();
}

function getSchedulesForDate(dateKey) { return mockPreviewData.schedules.filter((schedule) => schedule.date === dateKey); }

function renderSelectedSchedules() {
  const title = document.querySelector('#selected-date-title');
  const target = document.querySelector('#schedule-list');
  if (!title || !target) return;
  const selected = new Date(`${calendarState.selectedDate}T00:00:00`);
  title.textContent = `${selected.getMonth() + 1}월 ${selected.getDate()}일 일정`;
  const schedules = getSchedulesForDate(calendarState.selectedDate);
  target.innerHTML = schedules.length ? schedules.map((schedule) => {
    const contents = `<span class="schedule-item__name"><i class="schedule-dot schedule-dot--${schedule.tone}"></i>${schedule.name}</span><span class="schedule-item__type">${schedule.type}</span>`;
    return schedule.benefitId
      ? `<a class="schedule-item" href="benefit-detail.html?id=${encodeURIComponent(schedule.benefitId)}">${contents}</a>`
      : `<span class="schedule-item schedule-item--unavailable" title="개발용 Mock 일정에는 상세 혜택 정보가 없습니다.">${contents}</span>`;
  }).join('') : '<p class="schedule-empty">선택한 날짜에는 등록된 일정이 없어요.</p>';
}

function renderDateNotices(state) {
  const target = document.querySelector('#calendar-date-notices');
  if (!target) return;
  target.innerHTML = state === 'ready' ? mockPreviewData.dateNotices.map((notice) => `<p><strong>${notice.status}</strong><span>${notice.description}</span></p>`).join('') : '';
}

function getEventTone(dateKey) {
  const schedules = getSchedulesForDate(dateKey);
  if (schedules.some((schedule) => schedule.tone === 'deadline')) return 'deadline';
  if (schedules.some((schedule) => schedule.tone === 'result')) return 'result';
  return schedules.length ? 'start' : '';
}

function renderCalendar(state = getSectionState()) {
  const daysTarget = document.querySelector('#calendar-days');
  const monthTitle = document.querySelector('#calendar-month');
  const scheduleTarget = document.querySelector('#schedule-list');
  const scheduleTitle = document.querySelector('#selected-date-title');
  const navigation = document.querySelectorAll('[data-calendar-nav]');
  if (!daysTarget || !monthTitle || !scheduleTarget || !scheduleTitle) return;
  navigation.forEach((button) => { button.disabled = state !== 'ready'; });
  daysTarget.classList.toggle('calendar-days--state', state !== 'ready');
  if (state !== 'ready') {
    monthTitle.textContent = '신청 일정';
    scheduleTitle.textContent = '일정 안내';
    daysTarget.innerHTML = getDataUnavailableMarkup();
    scheduleTarget.innerHTML = '';
    renderDateNotices(state);
    return;
  }
  const year = calendarState.displayedDate.getFullYear();
  const month = calendarState.displayedDate.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const lastDate = new Date(year, month + 1, 0).getDate();
  const today = new Date();
  monthTitle.textContent = `${year}년 ${month + 1}월`;
  const blankDays = Array.from({ length: firstDay }, () => '<span class="calendar-date calendar-date--empty" aria-hidden="true"></span>');
  const dateButtons = Array.from({ length: lastDate }, (_, index) => {
    const day = index + 1;
    const dateKey = formatDateKey(year, month, day);
    const isToday = today.getFullYear() === year && today.getMonth() === month && today.getDate() === day;
    const eventTone = getEventTone(dateKey);
    const classes = ['calendar-date'];
    if (isToday) classes.push('calendar-date--today');
    if (dateKey === calendarState.selectedDate) classes.push('is-selected');
    if (eventTone) classes.push('has-event', `has-event--${eventTone}`);
    return `<button class="${classes.join(' ')}" type="button" data-calendar-date="${dateKey}" aria-label="${month + 1}월 ${day}일${eventTone ? ', 일정 있음' : ''}" aria-pressed="${dateKey === calendarState.selectedDate}">${day}</button>`;
  });
  daysTarget.innerHTML = [...blankDays, ...dateButtons].join('');
  renderSelectedSchedules();
  renderDateNotices(state);
}

function changeCalendarMonth(direction) {
  calendarState.displayedDate = new Date(calendarState.displayedDate.getFullYear(), calendarState.displayedDate.getMonth() + direction, 1);
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
  const state = getSectionState();
  renderSupportSummary(mockPreviewData.supportSummary, false, state);
  renderBenefitSummary(mockPreviewData.benefitSummary, state);
  renderWeeklySummary(mockPreviewData.weeklySummary, state);
  renderApplications(mockPreviewData.applications, false, state);
  renderCalendar(state);
  const timestamp = document.querySelector('#data-updated-at');
  if (timestamp) timestamp.textContent = '실제 데이터 연동 전';
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
  authState.status = status;
  renderHome();
  handleKakaoSignupRedirect();
  void handleProfileOnboardingRedirect();
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
    const category = event.target.closest('[data-benefit-category]');
    if (category) { document.querySelectorAll('.benefit-tab').forEach((tab) => tab.classList.toggle('is-current', tab === category)); return; }
    const retry = event.target.closest('[data-retry-section]');
    if (retry) { showToast('현재는 개발용 상태 미리보기입니다. API 연동 후 이 영역을 다시 조회할 수 있어요.'); return; }
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
