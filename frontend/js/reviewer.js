/**
 * AI 评审员管理模块
 * - 列表加载 / 渲染
 * - 新建 / 编辑 / 启停 / 删除
 * - Prompt 模板占位符提示
 *
 * 依赖 auth.js 提供 authFetch / getCurrentUser。
 */

const REVIEWER_API = '/api/v1/reviewers';
// 评审员模板是纯角色定义（system prompt），不含 PRD 占位符；
// 被评审的 PRD 由后端在试跑/正式评审时作为独立 user 消息附加。
const ICON_PRESETS = ['🧑‍💼', '🏗️', '📊', '🔍', '🛡️', '🎨', '⚖️', '🤖', '💼', '🎯'];

/** 当前页所有评审员（保留 version 用于乐观锁更新） */
let currentReviewers = [];

/** 当前编辑中的评审员（null 表示新建模式） */
let editingReviewer = null;

/** 当前用户是否 ADMIN */
function isAdmin() {
  const u = getCurrentUser();
  return u && u.role === 'ADMIN';
}

/** 截断 Prompt 模板用于卡片预览 */
function truncatePrompt(text, max = 180) {
  if (!text) return '';
  const clean = text.replace(/\n+/g, ' ').trim();
  return clean.length > max ? clean.slice(0, max) + '…' : clean;
}

/** 转义 HTML，防止注入 */
function escapeHtml(s) {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** 加载评审员列表并渲染到 #page-reviewer-manage .main */
async function loadReviewers() {
  const container = document.querySelector('#page-reviewer-manage .reviewer-list');
  if (!container) return;
  container.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">加载中…</div>';
  try {
    // ADMIN 不传 enabled 看全部；非 ADMIN 后端会强制 enabled=true
    const data = await authFetch(`${REVIEWER_API}?page=1&size=100`);
    currentReviewers = data.items || [];
    renderReviewers();
  } catch (e) {
    container.innerHTML = `<div style="padding:24px;color:var(--danger);text-align:center">加载失败：${escapeHtml(e.message)}</div>`;
  }
}

/** 渲染评审员卡片 */
function renderReviewers() {
  const container = document.querySelector('#page-reviewer-manage .reviewer-list');
  if (!container) return;
  if (!currentReviewers.length) {
    container.innerHTML = '<div style="padding:24px;color:var(--text-3);text-align:center">暂无评审员</div>';
    return;
  }
  const adminMode = isAdmin();
  container.innerHTML = currentReviewers.map(r => {
    const enabledBadge = r.enabled
      ? '<span class="badge badge-active">启用</span>'
      : '<span class="badge badge-inactive">已停用</span>';
    const toggleBtn = adminMode
      ? (r.enabled
          ? `<button class="btn btn-danger-outline btn-sm" onclick="toggleReviewer(${r.id})">停用</button>`
          : `<button class="btn btn-primary btn-sm" onclick="toggleReviewer(${r.id})">启用</button>`)
      : '';
    const editBtn = adminMode
      ? `<button class="btn btn-ghost btn-sm" onclick="openEditModal(${r.id})">✏️ 编辑 Prompt</button>`
      : '';
    const deleteBtn = adminMode
      ? `<button class="btn btn-danger-outline btn-sm" onclick="deleteReviewer(${r.id})">🗑️ 删除</button>`
      : '';
    const testBtn = adminMode
      ? `<button class="btn btn-ghost btn-sm" onclick="openTestModal(${r.id})">🧪 测试</button>`
      : '';
    return `
      <div class="manage-card" style="${r.enabled ? '' : 'opacity:.65'}">
        <div class="manage-card-icon">${escapeHtml(r.icon || '🤖')}</div>
        <div class="manage-info">
          <div class="manage-name">${escapeHtml(r.name)} ${enabledBadge}</div>
          <div class="manage-desc">${escapeHtml(r.description || '')}</div>
          <div class="prompt-preview">${escapeHtml(truncatePrompt(r.promptTemplate))}</div>
          <div class="manage-actions">
            ${editBtn}
            ${testBtn}
            ${toggleBtn}
            ${deleteBtn}
          </div>
        </div>
      </div>
    `;
  }).join('');
}

/** 打开新建评审员 Modal */
function openCreateModal() {
  if (!isAdmin()) { alert('仅管理员可创建评审员'); return; }
  editingReviewer = null;
  document.getElementById('reviewer-modal-title').textContent = '新建评审员';
  document.getElementById('rv-name').value = '';
  document.getElementById('rv-icon').value = '🤖';
  document.getElementById('rv-desc').value = '';
  document.getElementById('rv-prompt').value = '你是一名资深产品评审专家。你将收到一份 PRD，请进行评审。\n\n重点关注：\n① …\n② …\n\n请给出分级问题清单（严重/重要/建议），每条包含定位、原因、改进建议。';
  document.getElementById('rv-enabled').checked = true; // 新建默认启用
  document.getElementById('rv-sort').value = 0;
  showReviewerModal();
}

/** 打开编辑评审员 Modal */
function openEditModal(reviewerId) {
  const r = currentReviewers.find(x => x.id === reviewerId);
  if (!r) return;
  editingReviewer = r;
  document.getElementById('reviewer-modal-title').textContent = '编辑评审员';
  document.getElementById('rv-name').value = r.name || '';
  document.getElementById('rv-icon').value = r.icon || '🤖';
  document.getElementById('rv-desc').value = r.description || '';
  document.getElementById('rv-prompt').value = r.promptTemplate || '';
  document.getElementById('rv-enabled').checked = !!r.enabled; // 保留原启用状态，启停由卡片按钮控制
  document.getElementById('rv-sort').value = r.sortOrder ?? 0;
  showReviewerModal();
}

function showReviewerModal() {
  document.getElementById('reviewer-modal').classList.add('open');
}

function closeReviewerModal() {
  document.getElementById('reviewer-modal').classList.remove('open');
}

/** 提交 Modal（创建或更新） */
async function submitReviewerModal() {
  const name = document.getElementById('rv-name').value.trim();
  const icon = document.getElementById('rv-icon').value.trim();
  const description = document.getElementById('rv-desc').value.trim();
  const promptTemplate = document.getElementById('rv-prompt').value.trim();
  const enabled = document.getElementById('rv-enabled').checked;
  const sortOrder = parseInt(document.getElementById('rv-sort').value || '0', 10);

  if (!name) { alert('请填写名称'); return; }
  if (!promptTemplate) { alert('请填写 Prompt 模板'); return; }

  try {
    if (editingReviewer) {
      await authFetch(`${REVIEWER_API}/${editingReviewer.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          name, icon, description, promptTemplate,
          enabled, sortOrder, version: editingReviewer.version,
        }),
      });
    } else {
      await authFetch(REVIEWER_API, {
        method: 'POST',
        body: JSON.stringify({ name, icon, description, promptTemplate }),
      });
    }
    closeReviewerModal();
    await loadReviewers();
  } catch (e) {
    alert('保存失败：' + e.message);
  }
}

/** 切换启用/禁用 */
async function toggleReviewer(reviewerId) {
  const r = currentReviewers.find(x => x.id === reviewerId);
  if (!r) return;
  const action = r.enabled ? '停用' : '启用';
  if (!confirm(`确定${action}「${r.name}」？`)) return;
  try {
    await authFetch(`${REVIEWER_API}/${r.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: r.name,
        icon: r.icon,
        description: r.description,
        promptTemplate: r.promptTemplate,
        enabled: !r.enabled,
        sortOrder: r.sortOrder,
        version: r.version,
      }),
    });
    await loadReviewers();
  } catch (e) {
    alert(`${action}失败：` + e.message);
  }
}

/** 删除评审员（逻辑删除） */
async function deleteReviewer(reviewerId) {
  const r = currentReviewers.find(x => x.id === reviewerId);
  if (!r) return;
  if (!confirm(`确定删除「${r.name}」？删除后将无法在评审中使用。`)) return;
  try {
    await authFetch(`${REVIEWER_API}/${r.id}`, { method: 'DELETE' });
    await loadReviewers();
  } catch (e) {
    alert('删除失败：' + e.message);
  }
}

// ────────────────────────────────────────────────────────────────────
// #9 试跑评审员 — SSE 流式接收 AI 输出
// ────────────────────────────────────────────────────────────────────

const DEFAULT_TEST_PRD_TITLE = '会员付费订阅功能方案';
const DEFAULT_TEST_PRD_CONTENT = `# 背景
随着免费用户规模扩大，需要通过付费订阅实现商业化转化。

# 目标
- 上线月付/年付两档订阅
- 首月特惠 9.9 元，年付立省 30%
- 6 个月内付费用户数突破 5 万

# 功能设计
1. 订阅页：展示套餐对比、特惠倒计时
2. 支付流程：支持微信 / 支付宝 / Apple Pay
3. 会员权益：去广告、高清画质、专属图标、内容优先体验
4. 自动续费：支持开启 / 关闭，到期前 7/3/1 天 push 提醒
5. 取消订阅：保留至本周期结束，可随时重新启用

# 风险与依赖
- 苹果 IAP 抽成 30%，年付定价需重新核算
- 续费提醒频次过高可能影响留存
- 退款政策需对齐法务建议`;

let currentTestReviewer = null;
let currentTestAbortController = null;

/** 打开试跑 Modal */
function openTestModal(reviewerId) {
  if (!isAdmin()) { alert('仅管理员可试跑评审员'); return; }
  const r = currentReviewers.find(x => x.id === reviewerId);
  if (!r) return;
  currentTestReviewer = r;

  document.getElementById('test-modal-title').textContent =
    `试跑评审员：${r.icon || '🤖'} ${r.name}`;
  document.getElementById('test-prd-title').value = DEFAULT_TEST_PRD_TITLE;
  document.getElementById('test-prd-content').value = DEFAULT_TEST_PRD_CONTENT;
  document.getElementById('test-output').textContent = '';
  document.getElementById('test-status').textContent = '准备就绪';
  document.getElementById('test-status').style.color = 'var(--text-3)';
  document.getElementById('test-start-btn').disabled = false;
  document.getElementById('test-cancel-btn').style.display = 'none';

  document.getElementById('test-modal').classList.add('open');
}

function closeTestModal() {
  // 关闭前先终止可能的流
  cancelTest();
  document.getElementById('test-modal').classList.remove('open');
  currentTestReviewer = null;
}

/** 开始试跑：fetch + ReadableStream 读 SSE */
async function startTest() {
  if (!currentTestReviewer) return;
  const prdTitle = document.getElementById('test-prd-title').value.trim();
  const prdContent = document.getElementById('test-prd-content').value.trim();
  if (!prdTitle || !prdContent) { alert('请填写 PRD 标题和内容'); return; }

  const outputEl = document.getElementById('test-output');
  const statusEl = document.getElementById('test-status');
  const startBtn = document.getElementById('test-start-btn');
  const cancelBtn = document.getElementById('test-cancel-btn');

  outputEl.textContent = '';
  statusEl.textContent = '调用 AI 中…';
  statusEl.style.color = 'var(--primary)';
  startBtn.disabled = true;
  cancelBtn.style.display = '';

  currentTestAbortController = new AbortController();
  const token = getToken();

  try {
    const resp = await fetch(
      `${API_BASE}/api/v1/reviewers/${currentTestReviewer.id}/test`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
          'Accept': 'text/event-stream',
        },
        body: JSON.stringify({ prdTitle, prdContent }),
        signal: currentTestAbortController.signal,
      }
    );

    if (!resp.ok) {
      // 后端在同步阶段抛 BizException 会走 GlobalResponseAdvice 返回 JSON，
      // 此时 Content-Type 是 application/json 而非 SSE。
      let errMsg = `HTTP ${resp.status}`;
      try {
        const j = await resp.json();
        errMsg = j.message || errMsg;
      } catch (_) { /* 忽略 */ }
      throw new Error(errMsg);
    }

    // 读取 SSE 流（text/event-stream）
    const reader = resp.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    // streamFinished 标记：服务端发完 done/error 后会主动关闭连接，
    // 部分浏览器随后抛 "network error"——此时忽略 catch 中的覆盖，保留终态。
    let streamFinished = false;

    outer: while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      // SSE 事件以 \n\n 分割，每个事件由 "data: <json>" 行组成
      let sepIdx;
      while ((sepIdx = buffer.indexOf('\n\n')) >= 0) {
        const rawEvent = buffer.slice(0, sepIdx);
        buffer = buffer.slice(sepIdx + 2);
        const stage = handleSseEvent(rawEvent, outputEl, statusEl);
        if (stage === 'done' || stage === 'error') {
          streamFinished = true;
          // 主动取消后续读取，避免触发 reader 关闭异常
          try { reader.cancel(); } catch (_) { /* 忽略 */ }
          break outer;
        }
      }
    }
    // 流读完但未收到 done（异常关闭场景）
    if (!streamFinished && statusEl.textContent === '调用 AI 中…') {
      statusEl.textContent = '完成';
      statusEl.style.color = 'var(--success)';
    }
  } catch (e) {
    // 服务端已发完 done/error，忽略后续 reader 关闭抛出的网络异常
    const statusText = statusEl.textContent;
    if (statusText === '完成' || statusText.startsWith('失败：')) {
      // 终态已就绪，跳过覆盖
    } else if (e.name === 'AbortError') {
      statusEl.textContent = '已取消';
      statusEl.style.color = 'var(--text-3)';
    } else {
      statusEl.textContent = '失败：' + e.message;
      statusEl.style.color = 'var(--danger)';
    }
  } finally {
    startBtn.disabled = false;
    cancelBtn.style.display = 'none';
    currentTestAbortController = null;
  }
}

/** 解析单个 SSE 事件块，返回 stage 名称（用于上层判断是否终态） */
function handleSseEvent(rawEvent, outputEl, statusEl) {
  let lastStage = null;
  const lines = rawEvent.split('\n');
  for (const line of lines) {
    if (!line.startsWith('data:')) continue;
    const payload = line.slice(5).trim();
    if (!payload) continue;
    let evt;
    try { evt = JSON.parse(payload); } catch (_) { continue; }

    lastStage = evt.stage;
    switch (evt.stage) {
      case 'token':
        outputEl.textContent += evt.message || '';
        outputEl.scrollTop = outputEl.scrollHeight;
        break;
      case 'done':
        statusEl.textContent = '完成';
        statusEl.style.color = 'var(--success)';
        break;
      case 'error':
        statusEl.textContent = '失败：' + (evt.message || '未知错误');
        statusEl.style.color = 'var(--danger)';
        break;
      default:
        break;
    }
  }
  return lastStage;
}

/** 取消试跑 */
function cancelTest() {
  if (currentTestAbortController) {
    currentTestAbortController.abort();
    currentTestAbortController = null;
  }
}

// 暴露给 HTML inline handler
window.loadReviewers = loadReviewers;
window.openCreateModal = openCreateModal;
window.openEditModal = openEditModal;
window.closeReviewerModal = closeReviewerModal;
window.submitReviewerModal = submitReviewerModal;
window.toggleReviewer = toggleReviewer;
window.deleteReviewer = deleteReviewer;
window.openTestModal = openTestModal;
window.closeTestModal = closeTestModal;
window.startTest = startTest;
window.cancelTest = cancelTest;
