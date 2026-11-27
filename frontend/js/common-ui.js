/* Shared header controls. Page scripts must not bind these controls again. */
(function () {
    const FONT_SCALE_KEY = 'bokdeongi-font-scale';
    const THEME_KEY = 'bokdeongi-theme';
    const MIN_FONT_SCALE = 0.8;
    const MAX_FONT_SCALE = 1.3;
    const FONT_STEP = 0.1;

    function getFontScale() {
        const savedValue = localStorage.getItem(FONT_SCALE_KEY);
        if (savedValue === null) return 1;
        const saved = Number(savedValue);
        const scale = savedValue !== '' && Number.isFinite(saved)
            ? Math.min(MAX_FONT_SCALE, Math.max(MIN_FONT_SCALE, saved)) : 1;
        if (scale !== saved) localStorage.setItem(FONT_SCALE_KEY, scale);
        return scale;
    }

    function applyFontScale(scale) {
        document.documentElement.style.setProperty('--font-scale', scale);
        document.querySelectorAll('[data-font-size="decrease"]').forEach((button) => { button.disabled = scale <= MIN_FONT_SCALE; });
        document.querySelectorAll('[data-font-size="increase"]').forEach((button) => { button.disabled = scale >= MAX_FONT_SCALE; });
        updateHeaderLayout();
    }

    function updateHeaderLayout() {
        document.querySelectorAll('.site-header').forEach((header) => {
            const inner = header.querySelector('.site-header__inner');
            const brand = inner?.querySelector('.site-brand');
            const nav = inner?.querySelector('.site-nav');
            const tools = inner?.querySelector('.header-tools');
            if (!brand || !nav || !tools) return;

            header.classList.remove('is-nav-collapsed', 'is-nav-compact');
            const links = [...nav.querySelectorAll('.site-nav__link:not([hidden])')];
            header.classList.toggle('has-account-nav', links.length > 5);
            const requiredWidth = () => {
                const navGap = parseFloat(getComputedStyle(nav).columnGap) || 0;
                const headerGap = parseFloat(getComputedStyle(inner).columnGap) || 0;
                const linkWidth = links.reduce((width, link) => width + link.getBoundingClientRect().width, 0);
                return brand.getBoundingClientRect().width + linkWidth
                    + Math.max(0, links.length - 1) * navGap + tools.getBoundingClientRect().width + 2 * headerGap;
            };
            if (requiredWidth() > inner.clientWidth) header.classList.add('is-nav-compact');
            const collapsed = requiredWidth() > inner.clientWidth;
            header.classList.toggle('is-nav-collapsed', collapsed);
            if (!collapsed) {
                nav.classList.remove('is-open');
                const button = inner.querySelector('.menu-toggle');
                button?.setAttribute('aria-expanded', 'false');
                button?.setAttribute('aria-label', '메뉴 열기');
            }
        });
    }

    function getTheme() {
        const saved = localStorage.getItem(THEME_KEY);
        if (saved === 'light' || saved === 'dark') return saved;
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    function themeIcon(theme) {
        return theme === 'dark'
            ? '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4"></circle><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"></path></svg>'
            : '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M20.5 14.1A8.5 8.5 0 0 1 9.9 3.5 8.5 8.5 0 1 0 20.5 14.1Z"></path></svg>';
    }

    function configureFontButtons() {
        document.querySelectorAll('[data-font-size]').forEach((button) => {
            const isIncrease = button.dataset.fontSize === 'increase';
            const label = isIncrease ? '글자 크기 키우기' : '글자 크기 줄이기';
            button.textContent = isIncrease ? '+' : '-';
            button.setAttribute('aria-label', label);
            button.title = label;
        });
    }

    function applyTheme(theme) {
        document.documentElement.dataset.theme = theme;
        document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
            const isDark = theme === 'dark';
            const label = isDark ? '라이트 모드로 전환' : '다크 모드로 전환';
            button.innerHTML = themeIcon(theme);
            button.setAttribute('aria-label', label);
            button.title = label;
            button.setAttribute('aria-pressed', String(isDark));
        });
    }

    function showFontScaleFeedback(scale) {
        const toast = document.querySelector('#toast');
        if (!toast) return;
        toast.textContent = `글자 크기를 ${Math.round(scale * 100)}%로 설정했어요.`;
        toast.hidden = false;
        window.clearTimeout(showFontScaleFeedback.timer);
        showFontScaleFeedback.timer = window.setTimeout(() => { toast.hidden = true; }, 3000);
    }

    function pageNumbers(totalPages, currentPage) {
        if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index + 1);
        const middle = [currentPage - 1, currentPage, currentPage + 1].filter((page) => page > 1 && page < totalPages);
        return [1, ...(middle[0] > 2 ? ['…'] : []), ...middle, ...(middle[middle.length - 1] < totalPages - 1 ? ['…'] : []), totalPages];
    }

    function paginationMarkup({ totalCount, currentPage, pageSize = 5, label = '목록' }) {
        const totalPages = Math.ceil(totalCount / pageSize);
        if (totalPages <= 1) return '';
        const button = (page, text, disabled, current) => '<button class="pagination__button' + (current ? ' is-current' : '') + '" type="button" data-pagination-page="' + page + '"' + (current ? ' aria-current="page"' : '') + (disabled ? ' disabled' : '') + '>' + text + '</button>';
        const numbers = pageNumbers(totalPages, currentPage).map((page) => page === '…'
            ? '<span class="pagination__ellipsis" aria-hidden="true">…</span>'
            : button(page, page, false, page === currentPage)).join('');
        return '<nav class="pagination" aria-label="' + label + ' 페이지 이동">' + button(currentPage - 1, '이전', currentPage === 1, false) + '<span class="pagination__pages">' + numbers + '</span>' + button(currentPage + 1, '다음', currentPage === totalPages, false) + '</nav>';
    }

    window.BokdeongiPagination = {
        clampPage(totalCount, currentPage, pageSize = 5) {
            return Math.min(Math.max(1, currentPage), Math.max(1, Math.ceil(totalCount / pageSize)));
        },
        markup: paginationMarkup
    };

    configureFontButtons();
    applyFontScale(getFontScale());
    applyTheme(getTheme());
    window.addEventListener('resize', updateHeaderLayout);
    document.fonts?.ready.then(updateHeaderLayout);
    document.querySelectorAll('.site-header').forEach((header) => {
        const nav = header.querySelector('.site-nav');
        const auth = header.querySelector('.auth-links');
        const observer = new MutationObserver(updateHeaderLayout);
        if (nav) observer.observe(nav, { subtree: true, childList: true, attributes: true, attributeFilter: ['hidden'] });
        if (auth) observer.observe(auth, { subtree: true, childList: true, characterData: true });
    });

    document.addEventListener('click', (event) => {
        const fontButton = event.target.closest('[data-font-size]');
        if (fontButton) {
            if (fontButton.disabled) { event.stopImmediatePropagation(); return; }
            const current = getFontScale();
            const nextScale = fontButton.dataset.fontSize === 'increase'
                ? Math.min(MAX_FONT_SCALE, Number((Math.floor(current / FONT_STEP + 0.000001) * FONT_STEP + FONT_STEP).toFixed(1)))
                : Math.max(MIN_FONT_SCALE, Number((Math.ceil(current / FONT_STEP - 0.000001) * FONT_STEP - FONT_STEP).toFixed(1)));
            localStorage.setItem(FONT_SCALE_KEY, nextScale);
            applyFontScale(nextScale);
            showFontScaleFeedback(nextScale);
            event.stopImmediatePropagation();
            return;
        }

        if (event.target.closest('[data-theme-toggle]')) {
            const nextTheme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            localStorage.setItem(THEME_KEY, nextTheme);
            applyTheme(nextTheme);
            event.stopImmediatePropagation();
            return;
        }

        const paginationButton = event.target.closest('[data-pagination-page]');
        if (paginationButton && !paginationButton.disabled) {
            event.preventDefault();
            document.dispatchEvent(new CustomEvent('bokdeongi:pagination', {
                detail: { page: Number(paginationButton.dataset.paginationPage) }
            }));
            event.stopImmediatePropagation();
            return;
        }

        const menuButton = event.target.closest('.menu-toggle');
        if (!menuButton) return;
        const navigation = document.getElementById(menuButton.getAttribute('aria-controls') || 'site-navigation');
        if (!navigation) return;
        const isOpen = navigation.classList.toggle('is-open');
        menuButton.setAttribute('aria-expanded', String(isOpen));
        menuButton.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기');
        event.stopImmediatePropagation();
    });
})();
