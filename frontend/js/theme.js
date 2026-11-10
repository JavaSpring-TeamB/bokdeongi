// 라이트/다크 전환 (COMM-01). 기기 설정을 기본으로 따르고, 수동 선택은 브라우저에 저장한다.
(function () {
  const KEY = 'bd-theme';
  const root = document.documentElement;

  function saved() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function save(value) {
    try { localStorage.setItem(KEY, value); } catch (e) { /* 저장 불가 시 기기 설정만 따름 */ }
  }
  function current() {
    const t = root.getAttribute('data-theme');
    if (t) return t;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  const initial = saved();
  if (initial) root.setAttribute('data-theme', initial);

  const button = document.getElementById('theme-toggle');
  if (button) {
    button.addEventListener('click', function () {
      const next = current() === 'dark' ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      save(next);
    });
  }
})();
