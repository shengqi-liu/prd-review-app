/**
 * PRD 文件上传模块(#7 add-document-parsing)。
 *
 * 用 FormData + fetch 调 `POST /api/v1/prds/from-file`(multipart/form-data),
 * 后端用 Tika 解析 → AI 摘要 → 落 DRAFT。
 *
 * 注意:不能用 authFetch,因为它强制 Content-Type=application/json。
 * multipart 必须让浏览器自动加 boundary,不能手动设 Content-Type。
 */

const MAX_FILE_SIZE_MB = 10;

async function uploadPrdFile(file) {
  if (!file) return;

  // 客户端先校验大小,避免上传后被后端拒
  if (file.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
    alert(`文件过大: ${(file.size / 1024 / 1024).toFixed(2)}MB,最大 ${MAX_FILE_SIZE_MB}MB`);
    document.getElementById('prd-upload-input').value = '';
    return;
  }

  const formData = new FormData();
  formData.append('file', file);

  const token = getToken();
  if (!token) {
    alert('请先登录');
    window.location.href = 'login.html';
    return;
  }

  // 简单的"上传中"提示
  const uploadBtn = document.querySelector('button[onclick*="prd-upload-input"]');
  const originalHtml = uploadBtn ? uploadBtn.innerHTML : '';
  if (uploadBtn) {
    uploadBtn.disabled = true;
    uploadBtn.innerHTML = '<span>解析中,约 30 秒...</span>';
  }

  try {
    const resp = await fetch(`${API_BASE}/api/v1/prds/from-file`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` },  // 不设 Content-Type
      body: formData,
    });
    const json = await resp.json();

    if (json.code === 20001 || json.code === 20004) {
      logout();
      throw new Error('登录已过期');
    }
    if (json.code !== 0) {
      throw new Error(json.message || '上传失败');
    }

    const prd = json.data;
    // 上传成功后跳编辑页,让用户预览 AI 摘要 + 补章节 + 提交评审
    alert(`✅ 上传成功,已生成草稿 #${prd.id},正在跳转到编辑器…`);
    location.hash = '#edit?id=' + prd.id;
  } catch (e) {
    alert('上传失败: ' + e.message);
  } finally {
    if (uploadBtn) {
      uploadBtn.disabled = false;
      uploadBtn.innerHTML = originalHtml;
    }
    document.getElementById('prd-upload-input').value = '';  // 允许同一文件再次选择
  }
}

window.uploadPrdFile = uploadPrdFile;
