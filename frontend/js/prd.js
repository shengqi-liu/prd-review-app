/**
 * PRD 列表 + 编辑器前端模块(#add-prd-frontend)。
 *
 * 列表页:动态拉取 GET /api/v1/prds,渲染卡片 + stat 统计
 * 编辑器:URL hash 路由(#edit / #edit?id=N)+ CRUD + submit + delete
 *
 * 依赖 auth.js 的 authFetch / getToken / API_BASE。
 */

const PRD_API = '/api/v1/prds';

/** PRD 评审流程的 4 步(与进度栏对齐) */
const FLOW_STEPS = ['edit', 'agent', 'style', 'precheck'];
const FLOW_LABELS = {
  edit:     '编写方案',
  agent:    '选评审员',
  style:    '定评审风格',
  precheck: '提交评审',
};

/** localStorage 记录 PRD 进度,key = prd_flow_<prdId>,value = step name */
function saveFlowProgress(prdId, step) {
  if (!prdId) return;
  try { localStorage.setItem('prd_flow_' + prdId, step); } catch (_) {}
}
function loadFlowProgress(prdId) {
  if (!prdId) return null;
  try { return localStorage.getItem('prd_flow_' + prdId); } catch (_) { return null; }
}
function clearFlowProgress(prdId) {
  if (!prdId) return;
  try { localStorage.removeItem('prd_flow_' + prdId); } catch (_) {}
}

/** 取进度的人类可读"第 N 步 · xxx"标签 */
function describeFlowProgress(prdId) {
  const step = loadFlowProgress(prdId);
  if (!step) return null;
  const idx = FLOW_STEPS.indexOf(step);
  if (idx < 0) return null;
  return `第 ${idx + 1}/${FLOW_STEPS.length} 步 · ${FLOW_LABELS[step]}`;
}

/** PRD 状态徽标映射(label + 复用现有 badge-* CSS 类) */
const STATUS_BADGE = {
  INITIALIZING: { label: '初始化中', cls: 'badge-draft' },
  DRAFT:        { label: '草稿',     cls: 'badge-draft' },
  SUBMITTED:    { label: '评审中',   cls: 'badge-reviewing' }, // 已提交,等待 AI 启动
  UNDER_REVIEW: { label: '评审中',   cls: 'badge-reviewing' }, // AI 跑评审中
  APPROVED:     { label: '已通过',   cls: 'badge-pass' },
  REJECTED:     { label: '不通过',   cls: 'badge-fail' },
};

/** 当前编辑器加载的 PRD(供其他模块判定 DRAFT/已提交 状态) */
let currentEditPrd = null;
function getCurrentEditPrd() { return currentEditPrd; }
window.getCurrentEditPrd = getCurrentEditPrd;

function prdEscape(s) {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function prdFormatDate(iso) {
  if (!iso) return '—';
  return iso.split('T')[0];
}

// ─────────────────────────────────────────────────────────
// 列表页
// ─────────────────────────────────────────────────────────

async function loadPrds() {
  const listEl = document.getElementById('prd-list-container');
  const statsEl = document.getElementById('prd-stats');
  if (!listEl || !statsEl) return;
  listEl.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">加载中…</div>';
  statsEl.innerHTML = '';
  try {
    const data = await authFetch(`${PRD_API}?page=1&size=100`);
    const items = (data && data.items) || [];
    renderPrdStats(items);
    renderPrdList(items);
    updateNavBadge(items.length);
  } catch (e) {
    listEl.innerHTML = `<div style="padding:24px;color:var(--danger);text-align:center">加载失败:${prdEscape(e.message)}</div>`;
    updateNavBadge(0);
  }
}

/** 侧边栏"我的方案"徽标数字(动态从 PRD 列表算)。 */
function updateNavBadge(count) {
  const el = document.getElementById('nav-badge-prd');
  if (!el) return;
  if (count > 0) {
    el.textContent = String(count);
    el.style.display = '';
  } else {
    el.style.display = 'none';
  }
}

function renderPrdStats(items) {
  const statsEl = document.getElementById('prd-stats');
  if (!statsEl) return;
  if (!items.length) { statsEl.innerHTML = ''; return; }
  const count = (status) => items.filter(p => p.status === status).length;
  const total = items.length;
  const draft = count('DRAFT') + count('INITIALIZING');
  const reviewing = count('SUBMITTED') + count('UNDER_REVIEW');
  const approved = count('APPROVED');
  const rejected = count('REJECTED');
  statsEl.innerHTML = `
    <div class="stat-card"><div class="stat-num">${total}</div><div class="stat-label">全部方案</div></div>
    <div class="stat-card"><div class="stat-num" style="color:var(--text-2)">${draft}</div><div class="stat-label">草稿</div></div>
    <div class="stat-card"><div class="stat-num" style="color:var(--primary)">${reviewing}</div><div class="stat-label">评审中</div></div>
    <div class="stat-card"><div class="stat-num" style="color:var(--success)">${approved}</div><div class="stat-label">已通过</div></div>
    ${rejected > 0 ? `<div class="stat-card"><div class="stat-num" style="color:var(--danger)">${rejected}</div><div class="stat-label">不通过</div></div>` : ''}
  `;
}

function renderPrdList(items) {
  const listEl = document.getElementById('prd-list-container');
  if (!listEl) return;
  if (!items.length) {
    listEl.innerHTML = `<div style="padding:48px 24px;text-align:center;color:var(--text-3)">
      <div style="font-size:32px;margin-bottom:8px;opacity:.5">📝</div>
      <div>暂无方案,点击右上角"＋ 新建方案"或在编辑页"📎 上传文档"开始</div>
    </div>`;
    return;
  }
  listEl.innerHTML = items.map(p => {
    const badge = STATUS_BADGE[p.status] || { label: p.status, cls: 'badge-draft' };
    // 进度标签只对未提交状态(DRAFT/INITIALIZING)显示
    // 注意:已提交状态下只清"进度标签"(prd_flow_),保留"评审员/风格选择"(prd_agents_/prd_style_)——
    // 评审详情页要用这两条快照展示用户当初选了谁评审
    const isInFlow = p.status === 'DRAFT' || p.status === 'INITIALIZING';
    let progressTag = '';
    if (isInFlow) {
      const progress = describeFlowProgress(p.id);
      if (progress) progressTag = `<span style="color:var(--primary);font-weight:600">📍 ${prdEscape(progress)}</span>`;
    } else {
      // 已提交了还有"流程位置"残留 → 清掉(避免下次进编辑器误显示)
      // 但保留 prd_agents_ / prd_style_(评审详情页要展示)
      try {
        if (localStorage.getItem('prd_flow_' + p.id)) localStorage.removeItem('prd_flow_' + p.id);
      } catch (_) {}
    }
    return `
      <div class="prd-item" onclick="openPrdById(${p.id})">
        <div>
          <div class="prd-title">${prdEscape(p.title || '(无标题)')}</div>
          <div class="prd-meta">
            <span>${prdFormatDate(p.createdAt)}</span>
            <span>更新于 ${prdFormatDate(p.updatedAt)}</span>
            ${p.sourceUrl ? '<span>📎 URL 路径</span>' : ''}
            ${progressTag}
          </div>
        </div>
        <div class="prd-right">
          <span class="badge ${badge.cls}">${badge.label}</span>
          <span style="color:var(--text-4);font-size:16px">›</span>
        </div>
      </div>
    `;
  }).join('');
}

// ─────────────────────────────────────────────────────────
// 编辑器
// ─────────────────────────────────────────────────────────

function goCreatePrd() {
  // 直接路由,不依赖 hashchange(如果上次 hash 已是 #edit,setting 相同值不会触发事件)
  if (location.hash === '#edit') {
    handleHashRoute();
  } else {
    location.hash = '#edit';
  }
}

/**
 * 列表卡片点击入口:DRAFT 状态进编辑器,其他状态(SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED)
 * 进"评审详情"页(只读快照 + 配置展示)。
 */
async function openPrdById(id) {
  // 先轻量查一次状态(可改进:让列表数据里就带 status,但当前列表已经返回了 status,我们直接读 DOM)
  // 简化:直接 fetch 详情,根据 status 路由
  let target = '#edit?id=' + id;
  try {
    const prd = await authFetch(PRD_API + '/' + id);
    if (prd && prd.status && prd.status !== 'DRAFT' && prd.status !== 'INITIALIZING') {
      target = '#review?id=' + id;
    }
  } catch (_) {
    // 查不到就走默认 edit 路径,让 openPrdEditor 自己再报错
  }
  if (location.hash === target) {
    handleHashRoute();
  } else {
    location.hash = target;
  }
}

/** 进编辑器:id=null 新建空白,否则加载 PRD */
async function openPrdEditor(id) {
  const titleEl = document.getElementById('prd-edit-title');
  const contentEl = document.getElementById('prd-edit-content');
  const idEl = document.getElementById('prd-edit-id');
  const versionEl = document.getElementById('prd-edit-version');
  const infoEl = document.getElementById('prd-edit-info');
  const badgeEl = document.getElementById('prd-status-badge');
  const nextBtn = document.getElementById('prd-next-btn');
  const deleteBtn = document.getElementById('prd-delete-btn');
  const saveBtn = document.getElementById('prd-save-btn');
  const saveStatusEl = document.getElementById('prd-save-status');

  if (!titleEl || !contentEl) return;

  if (id == null) {
    // 新建模式:允许"下一步"(会先 saveDraft 持久化再 navTo agent),但无删除按钮(还没存)
    currentEditPrd = null;
    titleEl.value = '';
    contentEl.value = '';
    idEl.value = '';
    versionEl.value = '';
    infoEl.textContent = '新建 PRD';
    badgeEl.innerHTML = '';
    nextBtn.style.display = '';
    deleteBtn.style.display = 'none';
    saveBtn.style.display = '';
    titleEl.disabled = false;
    contentEl.disabled = false;
    saveStatusEl.textContent = '未保存';
    updatePrdCharCount();
    titleEl.focus();
    return;
  }

  // 编辑/查看模式
  saveStatusEl.textContent = '加载中…';
  try {
    const prd = await authFetch(`${PRD_API}/${id}`);
    currentEditPrd = prd;
    idEl.value = prd.id;
    versionEl.value = prd.version;
    titleEl.value = prd.title || '';
    contentEl.value = prd.content || '';
    infoEl.textContent = `PRD #${prd.id} · v${prd.version} · 创建于 ${prdFormatDate(prd.createdAt)}`;
    const badge = STATUS_BADGE[prd.status] || { label: prd.status, cls: 'badge-draft' };
    badgeEl.innerHTML = `<span class="badge ${badge.cls}">${badge.label}</span>`;
    const isDraft = prd.status === 'DRAFT';
    // 已提交(SUBMITTED+)状态全锁:不能编辑 / 不能保存 / 不能删除 / 不能再走流程
    nextBtn.style.display = isDraft ? '' : 'none';
    deleteBtn.style.display = isDraft ? '' : 'none';
    saveBtn.style.display = isDraft ? '' : 'none';
    titleEl.disabled = !isDraft;
    contentEl.disabled = !isDraft;
    saveStatusEl.textContent = isDraft ? '已保存' : '只读(状态:' + badge.label + ')';
    updatePrdCharCount();
  } catch (e) {
    alert('加载 PRD 失败:' + e.message);
    navTo('list', null);
  }
}

function updatePrdCharCount() {
  const el = document.getElementById('prd-char-count');
  const contentEl = document.getElementById('prd-edit-content');
  if (el && contentEl) {
    el.textContent = contentEl.value.length + ' 字符';
  }
}

async function saveDraft() {
  const title = document.getElementById('prd-edit-title').value.trim();
  const content = document.getElementById('prd-edit-content').value;
  if (!title) { alert('请填写标题'); return; }
  if (!content.trim()) { alert('请填写内容'); return; }

  const id = document.getElementById('prd-edit-id').value;
  const saveStatusEl = document.getElementById('prd-save-status');
  saveStatusEl.textContent = '保存中…';

  try {
    let prd;
    if (id) {
      // 更新
      const version = parseInt(document.getElementById('prd-edit-version').value, 10);
      prd = await authFetch(`${PRD_API}/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ title, content, version }),
      });
    } else {
      // 新建
      prd = await authFetch(PRD_API, {
        method: 'POST',
        body: JSON.stringify({ title, content }),
      });
    }
    currentEditPrd = prd;
    document.getElementById('prd-edit-id').value = prd.id;
    document.getElementById('prd-edit-version').value = prd.version;
    document.getElementById('prd-edit-info').textContent =
      `PRD #${prd.id} · v${prd.version} · 创建于 ${prdFormatDate(prd.createdAt)}`;
    document.getElementById('prd-next-btn').style.display = prd.status === 'DRAFT' ? '' : 'none';
    document.getElementById('prd-delete-btn').style.display = prd.status === 'DRAFT' ? '' : 'none';
    saveStatusEl.textContent = '已保存 ' + new Date().toLocaleTimeString();
    // 更新 hash 避免刷新后丢(replaceState 不触发 hashchange,刚好不重复 load)
    if (location.hash !== '#edit?id=' + prd.id) {
      history.replaceState(null, '', '#edit?id=' + prd.id);
    }
    // 保存草稿后,顶部进度栏第 1 步标 ✓(原型评审流程的视觉反馈)
    const stepEdit = document.getElementById('step-edit');
    if (stepEdit) {
      stepEdit.classList.remove('active');
      stepEdit.classList.add('done');
      const numEl = stepEdit.querySelector('.step-num');
      if (numEl) numEl.textContent = '✓';
    }
  } catch (e) {
    saveStatusEl.textContent = '保存失败';
    alert('保存失败:' + e.message);
  }
}

/**
 * 编辑页"下一步"按钮:先保存草稿持久化最新内容,然后进入评审员选择页(原型)。
 * 真正的 backend submit 在评审流程最后一步(precheck)触发。
 */
async function goNextFromEdit() {
  const title = document.getElementById('prd-edit-title').value.trim();
  const content = document.getElementById('prd-edit-content').value;
  if (!title) { alert('请先填写标题'); return; }
  if (!content.trim()) { alert('请先填写内容'); return; }

  // 先持久化(若新建则获得 id),保证后续流程操作的是最新内容
  await saveDraft();

  const prdId = document.getElementById('prd-edit-id').value;
  if (!prdId) { alert('保存草稿失败,无法继续'); return; }

  saveFlowProgress(prdId, 'agent');

  // 用带 id 的 hash 跳转,URL 永远保留 PRD id,流程页 localStorage 写入不会丢
  const target = '#agent?id=' + prdId;
  if (location.hash === target) {
    handleHashRoute();
  } else {
    location.hash = target;
  }
}

async function submitForReview() {
  const id = document.getElementById('prd-edit-id').value;
  if (!id) {
    alert('请先保存草稿,再提交评审');
    return;
  }
  // 先保存最新内容,避免提交旧版
  await saveDraft();
  if (!confirm('确认提交评审?提交后将进入待评审状态,不可再修改。')) return;
  try {
    const prd = await authFetch(`${PRD_API}/${id}/submit`, { method: 'POST' });
    // 已正式提交,流程结束:只清"流程位置"标签;评审员/风格选择保留,供评审详情页展示
    clearFlowProgress(id);
    alert(`✓ 提交成功!\n\nPRD #${prd.id} 已进入"${(STATUS_BADGE[prd.status] || {}).label}"状态。`);
    location.hash = '#list';
  } catch (e) {
    // #6 校验错误(30002 字数,30003 章节)消息已含具体细节
    alert('提交失败:\n\n' + e.message);
  }
}

async function deleteDraft() {
  const id = document.getElementById('prd-edit-id').value;
  if (!id) return;
  if (!confirm('确认删除该草稿?此操作不可撤销。')) return;
  try {
    await authFetch(`${PRD_API}/${id}`, { method: 'DELETE' });
    alert('已删除');
    location.hash = '#list';
  } catch (e) {
    alert('删除失败:' + e.message);
  }
}

// ─────────────────────────────────────────────────────────
// 路由
// ─────────────────────────────────────────────────────────

/**
 * 评审详情页加载(SUBMITTED+ 状态 PRD 的只读快照 + 评审配置展示)。
 * 多 Agent 真评审在 #17/#18 落地后,本页将替换为真实评审结果。
 */
async function loadReviewDetail(id) {
  const titleEl = document.getElementById('review-detail-prd-title');
  const contentEl = document.getElementById('review-detail-content');
  const statusEl = document.getElementById('review-detail-status');
  const infoEl = document.getElementById('review-detail-info');
  const agentsEl = document.getElementById('review-detail-agents');
  const styleEl = document.getElementById('review-detail-style');
  const agentCntEl = document.getElementById('review-detail-agent-count');

  if (!titleEl || !contentEl) return;
  if (!id) { contentEl.textContent = '未指定 PRD id'; return; }

  contentEl.textContent = '加载中…';
  try {
    const prd = await authFetch(PRD_API + '/' + id);
    titleEl.textContent = prd.title || '(无标题)';
    contentEl.textContent = prd.content || '(无内容)';

    const badge = STATUS_BADGE[prd.status] || { label: prd.status, cls: 'badge-draft' };
    statusEl.innerHTML = `<span class="badge ${badge.cls}">${badge.label}</span>`;
    infoEl.textContent = `PRD #${prd.id} · v${prd.version} · 创建于 ${prdFormatDate(prd.createdAt)} · 更新于 ${prdFormatDate(prd.updatedAt)}`;

    // 已选评审员 / 风格(localStorage 提交前快照)
    const agentIds = JSON.parse(localStorage.getItem('prd_agents_' + id) || '[]');
    const styleId = parseInt(localStorage.getItem('prd_style_' + id) || '0', 10);
    if (agentCntEl) agentCntEl.textContent = String(agentIds.length);

    if (agentsEl) {
      if (!agentIds.length) {
        agentsEl.innerHTML = '<div style="padding:8px;color:var(--text-3);font-size:12px">(本浏览器无记录,该 PRD 可能在其他设备提交)</div>';
      } else if (typeof cachedReviewers !== 'undefined' && cachedReviewers) {
        const agents = cachedReviewers.filter(r => agentIds.includes(r.id));
        agentsEl.innerHTML = agents.map(a =>
          `<div style="padding:6px 10px;background:var(--primary-light);border-radius:6px;margin-bottom:6px;font-size:13px">
            ${prdEscape(a.icon || '🤖')} <b>${prdEscape(a.name)}</b>
          </div>`).join('');
      } else {
        // 没缓存,直接拉一次
        try {
          const data = await authFetch('/api/v1/reviewers?page=1&size=100');
          const items = (data && data.items) || [];
          const agents = items.filter(r => agentIds.includes(r.id));
          agentsEl.innerHTML = agents.map(a =>
            `<div style="padding:6px 10px;background:var(--primary-light);border-radius:6px;margin-bottom:6px;font-size:13px">
              ${prdEscape(a.icon || '🤖')} <b>${prdEscape(a.name)}</b>
            </div>`).join('');
        } catch (_) { agentsEl.innerHTML = '<div style="font-size:12px;color:var(--text-3)">已选 ID:[' + agentIds.join(',') + ']</div>'; }
      }
    }
    if (styleEl) {
      if (!styleId) {
        styleEl.innerHTML = '<div style="padding:8px;color:var(--text-3);font-size:12px">(本浏览器无记录)</div>';
      } else if (typeof cachedStyles !== 'undefined' && cachedStyles) {
        const style = cachedStyles.find(s => s.id === styleId);
        styleEl.innerHTML = style
          ? `<div style="padding:6px 10px;background:var(--primary-light);border-radius:6px;font-size:13px">
              ${prdEscape(style.icon || '🎯')} <b>${prdEscape(style.name)}</b>
            </div>`
          : `<div style="font-size:12px;color:var(--text-3)">风格 ID:${styleId}</div>`;
      } else {
        try {
          const data = await authFetch('/api/v1/review-styles?page=1&size=100');
          const items = (data && data.items) || [];
          const style = items.find(s => s.id === styleId);
          styleEl.innerHTML = style
            ? `<div style="padding:6px 10px;background:var(--primary-light);border-radius:6px;font-size:13px">
                ${prdEscape(style.icon || '🎯')} <b>${prdEscape(style.name)}</b>
              </div>`
            : `<div style="font-size:12px;color:var(--text-3)">风格 ID:${styleId}</div>`;
        } catch (_) { styleEl.innerHTML = `<div style="font-size:12px;color:var(--text-3)">风格 ID:${styleId}</div>`; }
      }
    }
  } catch (e) {
    contentEl.textContent = '加载失败:' + e.message;
  }
}
window.loadReviewDetail = loadReviewDetail;

function parseHashRoute() {
  const h = location.hash.replace(/^#/, '');
  if (!h) return { page: 'list', params: {} };
  const [page, query] = h.split('?');
  const params = {};
  if (query) {
    query.split('&').forEach(kv => {
      const [k, v] = kv.split('=');
      params[k] = decodeURIComponent(v || '');
    });
  }
  return { page, params };
}

let __routing = false; // 防止 navTo 与 hashchange 循环触发

async function handleHashRoute() {
  if (__routing) return;
  __routing = true;
  try {
    const { page, params } = parseHashRoute();
    // 流程页/编辑页/评审详情:都把 URL 里的 id 同步到隐藏字段,作为 currentPrdId() 的兜底
    const urlId = params.id ? parseInt(params.id, 10) : null;
    if (urlId) {
      const idEl = document.getElementById('prd-edit-id');
      if (idEl) idEl.value = urlId;
    }

    if (page === 'list') {
      if (typeof navTo === 'function') navTo('list', null);
    } else if (page === 'edit') {
      if (typeof navTo === 'function') navTo('edit', null);
      await openPrdEditor(urlId);
    } else if (page === 'agent') {
      if (typeof navTo === 'function') navTo('agent', null);
    } else if (page === 'style') {
      if (typeof navTo === 'function') navTo('style', null);
    } else if (page === 'precheck') {
      if (typeof navTo === 'function') navTo('precheck', null);
    } else if (page === 'review') {
      if (typeof navTo === 'function') navTo('reviewing', null);
      await loadReviewDetail(urlId);
    }
  } finally {
    __routing = false;
  }
}

// 监听 content 输入更新字符数
document.addEventListener('input', (e) => {
  if (e.target && e.target.id === 'prd-edit-content') updatePrdCharCount();
});

window.handleHashRoute = handleHashRoute;
window.addEventListener('hashchange', handleHashRoute);
// 页面初次加载时,如果 hash 已含 #edit?id=,触发一次路由
document.addEventListener('DOMContentLoaded', () => {
  if (location.hash) {
    setTimeout(handleHashRoute, 100); // 等 auth.js + app.html script 初始化完
  }
});

// 暴露给 inline onclick
window.loadPrds = loadPrds;
window.goCreatePrd = goCreatePrd;
window.openPrdById = openPrdById;
window.openPrdEditor = openPrdEditor;
window.saveDraft = saveDraft;
window.submitForReview = submitForReview;
window.goNextFromEdit = goNextFromEdit;
window.deleteDraft = deleteDraft;
