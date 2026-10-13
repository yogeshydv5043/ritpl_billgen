/* BillGen progressive enhancement: mobile navigation and invoice line helpers. */
(() => {
  'use strict';

  const toggleNav = (force) => {
    const open = typeof force === 'boolean'
      ? force
      : !document.body.classList.contains('nav-open');
    document.body.classList.toggle('nav-open', open);
    const trigger = document.querySelector('.mobile-menu');
    if (trigger) trigger.setAttribute('aria-expanded', String(open));
  };

  // ------------------------------------------------------------- shell chrome
  const THEME_KEY = 'billgen-theme';
  const NAV_KEY = 'billgen-nav-collapsed';

  const applyTheme = (theme) => {
    document.documentElement.setAttribute('data-theme', theme);
    try { window.localStorage.setItem(THEME_KEY, theme); } catch (_) { /* private mode */ }
    document.querySelectorAll('.theme-toggle').forEach((button) => {
      button.setAttribute('aria-pressed', String(theme === 'dark'));
      button.title = theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme';
    });
  };

  const toggleTheme = () => {
    const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    applyTheme(current === 'dark' ? 'light' : 'dark');
  };

  const setSidebarCollapsed = (collapsed) => {
    document.body.classList.toggle('nav-collapsed', collapsed);
    try { window.localStorage.setItem(NAV_KEY, collapsed ? '1' : '0'); } catch (_) { /* ignore */ }
    document.querySelectorAll('.nav-toggle').forEach((button) => {
      button.setAttribute('aria-expanded', String(!collapsed));
      button.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation');
    });
  };

  const toggleSidebar = () => setSidebarCollapsed(!document.body.classList.contains('nav-collapsed'));

  /** Dropdown menus in the top bar: click to open, Escape or outside click to close. */
  const closeMenus = (except) => {
    document.querySelectorAll('.shell-menu').forEach((menu) => {
      const pop = menu.querySelector('.menu-pop');
      const trigger = menu.querySelector('[aria-haspopup]');
      if (!pop) return;
      if (menu === except) return;
      pop.dataset.open = 'false';
      pop.hidden = true;
      if (trigger) trigger.setAttribute('aria-expanded', 'false');
    });
  };

  const toggleMenu = (trigger) => {
    const menu = trigger.closest('.shell-menu');
    const pop = menu && menu.querySelector('.menu-pop');
    if (!pop) return;
    const open = pop.dataset.open !== 'true';
    closeMenus(open ? menu : null);
    pop.dataset.open = open ? 'true' : 'false';
    pop.hidden = !open;
    trigger.setAttribute('aria-expanded', String(open));
    if (open) {
      const first = pop.querySelector('a, button');
      if (first) first.focus();
    }
  };

  // ------------------------------------------------------- confirm dialogs
  let pendingConfirm = null;

  const openConfirm = (form) => {
    const dialog = document.getElementById('billgen-confirm');
    if (!dialog) { form.submit(); return; }
    const title = document.getElementById('confirm-title');
    const text = document.getElementById('confirm-text');
    const checkRow = document.getElementById('confirm-check-row');
    const check = document.getElementById('confirm-check');
    const checkLabel = document.getElementById('confirm-check-label');
    const go = document.getElementById('confirm-go');
    if (title) title.textContent = form.dataset.confirmTitle || 'Please confirm';
    if (text) text.textContent = form.dataset.confirmText || 'This action cannot be undone by an ordinary user.';
    if (checkRow && check) {
      const wantsCheck = form.dataset.confirmCheck;
      checkRow.hidden = !wantsCheck;
      check.checked = false;
      if (wantsCheck) {
        checkLabel.textContent = wantsCheck;
        check.name = wantsCheck;
        check.value = 'true';
        go.disabled = true;
      } else {
        go.disabled = false;
      }
    }
    if (check) {
      check.onchange = () => { go.disabled = !check.checked; };
    }
    pendingConfirm = form;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', 'open');
    if (go) go.focus();
  };

  const closeConfirm = () => {
    const dialog = document.getElementById('billgen-confirm');
    pendingConfirm = null;
    if (!dialog) return;
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  };

  /** Re-submits the stored form, bypassing this listener, and carries the confirmation field. */
  const confirmSubmit = () => {
    const form = pendingConfirm;
    if (!form) return;
    const check = document.getElementById('confirm-check');
    if (form.dataset.confirmCheck && check && check.name) {
      const carrier = document.createElement('input');
      carrier.type = 'hidden';
      carrier.name = form.dataset.confirmField || 'confirmed';
      carrier.value = 'true';
      form.appendChild(carrier);
    }
    form.dataset.confirmed = 'true';
    pendingConfirm = null;
    form.submit();
  };

  // ------------------------------------------------------------------ toasts
  const toast = (message, tone) => {
    const host = document.getElementById('billgen-toasts');
    if (!host) return;
    const node = document.createElement('div');
    node.className = 'toast ' + (tone === 'bad' ? 'is-bad' : 'is-ok');
    node.setAttribute('role', tone === 'bad' ? 'alert' : 'status');
    const strong = document.createElement('strong');
    strong.textContent = tone === 'bad' ? 'Could not complete that' : 'Done';
    const body = document.createElement('p');
    body.textContent = message;
    const dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.setAttribute('aria-label', 'Dismiss notification');
    dismiss.textContent = '×';
    dismiss.addEventListener('click', () => node.remove());
    node.append(strong, body, dismiss);
    host.appendChild(node);
    window.setTimeout(() => node.remove(), 6000);
  };

  window.BillGen = Object.assign(window.BillGen || {}, {
    toggleNav, toggleTheme, toggleSidebar, setSidebarCollapsed, toggleMenu, closeMenus,
    openConfirm, closeConfirm, confirmSubmit, toast,
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') { toggleNav(false); closeMenus(null); }
  });

  document.addEventListener('click', (event) => {
    if (!event.target.closest('.shell-menu')) closeMenus(null);
  });

  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (form.dataset.confirmed === 'true') return;
    if (form.dataset.confirmTitle || form.dataset.confirmText || form.dataset.confirmCheck) {
      event.preventDefault();
      openConfirm(form);
      return;
    }
    const submit = form.querySelector('[type=submit]');
    if (submit && !form.dataset.busy) {
      form.dataset.busy = '1';
      window.setTimeout(() => {
        submit.disabled = true;
        submit.dataset.label = submit.textContent;
        submit.textContent = 'Working…';
        document.body.classList.add('is-busy');
      }, 260);
    }
  });

  // Client-side mirror of the server rules: it only stops an obviously invalid
  // submit early, the server always re-validates everything.
  document.addEventListener('input', (event) => {
    const field = event.target;
    if (!field.checkValidity || field.dataset.touched === '1') return;
    field.dataset.touched = '1';
    const wrap = field.closest('.field');
    if (!wrap) return;
    const note = wrap.querySelector('.field-error');
    if (field.checkValidity()) {
      field.removeAttribute('aria-invalid');
      if (note && note.dataset.server !== 'true') note.remove();
    }
  });

  document.addEventListener('DOMContentLoaded', () => {
    applyTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');
    let collapsed = false;
    try { collapsed = window.localStorage.getItem(NAV_KEY) === '1'; } catch (_) { /* ignore */ }
    if (collapsed && window.matchMedia('(min-width: 1021px)').matches) setSidebarCollapsed(true);

    // Tabs that swap panels without a round trip (data-tab groups).
    document.querySelectorAll('[data-tabs]').forEach((group) => {
      const buttons = Array.from(group.querySelectorAll('[data-tab-button]'));
      const show = (name) => {
        buttons.forEach((button) => {
          const active = button.dataset.tabButton === name;
          button.classList.toggle('is-active', active);
          button.setAttribute('aria-selected', String(active));
        });
        group.querySelectorAll('[data-tab-panel]').forEach((panel) => {
          panel.hidden = panel.dataset.tabPanel !== name;
        });
      };
      buttons.forEach((button) => button.addEventListener('click', () => show(button.dataset.tabButton)));
      const initial = group.querySelector('[aria-selected=true]') || buttons[0];
      if (initial) show(initial.dataset.tabButton);
    });

    // Filter panels: show/hide without losing what is typed.
    document.querySelectorAll('[data-filter-toggle]').forEach((button) => {
      const target = document.getElementById(button.dataset.filterToggle);
      if (!target) return;
      button.addEventListener('click', () => {
        const open = target.hidden;
        target.hidden = !open;
        button.setAttribute('aria-expanded', String(open));
        if (open) {
          const first = target.querySelector('input, select');
          if (first) first.focus();
        }
      });
    });

    // Placeholder palette in the notification editor.
    document.querySelectorAll('[data-placeholder-insert]').forEach((chip) => {
      chip.addEventListener('click', () => {
        const target = document.querySelector(chip.dataset.placeholderInsert);
        if (!target) return;
        const token = chip.dataset.placeholderInsertValue || ('{{' + chip.textContent.trim() + '}}');
        const start = target.selectionStart || target.value.length;
        target.value = target.value.slice(0, start) + token + target.value.slice(target.selectionEnd || start);
        target.focus();
        target.setSelectionRange(start + token.length, start + token.length);
      });
    });

    // Checkbox groups that write a comma-separated id list back into a hidden input, so a form can
    // offer a real picker while the server keeps binding one simple string field.
    document.querySelectorAll('[data-checkgroup]').forEach((group) => {
      const target = document.querySelector(group.dataset.checkgroup);
      if (!target) return;
      const boxes = Array.from(group.querySelectorAll('input[type=checkbox]'));
      const existing = (target.value || '').split(',').map((part) => part.trim()).filter(Boolean);
      boxes.forEach((box) => { if (existing.length && box.value) box.checked = existing.includes(box.value); });
      const sync = () => {
        target.value = boxes.filter((box) => box.checked && box.value).map((box) => box.value).join(',');
        target.dispatchEvent(new Event('change', { bubbles: true }));
      };
      group.addEventListener('change', sync);
    });

    // Live preview for template configuration: the server renders the same markup, so the
    // preview cannot drift from what a customer actually receives.
    const previewFrame = document.getElementById('template-preview-frame');
    if (previewFrame && previewFrame.dataset.previewUrl) {
      const scope = previewFrame.closest('form') || document;
      let timer = null;
      const refresh = () => {
        const payload = new URLSearchParams();
        scope.querySelectorAll('[data-preview-field]').forEach((field) => {
          payload.set(field.dataset.previewField, field.value == null ? '' : field.value);
        });
        previewFrame.src = previewFrame.dataset.previewUrl + '?' + payload.toString();
      };
      scope.querySelectorAll('[data-preview-field]').forEach((field) => {
        field.addEventListener('input', () => {
          window.clearTimeout(timer);
          timer = window.setTimeout(refresh, 400);
        });
      });
      const previewButton = scope.querySelector('[data-open-preview]');
      if (previewButton) previewButton.addEventListener('click', (event) => {
        event.preventDefault();
        const dialog = document.getElementById('template-preview-dialog');
        if (!dialog) { refresh(); return; }
        refresh();
        if (typeof dialog.showModal === 'function') dialog.showModal();
        else dialog.setAttribute('open', 'open');
      });
    }

    const list = document.getElementById('invoice-lines');
    const addButton = document.getElementById('add-line');
    const currencySelect = document.getElementById('currency');
    const discountInput = document.getElementById('discountAmount');
    const standardTemplate = document.getElementById('templateId');
    const customTemplate = document.getElementById('customTemplateId');
    if (!list) return;

    const money = (amount) => {
      const currency = currencySelect?.value || 'INR';
      try {
        return new Intl.NumberFormat('en-IN', {
          style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2,
        }).format(amount);
      } catch (_) {
        return `${currency} ${amount.toFixed(2)}`;
      }
    };

    const rows = () => Array.from(list.querySelectorAll('[data-invoice-line]'));
    const renumber = () => {
      rows().forEach((row, index) => {
        row.querySelectorAll('input[name]').forEach((input) => {
          input.name = input.name.replace(/lines\[\d+\]/, `lines[${index}]`);
          input.id = input.id.replace(/lines\d+\./, `lines${index}.`);
        });
        const remove = row.querySelector('[data-remove-line]');
        if (remove) {
          remove.disabled = rows().length === 1;
          remove.setAttribute('aria-label', `Remove line ${index + 1}`);
        }
      });
    };

    const calculate = () => {
      let grandTotal = 0;
      rows().forEach((row) => {
        const quantity = Number.parseFloat(row.querySelector('.line-quantity')?.value) || 0;
        const price = Number.parseFloat(row.querySelector('.line-price')?.value) || 0;
        const tax = Number.parseFloat(row.querySelector('.line-tax')?.value) || 0;
        const subtotal = Math.round((quantity * price + Number.EPSILON) * 100) / 100;
        const taxAmount = Math.round((subtotal * tax / 100 + Number.EPSILON) * 100) / 100;
        const amount = subtotal + taxAmount;
        grandTotal += amount;
        const lineTotal = row.querySelector('.line-total');
        if (lineTotal) lineTotal.textContent = money(amount);
      });
      const discount = Number.parseFloat(document.getElementById('discountAmount')?.value) || 0;
      const estimate = document.getElementById('invoice-estimate');
      if (estimate) estimate.textContent = money(Math.max(0, grandTotal - discount));
    };

    const addLine = () => {
      const currentRows = rows();
      if (currentRows.length >= 30) return;
      const copy = currentRows[0].cloneNode(true);
      copy.querySelectorAll('input').forEach((input) => {
        if (input.classList.contains('line-quantity')) input.value = '1';
        else if (input.classList.contains('line-tax')) input.value = '18.00';
        else if (input.classList.contains('line-price')) input.value = '0.00';
        else input.value = '';
        input.classList.remove('field-error-input');
      });
      const picker = copy.querySelector('.product-picker');
      if (picker) picker.selectedIndex = 0;
      copy.querySelectorAll('.field-error').forEach((error) => error.remove());
      list.appendChild(copy);
      renumber();
      calculate();
      copy.querySelector('.line-description')?.focus();
    };

    currencySelect?.addEventListener('change', calculate);
    discountInput?.addEventListener('input', calculate);
    standardTemplate?.addEventListener('change', () => { if (standardTemplate.value && customTemplate) customTemplate.value = ''; });
    customTemplate?.addEventListener('change', () => { if (customTemplate.value && standardTemplate) standardTemplate.value = ''; });

    list.addEventListener('input', (event) => {
      if (event.target.matches('.line-quantity, .line-price, .line-tax')) calculate();
    });
    list.addEventListener('change', (event) => {
      if (event.target.matches('.product-picker')) {
        const row = event.target.closest('[data-invoice-line]');
        const selected = event.target.selectedOptions[0];
        const productId = row.querySelector('.line-product-id');
        if (productId) productId.value = event.target.value || '';
        if (selected?.dataset.name) {
          row.querySelector('.line-description').value = selected.dataset.name;
          row.querySelector('.line-price').value = selected.dataset.price || '0.00';
          row.querySelector('.line-tax').value = selected.dataset.tax || '0.00';
        }
        calculate();
      }
      if (event.target === currencySelect) calculate();
    });
    list.addEventListener('click', (event) => {
      const remove = event.target.closest('[data-remove-line]');
      if (!remove || rows().length <= 1) return;
      remove.closest('[data-invoice-line]').remove();
      renumber();
      calculate();
    });
    addButton?.addEventListener('click', addLine);

    renumber();
    calculate();
  });
})();
