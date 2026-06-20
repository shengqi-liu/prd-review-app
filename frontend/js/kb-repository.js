/**
 * 知识库 Git 仓库管理模块。
 *
 * - 加载并渲染当前仓库状态（HEALTHY / SYNCING / ERROR + 上次同步信息）
 * - ADMIN 可配置 / 编辑 / 立即同步 / 删除仓库
 * - 索引列表 / 检索测试在 #14 落地后再接通
 *
 * 依赖 auth.js 提供 authFetch / getCurrentUser / API_BASE。
 */

const KB_REPO_API = '/api/v1/kb/repositories';

/** 当前已加载的仓库（最多一个） */
let currentKbRepo = null;
let editingKbRepo = false;

function kbIsAdmin() {
  const u = getCurrentUser();
  return u && u.role === 'ADMIN';
}

function kbEscape(s) {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function kbFormatDateTime(iso) {
  if (!iso) return '从未同步';
  try {
    const d = new Date(iso);
    return d.toLocaleString();
  } catch (_) {
    return iso;
  }
}

function updateTopbarKbButtons() {
  const admin = kbIsAdmin();
  const topbarSync = document.getElementById('topbar-kb-sync-btn');
  const topbarConfig = document.getElementById('topbar-kb-config-btn');
  if (topbarConfig) topbarConfig.style.display = admin ? '' : 'none';
  if (topbarSync) {
    topbarSync.style.display = admin ? '' : 'none';
    topbarSync.disabled = !currentKbRepo || currentKbRepo.syncStatus === 'SYNCING';
  }
}

async function loadKbStatus() {
  const container = document.getElementById('kb-repo-status');
  const admin = kbIsAdmin();

  // 页面内按钮（知识库 tab 内，保持向后兼容）
  const syncBtn = document.getElementById('kb-sync-btn');
  const configBtn = document.getElementById('kb-config-btn');
  if (configBtn) configBtn.style.display = admin ? '' : 'none';

  try {
    const data = await authFetch(`${KB_REPO_API}?_=${Date.now()}`);
    const list = Array.isArray(data) ? data : [];
    currentKbRepo = list[0] || null;
    if (container) renderKbStatus();
    if (syncBtn) {
      syncBtn.disabled = !currentKbRepo || !admin;
      syncBtn.style.display = admin ? '' : 'none';
    }
    updateTopbarKbButtons();
  } catch (e) {
    if (container) container.innerHTML = `<div style="padding:18px;color:var(--danger);text-align:center">加载失败：${kbEscape(e.message)}</div>`;
    updateTopbarKbButtons();
  }
}

function renderKbStatus() {
  const container = document.getElementById('kb-repo-status');
  if (!container) return;
  if (!currentKbRepo) {
    container.innerHTML = `
      <div style="padding:18px;border:1px dashed var(--border);border-radius:var(--radius-lg);color:var(--text-3);text-align:center">
        尚未配置知识库仓库${kbIsAdmin() ? '，点击右上方"⚙ 配置仓库"开始配置' : '，请联系管理员'}
      </div>`;
    return;
  }
  const r = currentKbRepo;
  const statusColor = ({HEALTHY: 'var(--success)', SYNCING: 'var(--primary)', ERROR: 'var(--danger)'})[r.syncStatus] || 'var(--text-3)';
  const statusLabel = ({HEALTHY: '健康', SYNCING: '同步中…', ERROR: '同步失败'})[r.syncStatus] || r.syncStatus;
  const editBtn = kbIsAdmin()
    ? `<button class="btn btn-ghost btn-sm" onclick="openKbConfigModal(true)">✏️ 编辑</button>`
    : '';
  const deleteBtn = kbIsAdmin()
    ? `<button class="btn btn-danger-outline btn-sm" onclick="deleteKbRepo()">🗑️ 删除</button>`
    : '';
  const errorBlock = r.syncStatus === 'ERROR' && r.lastErrorMessage
    ? `<div style="margin-top:10px;padding:10px;background:#fff4f4;border-left:3px solid var(--danger);border-radius:6px;color:var(--danger);font-size:12px;word-break:break-all">⚠ ${kbEscape(r.lastErrorMessage)}</div>`
    : '';

  container.innerHTML = `
    <div style="background:#fff;border-radius:var(--radius-lg);border:1px solid var(--border);padding:18px 20px">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;flex-wrap:wrap;gap:8px">
        <div style="display:flex;align-items:center;gap:10px">
          <div style="width:8px;height:8px;border-radius:50%;background:${statusColor}"></div>
          <span style="font-weight:700;color:var(--text-1)">${kbEscape(r.name)}</span>
          <span style="font-size:12px;color:${statusColor};font-weight:600">${statusLabel}</span>
        </div>
        <div style="display:flex;gap:8px">${editBtn}${deleteBtn}</div>
      </div>
      <div class="kb-stats">
        <div class="kb-stat">
          <div class="kb-stat-num" style="font-size:13px;color:var(--text-2);word-break:break-all">${kbEscape(r.remoteUrl)}</div>
          <div class="kb-stat-lbl">远端 URL · 分支 ${kbEscape(r.branch)}</div>
        </div>
        <div class="kb-stat">
          <div class="kb-stat-num" style="font-size:13px;font-family:'SF Mono',Consolas,monospace">${r.lastSyncedCommit ? r.lastSyncedCommit.substring(0, 7) : '—'}</div>
          <div class="kb-stat-lbl">最后同步 commit</div>
        </div>
        <div class="kb-stat">
          <div class="kb-stat-num" style="font-size:14px">${kbEscape(kbFormatDateTime(r.lastSyncedAt))}</div>
          <div class="kb-stat-lbl">最后同步时间</div>
        </div>
      </div>
      ${errorBlock}
    </div>`;
}

function openKbConfigModal(editMode = false) {
  if (!kbIsAdmin()) { alert('仅管理员可配置知识库'); return; }
  editingKbRepo = !!editMode && !!currentKbRepo;
  document.getElementById('kb-modal-title').textContent = editingKbRepo ? '编辑知识库仓库' : '配置知识库仓库';
  const r = editingKbRepo ? currentKbRepo : {};
  document.getElementById('kb-name').value = r.name || '';
  document.getElementById('kb-remote-url').value = r.remoteUrl || '';
  document.getElementById('kb-branch').value = r.branch || 'main';
  document.getElementById('kb-auth-type').value = r.authType || 'NONE';
  document.getElementById('kb-auth-secret').value = '';
  document.getElementById('kb-auth-secret-hint').textContent = editingKbRepo && r.authSecretMasked
    ? '已配置（留空保持不变；填入新值则覆盖）'
    : '';
  document.getElementById('kb-poll-interval').value = r.pollIntervalMs || 3600000;
  document.getElementById('kb-modal').classList.add('open');
}

function closeKbModal() {
  document.getElementById('kb-modal').classList.remove('open');
}

async function submitKbConfig() {
  const name = document.getElementById('kb-name').value.trim();
  const remoteUrl = document.getElementById('kb-remote-url').value.trim();
  const branch = document.getElementById('kb-branch').value.trim() || 'main';
  const authType = document.getElementById('kb-auth-type').value;
  const authSecretInput = document.getElementById('kb-auth-secret').value;
  const pollIntervalMs = parseInt(document.getElementById('kb-poll-interval').value || '3600000', 10);

  if (!name) { alert('请填写仓库名称'); return; }
  if (!remoteUrl) { alert('请填写远端 URL'); return; }
  if ((authType === 'HTTPS_TOKEN' || authType === 'SSH_KEY_PATH') && !editingKbRepo && !authSecretInput) {
    alert('请填写凭据'); return;
  }

  // 编辑模式：authSecret 为空表示不修改（后端会用旧值），但目前后端 update 是覆盖；先按填什么传什么处理
  const body = {
    name, remoteUrl, branch, authType, authSecret: authSecretInput || null, pollIntervalMs
  };

  try {
    if (editingKbRepo) {
      body.version = currentKbRepo.version;
      // 留空时保留原 secret（仅在 mask 表示已存在时）
      if (!authSecretInput && currentKbRepo.authSecretMasked) {
        // 与后端协议：null 会被当作清空。这里把字段去掉模拟"保留"语义
        // 后端 update 当前实现：传入 null 也会保存为 null。给出明显提示
        // MVP：让用户必须重输，避免误清空
        if (authType !== 'NONE') {
          alert('编辑模式下请重新输入凭据以避免清空（或将凭据类型改为 NONE）');
          return;
        }
        body.authSecret = null;
      }
      await authFetch(`${KB_REPO_API}/${currentKbRepo.id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      });
    } else {
      await authFetch(KB_REPO_API, {
        method: 'POST',
        body: JSON.stringify(body),
      });
    }
    closeKbModal();
    await loadKbStatus();
  } catch (e) {
    alert('保存失败：' + e.message);
  }
}

async function triggerKbSync() {
  if (!currentKbRepo) return;
  if (currentKbRepo.syncStatus === 'SYNCING') { alert('已在同步中，请稍后'); return; }
  try {
    await authFetch(`${KB_REPO_API}/${currentKbRepo.id}/sync`, { method: 'POST' });
    // 状态会很快变 SYNCING，刷新展示
    await loadKbStatus();
    // 简单轮询 10s 内查看状态变化
    setTimeout(loadKbStatus, 3000);
    setTimeout(loadKbStatus, 10000);
  } catch (e) {
    alert('触发同步失败：' + e.message);
  }
}

async function deleteKbRepo() {
  if (!currentKbRepo) return;
  if (!confirm(`确定删除知识库仓库「${currentKbRepo.name}」？本地工作区会被一并清理。`)) return;
  try {
    await authFetch(`${KB_REPO_API}/${currentKbRepo.id}`, { method: 'DELETE' });
    await loadKbStatus();
  } catch (e) {
    alert('删除失败：' + e.message);
  }
}

// 暴露给 HTML inline handler
window.loadKbStatus = loadKbStatus;
window.openKbConfigModal = openKbConfigModal;
window.closeKbModal = closeKbModal;
window.submitKbConfig = submitKbConfig;
window.triggerKbSync = triggerKbSync;
window.deleteKbRepo = deleteKbRepo;
