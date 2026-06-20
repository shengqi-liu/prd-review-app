/**
 * 评审风格管理模块
 * - 列表加载 / 渲染
 * - 新建 / 编辑 / 启停 / 删除 / 设为默认
 * - 规则列表动态增删行
 *
 * 依赖 auth.js 提供 authFetch / getCurrentUser。
 */

const REVIEW_STYLE_API = '/api/v1/review-styles';
const STYLE_MIN_RULES = 4;
const STYLE_MAX_RULES = 8;

/** 当前页所有风格 */
let currentStyles = [];

/** 当前编辑中的风格（null 表示新建模式） */
let editingStyle = null;

function styleIsAdmin() {
  const u = getCurrentUser();
  return u && u.role === 'ADMIN';
}

function styleEscape(s) {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

async function loadReviewStyles() {
  const container = document.querySelector('#page-style-manage .review-style-list');
  if (!container) return;
  container.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">加载中…</div>';
  try {
    const data = await authFetch(`${REVIEW_STYLE_API}?page=1&size=100`);
    currentStyles = data.items || [];
    renderReviewStyles();
  } catch (e) {
    container.innerHTML = `<div style="padding:24px;color:var(--danger);text-align:center">加载失败：${styleEscape(e.message)}</div>`;
  }
}

function renderReviewStyles() {
  const container = document.querySelector('#page-style-manage .review-style-list');
  if (!container) return;
  if (!currentStyles.length) {
    container.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">暂无评审风格</div>';
    return;
  }
  const adminMode = styleIsAdmin();
  container.innerHTML = currentStyles.map(s => {
    const enabledBadge = s.enabled
      ? '<span class="badge badge-active">启用</span>'
      : '<span class="badge badge-inactive">已停用</span>';
    const defaultBadge = s.isDefault
      ? '<span style="font-size:10px;background:var(--success);color:#fff;padding:1px 7px;border-radius:6px">默认</span>'
      : '';
    const editBtn = adminMode
      ? `<button class="btn btn-ghost btn-sm" onclick="openEditStyleModal(${s.id})">✏️ 编辑</button>`
      : '';
    const setDefaultBtn = adminMode
      ? (s.isDefault
          ? `<button class="btn btn-ghost btn-sm" disabled style="opacity:.6">⭐ 默认</button>`
          : `<button class="btn btn-ghost btn-sm" onclick="setDefaultStyle(${s.id})">⭐ 设为默认</button>`)
      : '';
    const toggleBtn = adminMode
      ? (s.isDefault
          ? `<span style="font-size:12px;color:var(--text-4);padding:5px 10px;align-self:center">默认不可停用</span>`
          : (s.enabled
              ? `<button class="btn btn-danger-outline btn-sm" onclick="toggleStyle(${s.id})">停用</button>`
              : `<button class="btn btn-primary btn-sm" onclick="toggleStyle(${s.id})">启用</button>`))
      : '';
    const deleteBtn = adminMode && !s.isDefault
      ? `<button class="btn btn-danger-outline btn-sm" onclick="deleteStyle(${s.id})">🗑️ 删除</button>`
      : '';
    const cardStyle = s.isDefault ? 'style="border-color:var(--primary)"' : (s.enabled ? '' : 'style="opacity:.65"');
    const rulesGrid = (s.rules || []).map(r => `
      <div class="style-rule-cell">
        <div class="style-rule-lbl">${styleEscape(r.label)}</div>
        <div class="style-rule-val">${styleEscape(r.content)}</div>
      </div>
    `).join('');
    return `
      <div class="style-manage-card" ${cardStyle}>
        <div class="style-manage-hdr">
          <div class="style-manage-name">${styleEscape(s.icon || '')} ${styleEscape(s.name)} ${enabledBadge} ${defaultBadge}</div>
          <div style="display:flex;gap:8px;flex-wrap:wrap">${editBtn}${setDefaultBtn}${toggleBtn}${deleteBtn}</div>
        </div>
        <div style="font-size:12px;color:var(--text-3)">适用：${styleEscape(s.scenario || '')}</div>
        <div class="style-rules-grid">${rulesGrid}</div>
      </div>
    `;
  }).join('');
}

function openCreateStyleModal() {
  if (!styleIsAdmin()) { alert('仅管理员可创建评审风格'); return; }
  editingStyle = null;
  document.getElementById('style-modal-title').textContent = '新建评审风格';
  document.getElementById('st-name').value = '';
  document.getElementById('st-icon').value = '🎯';
  document.getElementById('st-scenario').value = '';
  document.getElementById('st-sort').value = 0;
  document.getElementById('st-enabled').checked = true; // 新建默认启用
  renderRuleRows([
    { label: '', content: '' },
    { label: '', content: '' },
    { label: '', content: '' },
    { label: '', content: '' },
  ]);
  showStyleModal();
}

function openEditStyleModal(styleId) {
  const s = currentStyles.find(x => x.id === styleId);
  if (!s) return;
  editingStyle = s;
  document.getElementById('style-modal-title').textContent = '编辑评审风格';
  document.getElementById('st-name').value = s.name || '';
  document.getElementById('st-icon').value = s.icon || '';
  document.getElementById('st-scenario').value = s.scenario || '';
  document.getElementById('st-sort').value = s.sortOrder ?? 0;
  document.getElementById('st-enabled').checked = !!s.enabled; // 保留原启用状态，启停由卡片按钮控制
  renderRuleRows((s.rules || []).map(r => ({ label: r.label, content: r.content })));
  showStyleModal();
}

function showStyleModal() {
  document.getElementById('style-modal').classList.add('open');
}

function closeStyleModal() {
  document.getElementById('style-modal').classList.remove('open');
}

function renderRuleRows(rules) {
  const wrap = document.getElementById('st-rules-wrap');
  wrap.innerHTML = '';
  rules.forEach(r => addRuleRow(r.label, r.content));
}

function addRuleRow(label = '', content = '') {
  const wrap = document.getElementById('st-rules-wrap');
  const count = wrap.querySelectorAll('.st-rule-row').length;
  if (count >= STYLE_MAX_RULES) { alert(`规则最多 ${STYLE_MAX_RULES} 条`); return; }
  const row = document.createElement('div');
  row.className = 'st-rule-row';
  row.style.cssText = 'display:flex;gap:8px;margin-bottom:8px;align-items:flex-start';
  row.innerHTML = `
    <input type="text" class="form-input st-rule-label" placeholder="标签（如：问题级别）" maxlength="50" value="${styleEscape(label)}" style="flex:0 0 160px">
    <input type="text" class="form-input st-rule-content" placeholder="规则描述" maxlength="500" value="${styleEscape(content)}" style="flex:1">
    <button type="button" class="btn btn-ghost btn-sm" onclick="removeRuleRow(this)" style="flex-shrink:0">×</button>
  `;
  wrap.appendChild(row);
}

function removeRuleRow(btn) {
  const wrap = document.getElementById('st-rules-wrap');
  const count = wrap.querySelectorAll('.st-rule-row').length;
  if (count <= STYLE_MIN_RULES) { alert(`规则至少 ${STYLE_MIN_RULES} 条`); return; }
  btn.closest('.st-rule-row').remove();
}

function collectRules() {
  const rows = document.querySelectorAll('#st-rules-wrap .st-rule-row');
  const rules = [];
  rows.forEach(row => {
    const label = row.querySelector('.st-rule-label').value.trim();
    const content = row.querySelector('.st-rule-content').value.trim();
    rules.push({ label, content });
  });
  return rules;
}

async function submitStyleModal() {
  const name = document.getElementById('st-name').value.trim();
  const icon = document.getElementById('st-icon').value.trim();
  const scenario = document.getElementById('st-scenario').value.trim();
  const sortOrder = parseInt(document.getElementById('st-sort').value || '0', 10);
  const enabled = document.getElementById('st-enabled').checked;
  const rules = collectRules();

  if (!name) { alert('请填写名称'); return; }
  if (rules.length < STYLE_MIN_RULES || rules.length > STYLE_MAX_RULES) {
    alert(`规则数量必须在 ${STYLE_MIN_RULES}–${STYLE_MAX_RULES} 条之间`); return;
  }
  for (const r of rules) {
    if (!r.label || !r.content) { alert('每条规则的标签和内容都不能为空'); return; }
  }

  try {
    if (editingStyle) {
      await authFetch(`${REVIEW_STYLE_API}/${editingStyle.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          name, icon, scenario, rules,
          enabled, sortOrder, version: editingStyle.version,
        }),
      });
    } else {
      await authFetch(REVIEW_STYLE_API, {
        method: 'POST',
        body: JSON.stringify({ name, icon, scenario, rules, sortOrder }),
      });
    }
    closeStyleModal();
    await loadReviewStyles();
  } catch (e) {
    alert('保存失败：' + e.message);
  }
}

async function toggleStyle(styleId) {
  const s = currentStyles.find(x => x.id === styleId);
  if (!s) return;
  if (s.isDefault) { alert('默认风格不可停用，请先切换默认风格'); return; }
  const action = s.enabled ? '停用' : '启用';
  if (!confirm(`确定${action}「${s.name}」？`)) return;
  try {
    await authFetch(`${REVIEW_STYLE_API}/${s.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: s.name,
        icon: s.icon,
        scenario: s.scenario,
        rules: (s.rules || []).map(r => ({ label: r.label, content: r.content })),
        enabled: !s.enabled,
        sortOrder: s.sortOrder,
        version: s.version,
      }),
    });
    await loadReviewStyles();
  } catch (e) {
    alert(`${action}失败：` + e.message);
  }
}

async function deleteStyle(styleId) {
  const s = currentStyles.find(x => x.id === styleId);
  if (!s) return;
  if (s.isDefault) { alert('默认风格不可删除，请先切换默认风格'); return; }
  if (!confirm(`确定删除「${s.name}」？删除后将无法在评审中使用。`)) return;
  try {
    await authFetch(`${REVIEW_STYLE_API}/${s.id}`, { method: 'DELETE' });
    await loadReviewStyles();
  } catch (e) {
    alert('删除失败：' + e.message);
  }
}

async function setDefaultStyle(styleId) {
  const s = currentStyles.find(x => x.id === styleId);
  if (!s) return;
  if (!confirm(`将「${s.name}」设为默认风格？`)) return;
  try {
    await authFetch(`${REVIEW_STYLE_API}/${s.id}/set-default`, { method: 'POST' });
    await loadReviewStyles();
  } catch (e) {
    alert('设为默认失败：' + e.message);
  }
}

// 暴露给 HTML inline handler
window.loadReviewStyles = loadReviewStyles;
window.openCreateStyleModal = openCreateStyleModal;
window.openEditStyleModal = openEditStyleModal;
window.closeStyleModal = closeStyleModal;
window.submitStyleModal = submitStyleModal;
window.toggleStyle = toggleStyle;
window.deleteStyle = deleteStyle;
window.setDefaultStyle = setDefaultStyle;
window.addRuleRow = addRuleRow;
window.removeRuleRow = removeRuleRow;
