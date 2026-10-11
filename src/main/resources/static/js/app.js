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

  window.BillGen = Object.assign(window.BillGen || {}, { toggleNav });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') toggleNav(false);
  });

  document.addEventListener('DOMContentLoaded', () => {
    const list = document.getElementById('invoice-lines');
    const addButton = document.getElementById('add-line');
    const currencySelect = document.getElementById('currency');
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
        const amount = quantity * price * (1 + tax / 100);
        grandTotal += amount;
        const lineTotal = row.querySelector('.line-total');
        if (lineTotal) lineTotal.textContent = money(amount);
      });
      const estimate = document.getElementById('invoice-estimate');
      if (estimate) estimate.textContent = money(grandTotal);
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

    list.addEventListener('input', (event) => {
      if (event.target.matches('.line-quantity, .line-price, .line-tax')) calculate();
    });
    list.addEventListener('change', (event) => {
      if (event.target.matches('.product-picker')) {
        const row = event.target.closest('[data-invoice-line]');
        const selected = event.target.selectedOptions[0];
        if (selected?.dataset.name) {
          row.querySelector('.line-description').value = selected.dataset.name;
          row.querySelector('.line-price').value = selected.dataset.price || '0.00';
          row.querySelector('.line-tax').value = selected.dataset.tax || '0.00';
          calculate();
        }
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
