/**
 * 认证工具模块
 * - 统一管理 JWT token 的存取/清除
 * - 提供带 Bearer token 的 fetch 封装
 * - 登录态守卫
 */

const API_BASE = 'http://localhost:8080';
const TOKEN_KEY = 'prd_review_token';
const USER_KEY  = 'prd_review_user';

/** 保存登录信息 */
function saveAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** 获取 token */
function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

/** 获取当前用户 */
function getCurrentUser() {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) : null;
}

/** 清除登录态并跳回登录页 */
function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  window.location.href = 'login.html';
}

/** 守卫：未登录则跳转登录页 */
function requireAuth() {
  if (!getToken()) {
    window.location.href = 'login.html';
    return false;
  }
  return true;
}

/**
 * 带鉴权的 fetch 封装
 * - 自动附加 Authorization Bearer token
 * - 自动处理 401/20001 跳转登录
 * - 返回 data 字段，异常时 throw Error
 */
async function authFetch(path, options = {}) {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const resp = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const json = await resp.json();

  if (json.code === 20001 || json.code === 20004) {
    logout();
    throw new Error('登录已过期，请重新登录');
  }
  if (json.code !== 0) {
    throw new Error(json.message || '请求失败');
  }
  return json.data;
}

/** 登录接口 */
async function login(username, password) {
  const resp = await fetch(`${API_BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json = await resp.json();
  if (json.code !== 0) throw new Error(json.message || '登录失败');
  return json.data; // { accessToken, tokenType, expiresIn }
}

