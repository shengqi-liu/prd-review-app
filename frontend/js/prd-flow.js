/**
 * PRD 评审流程的 3 个原型页(选评审员 / 定评审风格 / 提交评审)接通真后端 + 选择持久化。
 *
 * 后端依赖:
 *   GET /api/v1/reviewers           列表(只显示 enabled=true 的)
 *   GET /api/v1/review-styles       列表
 *   GET /api/v1/prds/{id}           精确查 PRD title + content
 *
 * 选择持久化(localStorage):
 *   prd_agents_<prdId> = "[1,3,5]"  评审员 id 数组
 *   prd_style_<prdId>  = "2"        风格 id
 *
 * 这些选择目前仅前端记录;真正的"按所选 agent 并行评审"功能在 #17/#18 落地。
 * 现阶段"提交评审"调 backend POST /prds/{id}/submit,触发 #6 校验 + 转 SUBMITTED 状态。
 */

const REVIEWERS_API = '/api/v1/reviewers';
const STYLES_API = '/api/v1/review-styles';

function flowEscape(s) {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/**
 * 当前 PRD id —— 优先从 URL hash query 取(单一可信源,刷新/直接输入 URL 都保留),
 * 没有时 fallback 到编辑器隐藏字段。
 */
function currentPrdId() {
  // 1. URL hash 中的 ?id=N
  const m = location.hash.match(/\?(?:.*&)?id=(\d+)/);
  if (m) return parseInt(m[1], 10);
  // 2. 编辑器 hidden input
  const el = document.getElementById('prd-edit-id');
  return el && el.value ? parseInt(el.value, 10) : null;
}

function getSelectedAgentIds(prdId) {
  if (!prdId) return [];
  try {
    const raw = localStorage.getItem('prd_agents_' + prdId);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch (_) { return []; }
}

function setSelectedAgentIds(prdId, ids) {
  if (!prdId) {
    console.warn('[prd-flow] setSelectedAgentIds: prdId 为空,不写入');
    return;
  }
  try {
    localStorage.setItem('prd_agents_' + prdId, JSON.stringify(ids));
    console.log('[prd-flow] 写入评审员选择 prd_agents_' + prdId + ' =', ids);
  } catch (e) { console.error('[prd-flow] localStorage 写入失败:', e); }
}

function getSelectedStyleId(prdId) {
  if (!prdId) return null;
  try {
    const raw = localStorage.getItem('prd_style_' + prdId);
    return raw ? parseInt(raw, 10) : null;
  } catch (_) { return null; }
}

function setSelectedStyleId(prdId, id) {
  if (!prdId) {
    console.warn('[prd-flow] setSelectedStyleId: prdId 为空,不写入');
    return;
  }
  try {
    localStorage.setItem('prd_style_' + prdId, String(id));
    console.log('[prd-flow] 写入风格选择 prd_style_' + prdId + ' =', id);
  } catch (e) { console.error('[prd-flow] localStorage 写入失败:', e); }
}

// 缓存(避免每次切页都拉)
let cachedReviewers = null;
let cachedStyles = null;

// ────────────────────────────────────────
// 选评审员页
// ────────────────────────────────────────

async function loadFlowAgents() {
  const grid = document.getElementById('flow-agent-grid');
  if (!grid) return;
  grid.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">加载中…</div>';
  try {
    if (!cachedReviewers) {
      const data = await authFetch(`${REVIEWERS_API}?page=1&size=100&enabled=true`);
      cachedReviewers = (data && data.items) || [];
    }
    renderFlowAgents();
  } catch (e) {
    grid.innerHTML = `<div style="padding:24px;color:var(--danger);text-align:center">加载失败:${flowEscape(e.message)}</div>`;
  }
}

function renderFlowAgents() {
  const grid = document.getElementById('flow-agent-grid');
  if (!grid) return;
  if (!cachedReviewers || !cachedReviewers.length) {
    grid.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">尚无评审员,请管理员先在「AI 评审员」页配置</div>';
    return;
  }
  const prdId = currentPrdId();
  const selected = new Set(getSelectedAgentIds(prdId));
  grid.innerHTML = cachedReviewers.map(r => {
    const isSel = selected.has(r.id);
    const desc = r.description || '(无描述)';
    return `
      <div class="agent-card ${isSel ? 'selected' : ''}" onclick="toggleFlowAgent(${r.id})">
        <div class="agent-icon">${flowEscape(r.icon || '🤖')}</div>
        <div class="agent-name">${flowEscape(r.name)}</div>
        <div class="agent-desc">${flowEscape(desc)}</div>
      </div>
    `;
  }).join('');
  updateFlowAgentTip();
}

function toggleFlowAgent(reviewerId) {
  const prdId = currentPrdId();
  console.log('[prd-flow] toggleFlowAgent', { reviewerId, prdId, idElValue: document.getElementById('prd-edit-id') && document.getElementById('prd-edit-id').value });
  if (!prdId) { alert('未关联 PRD,请回到编辑页保存草稿再走流程'); return; }
  const ids = getSelectedAgentIds(prdId);
  const idx = ids.indexOf(reviewerId);
  if (idx >= 0) ids.splice(idx, 1);
  else ids.push(reviewerId);
  setSelectedAgentIds(prdId, ids);
  renderFlowAgents();
}

function updateFlowAgentTip() {
  const prdId = currentPrdId();
  const ids = getSelectedAgentIds(prdId);
  const sel = (cachedReviewers || []).filter(r => ids.includes(r.id));
  const tip = document.getElementById('flow-agent-tip');
  const info = document.getElementById('flow-agent-info');
  if (tip) {
    tip.textContent = sel.length
      ? '已选择:' + sel.map(r => r.name).join('、')
      : '尚未选择评审员(可多选)';
  }
  if (info) info.textContent = `已选 ${sel.length} 位评审员`;
}

function goStepStyle() {
  const prdId = currentPrdId();
  if (!prdId) { alert('未关联 PRD,请回到编辑页'); return; }
  const ids = getSelectedAgentIds(prdId);
  if (!ids.length) { alert('请至少选 1 位评审员'); return; }
  const target = '#style?id=' + prdId;
  if (location.hash === target) handleHashRoute();
  else location.hash = target;
}

// ────────────────────────────────────────
// 定评审风格页
// ────────────────────────────────────────

async function loadFlowStyles() {
  const grid = document.getElementById('flow-style-grid');
  if (!grid) return;
  grid.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">加载中…</div>';
  try {
    if (!cachedStyles) {
      const data = await authFetch(`${STYLES_API}?page=1&size=100`);
      cachedStyles = (data && data.items) || [];
    }
    // 新建 PRD 第一次进风格页:默认选 isDefault=true 的那条
    const prdId = currentPrdId();
    if (prdId && getSelectedStyleId(prdId) == null) {
      const def = cachedStyles.find(s => s.isDefault) || cachedStyles[0];
      if (def) setSelectedStyleId(prdId, def.id);
    }
    renderFlowStyles();
  } catch (e) {
    grid.innerHTML = `<div style="padding:24px;color:var(--danger);text-align:center">加载失败:${flowEscape(e.message)}</div>`;
  }
}

function renderFlowStyles() {
  const grid = document.getElementById('flow-style-grid');
  if (!grid) return;
  if (!cachedStyles || !cachedStyles.length) {
    grid.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">尚无评审风格,请管理员先在「评审风格」页配置</div>';
    return;
  }
  const prdId = currentPrdId();
  const selectedId = getSelectedStyleId(prdId);
  grid.innerHTML = cachedStyles.map(s => {
    const isSel = s.id === selectedId;
    const rules = (s.rules || []).slice(0, 4); // 前 4 条规则展示
    const ruleHtml = rules.map(r =>
      `<div class="focus-row"><span class="focus-label">✓</span><span><b>${flowEscape(r.label)}</b>:${flowEscape(r.content)}</span></div>`
    ).join('');
    return `
      <div class="style-card ${isSel ? 'selected' : ''}" onclick="selectFlowStyle(${s.id})">
        <div class="style-header">
          <div class="style-emoji">${flowEscape(s.icon || '🎯')}</div>
          <div class="style-name">${flowEscape(s.name)}${s.isDefault ? ' <span class="style-default-tag">默认</span>' : ''}</div>
        </div>
        <div class="style-scene">${flowEscape(s.scenario || '(无场景说明)')}</div>
        ${ruleHtml || '<div style="font-size:12px;color:var(--text-4)">无规则</div>'}
      </div>
    `;
  }).join('');
  updateFlowStyleTip();
}

function selectFlowStyle(styleId) {
  const prdId = currentPrdId();
  console.log('[prd-flow] selectFlowStyle', { styleId, prdId });
  if (!prdId) { alert('未关联 PRD,请回到编辑页保存草稿再走流程'); return; }
  setSelectedStyleId(prdId, styleId);
  renderFlowStyles();
}

function updateFlowStyleTip() {
  const prdId = currentPrdId();
  const styleId = getSelectedStyleId(prdId);
  const style = (cachedStyles || []).find(s => s.id === styleId);
  const agentCount = getSelectedAgentIds(prdId).length;
  const info = document.getElementById('flow-style-info');
  if (info) {
    info.textContent = style
      ? `${style.name} 风格 · ${agentCount} 位评审员`
      : '未选风格';
  }
}

function goStepPrecheck() {
  const prdId = currentPrdId();
  if (!prdId) { alert('未关联 PRD,请回到编辑页'); return; }
  const styleId = getSelectedStyleId(prdId);
  if (!styleId) { alert('请选 1 种评审风格'); return; }
  const target = '#precheck?id=' + prdId;
  if (location.hash === target) handleHashRoute();
  else location.hash = target;
}

// ────────────────────────────────────────
// 提交评审页(确认)
// ────────────────────────────────────────

async function loadFlowPrecheck() {
  const prdId = currentPrdId();
  const titleEl = document.getElementById('flow-precheck-title');
  const contentEl = document.getElementById('flow-precheck-content');
  const agentsEl = document.getElementById('flow-precheck-agents');
  const styleEl = document.getElementById('flow-precheck-style');
  const agentCntEl = document.getElementById('flow-precheck-agent-count');
  const hintEl = document.getElementById('flow-precheck-hint');
  if (!titleEl || !contentEl) return;

  if (!prdId) {
    titleEl.textContent = '— 未加载 PRD —';
    contentEl.textContent = '请先回到 #1 编写方案,保存后再走流程。';
    return;
  }

  // 拉 PRD 最新内容(用户可能在中间编辑过)
  contentEl.textContent = '加载中…';
  try {
    const prd = await authFetch('/api/v1/prds/' + prdId);
    titleEl.textContent = prd.title || '(无标题)';
    contentEl.textContent = prd.content || '(无内容)';
  } catch (e) {
    contentEl.textContent = '加载 PRD 失败:' + e.message;
  }

  // 渲染已选评审员
  const agentIds = getSelectedAgentIds(prdId);
  if (cachedReviewers) {
    const agents = cachedReviewers.filter(r => agentIds.includes(r.id));
    agentsEl.innerHTML = agents.length
      ? agents.map(a =>
          `<div style="padding:6px 10px;background:var(--primary-light);border-radius:6px;margin-bottom:6px;font-size:13px">
            ${flowEscape(a.icon || '🤖')} <b>${flowEscape(a.name)}</b>
            <div style="font-size:11px;color:var(--text-3);margin-top:2px">${flowEscape(a.description || '')}</div>
          </div>`).join('')
      : '<div style="padding:8px;color:var(--danger);font-size:12px">⚠ 未选评审员</div>';
  }
  if (agentCntEl) agentCntEl.textContent = String(agentIds.length);

  // 渲染已选风格
  const styleId = getSelectedStyleId(prdId);
  if (cachedStyles) {
    const style = cachedStyles.find(s => s.id === styleId);
    styleEl.innerHTML = style
      ? `<div style="padding:6px 10px;background:var(--primary-light);border-radius:6px;font-size:13px">
          ${flowEscape(style.icon || '🎯')} <b>${flowEscape(style.name)}</b>
          <div style="font-size:11px;color:var(--text-3);margin-top:2px">${flowEscape(style.scenario || '')}</div>
        </div>`
      : '<div style="padding:8px;color:var(--danger);font-size:12px">⚠ 未选风格</div>';
  }

  // 提示文案
  if (hintEl) {
    const ready = agentIds.length > 0 && styleId != null;
    hintEl.textContent = ready
      ? '✓ 配置完整,点"确认提交评审"触发 #6 输入门槛 + 真后端 SUBMITTED 归档'
      : '⚠ 请回到上一步补全选择';
  }
}

// ────────────────────────────────────────
// navTo 钩子(在 app.html 的 navTo 末尾扩展)
// ────────────────────────────────────────

/**
 * 流程页防护:已提交评审(SUBMITTED+)的 PRD 不允许再进流程编辑。
 * 用户从 URL 直接输入 #agent/#style/#precheck,或从评审详情误点回到流程,都会被拦截到评审详情。
 */
function guardFlowAccess(name) {
  if (!['agent', 'style', 'precheck'].includes(name)) return true;
  const prd = typeof getCurrentEditPrd === 'function' ? getCurrentEditPrd() : null;
  if (!prd) return true; // 新建模式无 prd,允许走流程
  if (prd.status === 'DRAFT') return true;
  // 已提交,锁定:跳到评审详情
  alert(`PRD "${prd.title}" 已是 ${prd.status} 状态,不能再修改流程配置。\n\n将跳转到评审详情查看。`);
  location.hash = '#review?id=' + prd.id;
  return false;
}

function maybeLoadFlowPage(name) {
  if (!guardFlowAccess(name)) return;
  if (name === 'agent') loadFlowAgents();
  else if (name === 'style') loadFlowStyles();
  else if (name === 'precheck') loadFlowPrecheck();
}

window.loadFlowAgents = loadFlowAgents;
window.loadFlowStyles = loadFlowStyles;
window.loadFlowPrecheck = loadFlowPrecheck;
window.toggleFlowAgent = toggleFlowAgent;
window.selectFlowStyle = selectFlowStyle;
window.goStepStyle = goStepStyle;
window.goStepPrecheck = goStepPrecheck;
window.maybeLoadFlowPage = maybeLoadFlowPage;
