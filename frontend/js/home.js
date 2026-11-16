/* 홈 화면의 Mock Data는 API 명세 확정 후 데이터 조회 함수로 교체합니다. */
const initialDate = new Date();
const mockSession = {
  isLoggedIn: false
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
    { date: getDateKeyWithOffset(baseDate, -3), name: '청년 월세 지원', type: '신청 시작', tone: 'start' },
    { date: getDateKeyWithOffset(baseDate, 0), name: '청년 생활 지원', type: '결과 발표', tone: 'result' },
    { date: getDateKeyWithOffset(baseDate, 5), name: '청년 월세 지원', type: '신청 마감 (D-5)', tone: 'deadline' },
    { date: getDateKeyWithOffset(baseDate, 5), name: '청년 문화생활 지원', type: '결과 발표', tone: 'result' }
  ];
}

function formatMockTimestamp(date) {
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')} 06:00`;
}

const mockHomeData = {
  supportSummary: {
    expectedAmount: 420000,
    maximumAmount: 1200000,
    note: '중복 수혜가 불가능한 혜택은 가장 높은 1건만 반영한 예시입니다.'
  },
  benefitSummary: {
    categories: [
      { label: '전체 12건' },
      { label: '해당 6건' },
      { label: '조건부 4건' },
      { label: '해당 없음 2건' }
    ],
    newBenefits: 2,
    description: '이번 주 새로 나에게 맞는 혜택이 2건 추가되었어요.'
  },
  weeklySummary: {
    period: '11.09 ~ 11.15',
    items: [
      { label: '신규 혜택', count: 2, tone: 'new' },
      { label: '마감 임박', count: 3, tone: 'deadline' },
      { label: '결과 발표 예정', count: 1, tone: 'result' },
      { label: '공고 변경', count: 1, tone: 'default' }
    ]
  },
  applications: [
    { name: '청년 월세 지원', stage: '서류 검토 중', progress: 60, status: '진행 중', icon: 'house' },
    { name: '청년 주거 지원', stage: '서류 준비 중', progress: 30, status: '준비 중', icon: 'house' },
    { name: '청년 생활 지원', stage: '신청 완료', progress: 100, status: '완료', icon: 'wallet' }
  ],
  schedules: createMockSchedules(initialDate),
  updatedAt: formatMockTimestamp(initialDate)
};

const calendarState = {
  displayedDate: new Date(initialDate.getFullYear(), initialDate.getMonth(), 1),
  selectedDate: getDateKeyWithOffset(initialDate, 0)
};

function formatCurrency(amount) {
  return `${Number(amount).toLocaleString('ko-KR')}원`;
}

function getLoginRequiredMarkup(message, actionLabel) {
  return `<div class="login-required"><p>${message}</p><a class="btn btn-secondary" href="login.html" data-login-required>${actionLabel}</a></div>`;
}

function renderSupportSummary(data, isLoggedIn) {
  const target = document.querySelector('#support-summary');
  if (!target || !data) return;

  if (!isLoggedIn) {
    target.innerHTML = getLoginRequiredMarkup('로그인하면 나에게 맞는 지원금을 확인할 수 있어요.', '로그인하고 지원금 확인하기');
    return;
  }

  target.innerHTML = `
    <div class="support-item"><p class="support-item__label">예상 지원금</p><p class="support-item__amount">${formatCurrency(data.expectedAmount)}</p></div>
    <div class="support-item"><p class="support-item__label">최대 지원금</p><p class="support-item__amount">${formatCurrency(data.maximumAmount)}</p></div>
    <p class="support-item__note">${data.note}</p>
  `;
}

function renderBenefitSummary(data) {
  const target = document.querySelector('#benefit-summary');
  if (!target || !data) return;

  target.innerHTML = `
    <div class="benefit-tabs">${data.categories.map((category, index) => `<button class="benefit-tab${index === 0 ? ' is-current' : ''}" type="button" data-benefit-category="${index}">${category.label}</button>`).join('')}</div>
    <button class="benefit-highlight" type="button" data-unavailable-action><span>신규 해당 혜택 ${data.newBenefits}건</span><b aria-hidden="true">›</b></button>
    <p class="benefit-description">${data.description}</p>
  `;
}

function renderWeeklySummary(data) {
  const target = document.querySelector('#weekly-summary');
  const period = document.querySelector('#weekly-period');
  if (!target || !data) return;

  if (period) period.textContent = data.period;
  target.innerHTML = data.items.map((item) => `
    <button class="weekly-item weekly-item--${item.tone}" type="button" data-unavailable-action><span>${item.label}</span><span class="weekly-item__count">${item.count}건</span></button>
  `).join('');
}

function getApplicationIcon(iconName) {
  const icons = {
    house: '<svg class="application-item__icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="m3 11 9-7 9 7v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"></path><path d="M9 21v-6h6v6"></path></svg>',
    wallet: '<svg class="application-item__icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M20 7V6a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h15v8a2 2 0 0 1-2 2H5a3 3 0 0 1-3-3V7"></path><path d="M16 14h.01"></path></svg>'
  };
  return icons[iconName] || icons.wallet;
}

function renderApplications(applications, isLoggedIn) {
  const target = document.querySelector('#application-list');
  if (!target) return;

  if (!isLoggedIn) {
    target.innerHTML = getLoginRequiredMarkup('로그인하면 신청 진행 상황과 마감일을 확인할 수 있어요.', '로그인하고 신청 현황 확인하기');
    return;
  }

  target.innerHTML = applications.map((application) => `
    <button class="application-item" type="button" data-unavailable-action aria-label="${application.name} 신청 현황 보기">
      ${getApplicationIcon(application.icon)}
      <span><span class="application-item__name">${application.name}</span><span class="application-item__stage">${application.stage}</span></span>
      <span class="application-item__status">${application.status}</span>
      <span class="application-item__progress"><span class="progress-track"><span style="width: ${application.progress}%"></span></span><small>${application.progress}%</small></span>
    </button>
  `).join('');
}

function getSchedulesForDate(dateKey) {
  return mockHomeData.schedules.filter((schedule) => schedule.date === dateKey);
}

function renderSelectedSchedules() {
  const title = document.querySelector('#selected-date-title');
  const target = document.querySelector('#schedule-list');
  if (!title || !target) return;

  const selected = new Date(`${calendarState.selectedDate}T00:00:00`);
  title.textContent = `${selected.getMonth() + 1}월 ${selected.getDate()}일 일정`;
  const schedules = getSchedulesForDate(calendarState.selectedDate);

  target.innerHTML = schedules.length
    ? schedules.map((schedule) => `<button class="schedule-item" type="button" data-unavailable-action><span class="schedule-item__name"><i class="schedule-dot schedule-dot--${schedule.tone}"></i>${schedule.name}</span><span class="schedule-item__type">${schedule.type}</span></button>`).join('')
    : '<p class="schedule-empty">선택한 날짜에는 등록된 일정이 없어요.</p>';
}

function getEventTone(dateKey) {
  const schedules = getSchedulesForDate(dateKey);
  if (schedules.some((schedule) => schedule.tone === 'deadline')) return 'deadline';
  if (schedules.some((schedule) => schedule.tone === 'result')) return 'result';
  return schedules.length ? 'start' : '';
}

function renderCalendar() {
  const daysTarget = document.querySelector('#calendar-days');
  const monthTitle = document.querySelector('#calendar-month');
  if (!daysTarget || !monthTitle) return;

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
}

function changeCalendarMonth(direction) {
  calendarState.displayedDate = new Date(
    calendarState.displayedDate.getFullYear(),
    calendarState.displayedDate.getMonth() + direction,
    1
  );
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

function applySavedSettings() {
  const savedTheme = localStorage.getItem('bokdeongi-theme') || 'light';
  const savedScale = Number(localStorage.getItem('bokdeongi-font-scale')) || 1;
  document.documentElement.dataset.theme = savedTheme;
  document.documentElement.style.setProperty('--font-scale', savedScale);
  updateThemeButton(savedTheme);
}

function updateThemeButton(theme) {
  const button = document.querySelector('[data-theme-toggle]');
  if (!button) return;
  const isDark = theme === 'dark';
  button.textContent = isDark ? '라이트 모드' : '다크 모드';
  button.setAttribute('aria-pressed', String(isDark));
}

function setFontScale(change) {
  const currentScale = Number(localStorage.getItem('bokdeongi-font-scale')) || 1;
  const nextScale = Math.min(1.2, Math.max(.9, Number((currentScale + change).toFixed(2))));
  document.documentElement.style.setProperty('--font-scale', nextScale);
  localStorage.setItem('bokdeongi-font-scale', nextScale);
  showToast(`글자 크기를 ${Math.round(nextScale * 100)}%로 설정했어요.`);
}

function bindEvents() {
  document.addEventListener('click', (event) => {
    const loginRequired = event.target.closest('[data-login-required]');
    if (loginRequired) {
      event.preventDefault();
      showToast('이 기능은 로그인 후 이용할 수 있어요. 로그인 기능은 현재 준비 중입니다.');
      return;
    }

    const unavailableLink = event.target.closest('[data-unavailable-page]');
    if (unavailableLink) {
      event.preventDefault();
      showToast('이 페이지는 현재 준비 중입니다.');
      return;
    }

    const unavailableAction = event.target.closest('[data-unavailable-action]');
    if (unavailableAction) {
      showToast('상세 기능은 해당 페이지가 준비되면 이용할 수 있습니다.');
      return;
    }

    const calendarDate = event.target.closest('[data-calendar-date]');
    if (calendarDate) {
      calendarState.selectedDate = calendarDate.dataset.calendarDate;
      renderCalendar();
      return;
    }

    const calendarNavigation = event.target.closest('[data-calendar-nav]');
    if (calendarNavigation) {
      changeCalendarMonth(calendarNavigation.dataset.calendarNav === 'next' ? 1 : -1);
      return;
    }

    const category = event.target.closest('[data-benefit-category]');
    if (category) {
      document.querySelectorAll('.benefit-tab').forEach((tab) => tab.classList.toggle('is-current', tab === category));
      showToast('혜택 목록 기능은 준비 중입니다.');
      return;
    }

    const fontButton = event.target.closest('[data-font-size]');
    if (fontButton) {
      setFontScale(fontButton.dataset.fontSize === 'increase' ? .1 : -.1);
      return;
    }

    const themeButton = event.target.closest('[data-theme-toggle]');
    if (themeButton) {
      const nextTheme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = nextTheme;
      localStorage.setItem('bokdeongi-theme', nextTheme);
      updateThemeButton(nextTheme);
      return;
    }

    const menuButton = event.target.closest('.menu-toggle');
    if (menuButton) {
      const navigation = document.querySelector('#site-navigation');
      const willOpen = !navigation.classList.contains('is-open');
      navigation.classList.toggle('is-open', willOpen);
      menuButton.setAttribute('aria-expanded', String(willOpen));
      menuButton.setAttribute('aria-label', willOpen ? '메뉴 닫기' : '메뉴 열기');
    }
  });
}

function initializeHome() {
  applySavedSettings();
  renderSupportSummary(mockHomeData.supportSummary, mockSession.isLoggedIn);
  renderBenefitSummary(mockHomeData.benefitSummary);
  renderWeeklySummary(mockHomeData.weeklySummary);
  renderApplications(mockHomeData.applications, mockSession.isLoggedIn);
  renderCalendar();
  const timestamp = document.querySelector('#data-updated-at');
  if (timestamp) timestamp.textContent = mockHomeData.updatedAt;
  bindEvents();
}

document.addEventListener('DOMContentLoaded', initializeHome);
