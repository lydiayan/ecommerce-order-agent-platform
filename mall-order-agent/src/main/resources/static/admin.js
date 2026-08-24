let csrf;
let selectedUser;
let selectedIdentity;
let impersonationEnabled = false;
let selectedBadCase;

const BAD_CASE_REASONS = {
  INCORRECT: '回答错误', IRRELEVANT: '答非所问', INCOMPLETE: '信息不完整',
  TOOL_FAILURE: '工具执行失败', HARD_TO_UNDERSTAND: '难以理解',
  SAFETY_RISK: '存在安全风险', OTHER: '其他',
};

const BAD_CASE_TRANSITIONS = {
  NEW: ['NEW', 'TRIAGED', 'IGNORED'],
  TRIAGED: ['TRIAGED', 'IN_PROGRESS', 'IGNORED'],
  IN_PROGRESS: ['IN_PROGRESS', 'TRIAGED', 'RESOLVED', 'IGNORED'],
  RESOLVED: ['RESOLVED', 'IN_PROGRESS'],
  IGNORED: ['IGNORED', 'NEW'],
};

async function ensureCsrf() {
  if (csrf) return csrf;
  const response = await fetch('/auth/csrf');
  const body = await response.json();
  csrf = body.data;
  return csrf;
}

async function api(path, options = {}) {
  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (options.method && options.method !== 'GET') {
    const token = await ensureCsrf();
    headers[token.headerName] = token.token;
    if (options.body) headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    window.location.replace('/login.html');
    throw new Error('登录已失效');
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || body.detail || `请求失败（HTTP ${response.status}）`);
  return body.data;
}

const text = (value) => value == null || value === '' ? '—' : String(value);
const date = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';

function button(label, action, danger = false) {
  const element = document.createElement('button');
  element.type = 'button';
  element.className = danger ? 'link-button danger-link' : 'link-button';
  element.textContent = label;
  element.addEventListener('click', action);
  return element;
}

async function loadUsers() {
  const users = await api('/admin/users');
  const tbody = document.getElementById('userRows');
  tbody.replaceChildren();
  users.forEach((user) => {
    const tr = document.createElement('tr');
    [user.username, `${text(user.displayName)} · ${text(user.actorUserId)}`,
      [...user.roles].join('、'), user.enabled ? (user.lockedUntil ? '已锁定' : '启用') : '停用',
      date(user.lastLoginAt)].forEach((value) => {
      const td = document.createElement('td'); td.textContent = value; tr.appendChild(td);
    });
    const actions = document.createElement('td');
    actions.append(
      button(user.enabled ? '停用' : '启用', () => setEnabled(user, !user.enabled), user.enabled),
      button('解锁', () => unlock(user)), button('重置密码', () => openPasswordReset(user)),
    );
    if (impersonationEnabled && user.actorUserId && user.enabled) {
      actions.append(button('进入身份', () => openImpersonation(user)));
    }
    tr.appendChild(actions); tbody.appendChild(tr);
  });
}

async function loadIdentities() {
  const identities = await api('/admin/identities');
  const select = document.getElementById('newIdentity');
  select.replaceChildren();
  identities.forEach((identity) => {
    const option = document.createElement('option');
    option.value = identity.actorUserId;
    option.textContent = `${identity.displayName} · ${identity.category} · ${identity.actorUserId}`;
    select.appendChild(option);
  });
}

async function setEnabled(user, enabled) {
  await api(`/admin/users/${user.id}/enabled`, { method: 'POST', body: JSON.stringify({ enabled }) });
  document.getElementById('userNotice').textContent = enabled ? '账户已启用' : '账户已停用，原会话将失效';
  await loadUsers();
}

async function unlock(user) {
  await api(`/admin/users/${user.id}/unlock`, { method: 'POST' });
  document.getElementById('userNotice').textContent = '账户已解锁，原会话将失效';
  await loadUsers();
}

function openPasswordReset(user) {
  selectedUser = user;
  document.getElementById('resetTarget').textContent = user.username;
  document.getElementById('resetPassword').value = '';
  document.getElementById('resetNotice').textContent = '';
  document.getElementById('passwordDialog').showModal();
}

function openImpersonation(user) {
  selectedIdentity = user;
  document.getElementById('impersonationTarget').textContent = `${user.displayName} · ${user.actorUserId}`;
  document.getElementById('impersonationPassword').value = '';
  document.getElementById('impersonationReason').value = '';
  document.getElementById('impersonationNotice').textContent = '';
  document.getElementById('impersonationDialog').showModal();
}

async function loadTokens() {
  const tokens = await api('/admin/tokens');
  const tbody = document.getElementById('tokenRows');
  tbody.replaceChildren();
  tokens.forEach((token) => {
    const tr = document.createElement('tr');
    [token.name, token.prefix, token.scopes, date(token.expiresAt), date(token.lastUsedAt)].forEach((value) => {
      const td = document.createElement('td'); td.textContent = value; tr.appendChild(td);
    });
    const actions = document.createElement('td');
    if (token.enabled && !token.revokedAt) actions.append(button('撤销', () => revokeToken(token), true));
    else actions.textContent = '已撤销';
    tr.appendChild(actions); tbody.appendChild(tr);
  });
}

async function revokeToken(token) {
  await api(`/admin/tokens/${token.id}/revoke`, { method: 'POST' });
  document.getElementById('tokenNotice').textContent = '令牌已撤销';
  await loadTokens();
}

async function loadAudit() {
  const events = await api('/admin/audit?limit=100');
  const tbody = document.getElementById('auditRows');
  tbody.replaceChildren();
  events.forEach((event) => {
    const tr = document.createElement('tr');
    [date(event.createdAt), event.eventType, text(event.subjectFingerprint),
      text(event.resourceFingerprint), event.outcome, text(event.sourceIp)].forEach((value) => {
      const td = document.createElement('td'); td.textContent = value; tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
}

function percent(value) {
  return Number.isFinite(Number(value)) ? `${(Number(value) * 100).toFixed(1)}%` : '—';
}

async function loadBadCaseMetrics() {
  const metrics = await api('/admin/bad-cases/metrics?days=30');
  document.getElementById('metricResponses').textContent = metrics.responseCount;
  document.getElementById('metricFeedback').textContent = metrics.feedbackCount;
  document.getElementById('metricParticipation').textContent = percent(metrics.participationRate);
  document.getElementById('metricDownRate').textContent = percent(metrics.downRate);
  document.getElementById('metricResolved').textContent = metrics.resolvedCount;
}

function reasonText(reasons) {
  if (!reasons?.length) return '未填写';
  return reasons.map((reason) => BAD_CASE_REASONS[reason] || reason).join('、');
}

async function loadBadCases() {
  const query = new URLSearchParams();
  const status = document.getElementById('badCaseStatusFilter').value;
  const reason = document.getElementById('badCaseReasonFilter').value;
  const strategy = document.getElementById('badCaseStrategyFilter').value.trim();
  const modelName = document.getElementById('badCaseModelFilter').value.trim();
  const agentVersion = document.getElementById('badCaseVersionFilter').value.trim();
  const from = document.getElementById('badCaseFromFilter').value;
  const to = document.getElementById('badCaseToFilter').value;
  if (status) query.set('status', status);
  if (reason) query.set('reason', reason);
  if (strategy) query.set('strategy', strategy);
  if (modelName) query.set('modelName', modelName);
  if (agentVersion) query.set('agentVersion', agentVersion);
  if (from) query.set('from', from);
  if (to) query.set('to', to);
  const rows = await api(`/admin/bad-cases?${query}`);
  const tbody = document.getElementById('badCaseRows');
  tbody.replaceChildren();
  rows.forEach((item) => {
    const tr = document.createElement('tr');
    [date(item.updatedAt), item.status, item.priority, reasonText(item.reasons),
      `${text(item.planStrategy)} / ${text(item.modelName)}`, text(item.ownerUsername)].forEach((value) => {
      const td = document.createElement('td'); td.textContent = value; tr.appendChild(td);
    });
    const actions = document.createElement('td');
    actions.append(button('查看', () => openBadCase(item.id)));
    tr.appendChild(actions); tbody.appendChild(tr);
  });
  if (!rows.length) {
    const tr = document.createElement('tr');
    const td = document.createElement('td'); td.colSpan = 7; td.className = 'empty-table'; td.textContent = '暂无数据';
    tr.appendChild(td); tbody.appendChild(tr);
  }
}

function populateBadCaseStatus(current) {
  const select = document.getElementById('badCaseStatus');
  select.replaceChildren();
  (BAD_CASE_TRANSITIONS[current] || [current]).forEach((status) => {
    const option = document.createElement('option'); option.value = status; option.textContent = status;
    option.selected = status === current; select.appendChild(option);
  });
}

async function openBadCase(id) {
  selectedBadCase = await api(`/admin/bad-cases/${id}`);
  document.getElementById('badCaseId').textContent = `#${selectedBadCase.id}`;
  document.getElementById('badCaseMeta').textContent = [selectedBadCase.priority, selectedBadCase.planStrategy,
    selectedBadCase.modelName, date(selectedBadCase.createdAt)].filter(Boolean).join(' · ');
  document.getElementById('badCaseQuery').textContent = text(selectedBadCase.query);
  document.getElementById('badCaseAnswer').textContent = text(selectedBadCase.answer);
  document.getElementById('badCaseToolSummary').textContent = text(selectedBadCase.toolSummary);
  document.getElementById('badCaseFeedback').textContent = [reasonText(selectedBadCase.reasons),
    selectedBadCase.comment].filter(Boolean).join(' · ');
  populateBadCaseStatus(selectedBadCase.status);
  document.getElementById('badCaseCategory').value = selectedBadCase.category || '';
  document.getElementById('badCaseOwner').value = selectedBadCase.ownerUsername || '';
  document.getElementById('badCaseFixVersion').value = selectedBadCase.fixVersion || '';
  document.getElementById('badCaseRootCause').value = selectedBadCase.rootCause || '';
  document.getElementById('badCaseResolution').value = selectedBadCase.resolution || '';
  document.getElementById('copyTrace').disabled = !selectedBadCase.traceId;
  document.getElementById('badCaseDialogNotice').textContent = '';
  document.getElementById('badCaseDialog').showModal();
}

document.querySelectorAll('.tab').forEach((tab) => tab.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach((item) => item.classList.toggle('active', item === tab));
  ['users', 'tokens', 'badCases', 'audit'].forEach((name) => {
    document.getElementById(`${name}Panel`).classList.toggle('hidden', tab.dataset.tab !== name);
  });
  if (tab.dataset.tab === 'tokens') loadTokens().catch(showError);
  if (tab.dataset.tab === 'badCases') Promise.all([loadBadCases(), loadBadCaseMetrics()]).catch(showError);
  if (tab.dataset.tab === 'audit') loadAudit().catch(showError);
}));

document.getElementById('openCreateUser').addEventListener('click', () => document.getElementById('createUserForm').classList.remove('hidden'));
document.getElementById('cancelCreateUser').addEventListener('click', () => document.getElementById('createUserForm').classList.add('hidden'));
document.getElementById('createUserForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api('/admin/users', { method: 'POST', body: JSON.stringify({
      username: document.getElementById('newUsername').value.trim(),
      actorUserId: document.getElementById('newIdentity').value,
      temporaryPassword: document.getElementById('newPassword').value,
    }) });
    event.target.reset(); event.target.classList.add('hidden');
    document.getElementById('userNotice').textContent = '账户已创建，首次登录必须修改密码';
    await loadUsers();
  } catch (error) { document.getElementById('userNotice').textContent = error.message; }
});

document.getElementById('resetPasswordForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api(`/admin/users/${selectedUser.id}/reset-password`, { method: 'POST', body: JSON.stringify({
      temporaryPassword: document.getElementById('resetPassword').value,
    }) });
    document.getElementById('passwordDialog').close();
    document.getElementById('userNotice').textContent = '临时密码已设置，原会话将失效';
    await loadUsers();
  } catch (error) { document.getElementById('resetNotice').textContent = error.message; }
});
document.getElementById('cancelReset').addEventListener('click', () => document.getElementById('passwordDialog').close());
document.getElementById('cancelImpersonation').addEventListener('click', () => document.getElementById('impersonationDialog').close());
document.getElementById('impersonationForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const result = await api('/admin/impersonation', { method: 'POST', body: JSON.stringify({
      actorUserId: selectedIdentity.actorUserId,
      adminPassword: document.getElementById('impersonationPassword').value,
      reason: document.getElementById('impersonationReason').value.trim(),
    }) });
    window.location.replace(result.redirect || '/');
  } catch (error) { document.getElementById('impersonationNotice').textContent = error.message; }
});

document.getElementById('openCreateToken').addEventListener('click', () => document.getElementById('createTokenForm').classList.remove('hidden'));
document.getElementById('cancelCreateToken').addEventListener('click', () => document.getElementById('createTokenForm').classList.add('hidden'));
document.getElementById('createTokenForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const result = await api('/admin/tokens', { method: 'POST', body: JSON.stringify({
      name: document.getElementById('tokenName').value.trim(), scope: 'EVALUATION_ACT_AS',
      validDays: Number(document.getElementById('tokenDays').value),
    }) });
    document.getElementById('rawToken').textContent = result.token;
    document.getElementById('oneTimeToken').classList.remove('hidden');
    event.target.reset(); event.target.classList.add('hidden');
    await loadTokens();
  } catch (error) { document.getElementById('tokenNotice').textContent = error.message; }
});
document.getElementById('copyToken').addEventListener('click', async () => navigator.clipboard.writeText(document.getElementById('rawToken').textContent));
document.getElementById('refreshAudit').addEventListener('click', () => loadAudit().catch(showError));
document.getElementById('refreshBadCases').addEventListener('click', () => Promise.all([
  loadBadCases(), loadBadCaseMetrics(),
]).catch(showError));
document.getElementById('badCaseFilters').addEventListener('submit', (event) => {
  event.preventDefault(); loadBadCases().catch(showError);
});
document.getElementById('closeBadCase').addEventListener('click', () => document.getElementById('badCaseDialog').close());
document.getElementById('copyTrace').addEventListener('click', async () => {
  if (selectedBadCase?.traceId) await navigator.clipboard.writeText(selectedBadCase.traceId);
  document.getElementById('badCaseDialogNotice').textContent = 'Trace ID 已复制';
});
document.getElementById('badCaseForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    selectedBadCase = await api(`/admin/bad-cases/${selectedBadCase.id}`, {
      method: 'POST', body: JSON.stringify({
        status: document.getElementById('badCaseStatus').value,
        category: document.getElementById('badCaseCategory').value.trim(),
        ownerUsername: document.getElementById('badCaseOwner').value.trim(),
        rootCause: document.getElementById('badCaseRootCause').value.trim(),
        resolution: document.getElementById('badCaseResolution').value.trim(),
        fixVersion: document.getElementById('badCaseFixVersion').value.trim(),
      }),
    });
    document.getElementById('badCaseDialog').close();
    document.getElementById('badCaseNotice').textContent = `Bad Case #${selectedBadCase.id} 已更新`;
    await Promise.all([loadBadCases(), loadBadCaseMetrics()]);
  } catch (error) { document.getElementById('badCaseDialogNotice').textContent = error.message; }
});
document.getElementById('logoutButton').addEventListener('click', async () => {
  await api('/auth/logout', { method: 'POST' }); window.location.replace('/login.html');
});

function showError(error) {
  const visible = [...document.querySelectorAll('.admin-section')].find((panel) => !panel.classList.contains('hidden'));
  const notice = visible?.querySelector('.notice');
  if (notice) notice.textContent = error.message;
}

async function init() {
  try {
    await ensureCsrf();
    const me = await api('/auth/me');
    impersonationEnabled = Boolean(me.demoImpersonationEnabled);
    document.getElementById('adminName').textContent = me.displayName || me.username;
    await Promise.all([loadUsers(), loadIdentities()]);
  } catch (error) { showError(error); }
}

init();
