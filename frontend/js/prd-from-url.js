/**
 * 从 URL 导入 PRD(#5 from-url SSE 流式)。
 *
 * 后端 POST /api/v1/prds/from-url 是 SSE 流式响应,推送 fetching/summarizing/done/error 4 类事件:
 *   data: {"stage":"fetching","message":"正在读取文档..."}
 *   data: {"stage":"summarizing","message":"AI 正在分析内容..."}
 *   data: {"stage":"done","prd":{...PrdResponse}}
 *   data: {"stage":"error","message":"读取失败"}
 *
 * 前端用 fetch + ReadableStream 读 SSE,展示阶段进度,done 后跳编辑页。
 */

function openImportFromUrl() {
  document.getElementById('prd-url-input').value = '';
  document.getElementById('prd-url-progress').style.display = 'none';
  document.getElementById('prd-url-stage').textContent = '准备就绪…';
  document.getElementById('prd-url-submit').disabled = false;
  document.getElementById('prd-url-modal').classList.add('open');
}

function closeImportFromUrl() {
  document.getElementById('prd-url-modal').classList.remove('open');
}

async function submitImportFromUrl() {
  const url = document.getElementById('prd-url-input').value.trim();
  if (!url) { alert('请输入 URL'); return; }
  if (!/^https?:\/\//i.test(url)) { alert('URL 必须以 http:// 或 https:// 开头'); return; }

  const stageEl = document.getElementById('prd-url-stage');
  const submitBtn = document.getElementById('prd-url-submit');
  document.getElementById('prd-url-progress').style.display = '';
  submitBtn.disabled = true;
  stageEl.textContent = '发起请求…';

  const token = getToken();
  if (!token) { alert('请先登录'); window.location.href = 'login.html'; return; }

  try {
    const resp = await fetch(`${API_BASE}/api/v1/prds/from-url`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify({ sourceUrl: url }),
    });

    if (!resp.ok) {
      // 同步阶段抛 BizException 会返回 JSON
      let errMsg = `HTTP ${resp.status}`;
      try {
        const j = await resp.json();
        errMsg = j.message || errMsg;
      } catch (_) {}
      throw new Error(errMsg);
    }

    // 读 SSE
    const reader = resp.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let finalPrd = null;
    let errorMsg = null;

    outer: while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      let sepIdx;
      while ((sepIdx = buffer.indexOf('\n\n')) >= 0) {
        const rawEvent = buffer.slice(0, sepIdx);
        buffer = buffer.slice(sepIdx + 2);
        const evt = parseSseEvent(rawEvent);
        if (!evt) continue;
        switch (evt.stage) {
          case 'fetching':
            stageEl.textContent = '📥 ' + (evt.message || '正在读取文档…');
            break;
          case 'summarizing':
            stageEl.textContent = '🤖 ' + (evt.message || 'AI 正在分析内容…');
            break;
          case 'done':
            finalPrd = evt.data || evt.prd;
            try { reader.cancel(); } catch (_) {}
            break outer;
          case 'error':
            errorMsg = evt.message || '导入失败';
            try { reader.cancel(); } catch (_) {}
            break outer;
        }
      }
    }

    if (errorMsg) throw new Error(errorMsg);
    if (!finalPrd || !finalPrd.id) throw new Error('未收到完成事件');

    stageEl.textContent = '✓ 完成,正在跳转编辑器…';
    setTimeout(() => {
      closeImportFromUrl();
      location.hash = '#edit?id=' + finalPrd.id;
    }, 500);
  } catch (e) {
    stageEl.textContent = '✗ 失败:' + e.message;
    submitBtn.disabled = false;
  }
}

/** 解析单个 SSE 事件块(data: <json>) */
function parseSseEvent(raw) {
  const lines = raw.split('\n');
  for (const line of lines) {
    if (!line.startsWith('data:')) continue;
    const payload = line.slice(5).trim();
    if (!payload) continue;
    try {
      return JSON.parse(payload);
    } catch (_) {
      continue;
    }
  }
  return null;
}

window.openImportFromUrl = openImportFromUrl;
window.closeImportFromUrl = closeImportFromUrl;
window.submitImportFromUrl = submitImportFromUrl;
