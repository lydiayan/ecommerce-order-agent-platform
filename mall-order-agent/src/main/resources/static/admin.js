let csrf;
let selectedUser;
let selectedIdentity;
let impersonationEnabled = false;
let selectedBadCase;
let knowledgeCatalog = [];
let selectedKnowledgeDocument;
let pendingKnowledgeFile;
let knowledgePreview;

const MAX_RENDERED_CHUNKS = 500;

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

async function api(path, options = {}, retryCsrf = true) {
  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (options.method && options.method !== 'GET') {
    const token = await ensureCsrf();
    headers[token.headerName] = token.token;
    if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    window.location.replace('/login.html');
    throw new Error('登录已失效');
  }
  const body = await response.json().catch(() => ({}));
  if (response.status === 403 && retryCsrf && options.method && options.method !== 'GET') {
    csrf = undefined;
    return api(path, options, false);
  }
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

function knowledgeMetadataLine(metadata, mode) {
  const values = [metadata?.department, metadata?.role, metadata?.version && `v${metadata.version}`]
    .filter(Boolean).join(' · ');
  return `${values || '未配置元数据'} · ${mode}`;
}

function knowledgeStatusText(status) {
  return ({ READY: '已入库', IMPORTING: '入库中', FAILED: '导入失败',
    MILVUS_ONLY: '仅 Milvus', NOT_IMPORTED: '未入库', PREVIEW: '待确认预览' })[status] || status || '未入库';
}

function fileSizeText(bytes) {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value <= 0) return '—';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(2)} MB`;
}

function setKnowledgeRecordSummary(summary) {
  const hash = summary?.fileSha256 || '';
  const hashNode = document.getElementById('knowledgeRecordFileHash');
  document.getElementById('knowledgeRecordStatus').textContent = knowledgeStatusText(summary?.importStatus);
  document.getElementById('knowledgeRecordDocumentId').textContent = text(summary?.documentId);
  document.getElementById('knowledgeRecordFileSize').textContent = fileSizeText(summary?.originalFileSize);
  hashNode.textContent = hash ? `${hash.slice(0, 12)}…${hash.slice(-8)}` : '—';
  hashNode.title = hash;
  document.getElementById('knowledgeRecordImportedAt').textContent = date(summary?.importedAt);
  document.getElementById('knowledgeChunkCount').textContent = summary?.chunkCount || 0;
  document.getElementById('knowledgeAverageTokens').textContent = summary?.averageTokenCount || 0;
  document.getElementById('knowledgeMaxTokens').textContent = summary?.maxTokenCount || 0;
  document.getElementById('knowledgeOverlapTokens').textContent = summary?.overlapTokenCount || 0;
}

function setKnowledgeBusy(busy, message = '') {
  document.getElementById('previewKnowledgeChunks').disabled = busy || !pendingKnowledgeFile;
  document.getElementById('confirmKnowledgeImport').disabled = busy || !pendingKnowledgeFile || !knowledgePreview;
  document.getElementById('uploadKnowledgeVersion').disabled = busy;
  document.getElementById('addKnowledgeFile').disabled = busy;
  ['knowledgeDepartment', 'knowledgeRole', 'knowledgeVersion'].forEach((id) => {
    document.getElementById(id).disabled = busy || !pendingKnowledgeFile;
  });
  if (message) document.getElementById('knowledgeNotice').textContent = message;
}

function setKnowledgeMetadataInputs(metadata) {
  document.getElementById('knowledgeDepartment').value = metadata?.department || 'default';
  const role = document.getElementById('knowledgeRole');
  const roleValue = metadata?.role || 'public';
  if (![...role.options].some((option) => option.value === roleValue)) {
    const option = document.createElement('option'); option.value = roleValue; option.textContent = roleValue;
    role.appendChild(option);
  }
  role.value = roleValue;
  document.getElementById('knowledgeVersion').value = metadata?.version || '1.0';
}

function renderKnowledgeCatalog() {
  const list = document.getElementById('knowledgeDocumentList');
  list.replaceChildren();
  knowledgeCatalog.forEach((summary) => {
    const control = window.document.createElement('button');
    control.type = 'button';
    control.className = 'knowledge-document';
    control.classList.toggle('active', selectedKnowledgeDocument?.filename === summary.filename);
    const name = window.document.createElement('strong');
    name.textContent = summary.filename;
    const meta = window.document.createElement('span');
    meta.textContent = `${summary.chunkCount} Chunks · v${summary.metadata?.version || '—'} · ${knowledgeStatusText(summary.importStatus)}`;
    control.append(name, meta);
    control.addEventListener('click', () => selectKnowledgeDocument(summary).catch(showError));
    list.appendChild(control);
  });
}

async function loadKnowledgeCatalog(preferredFilename) {
  document.getElementById('knowledgeNotice').textContent = '正在读取知识库目录…';
  knowledgeCatalog = await api('/vector/milvus/documents/catalog');
  const selected = knowledgeCatalog.find((item) => item.filename === preferredFilename)
    || knowledgeCatalog.find((item) => item.filename === selectedKnowledgeDocument?.filename)
    || knowledgeCatalog[0];
  selectedKnowledgeDocument = selected;
  renderKnowledgeCatalog();
  if (selected) await selectKnowledgeDocument(selected);
  else {
    document.getElementById('knowledgeNotice').textContent = '知识库目录为空，可上传 PDF 新建文档';
  }
}

async function selectKnowledgeDocument(document) {
  selectedKnowledgeDocument = document;
  pendingKnowledgeFile = undefined;
  knowledgePreview = undefined;
  renderKnowledgeCatalog();
  document.getElementById('knowledgeFilename').textContent = document.filename;
  document.getElementById('knowledgeMetadata').textContent = knowledgeMetadataLine(document.metadata, '读取已入库结果');
  setKnowledgeRecordSummary(document);
  setKnowledgeMetadataInputs(document.metadata);
  document.getElementById('knowledgeStrategy').value = document.strategy || 'CONTENT_TYPE_AWARE';
  setKnowledgeBusy(true, '正在读取 Milvus 中的 Chunk…');
  try {
    const query = new URLSearchParams({ source: document.filename });
    knowledgePreview = await api(`/vector/milvus/documents/chunks?${query}`);
    renderKnowledgeChunks(knowledgePreview);
    if (document.importStatus === 'FAILED') {
      document.getElementById('knowledgeNotice').textContent = `最近一次导入失败：${document.lastError || '未记录原因'}`;
    } else if (document.importStatus === 'READY' && !knowledgePreview.chunkCount) {
      document.getElementById('knowledgeNotice').textContent = 'MySQL 显示已入库，但 Milvus 未找到对应 Chunk，请重新导入';
    } else {
      document.getElementById('knowledgeNotice').textContent = knowledgePreview.chunkCount
        ? '当前显示 Milvus 中已入库的真实 Chunk' : '该文档尚未入库，可上传 PDF 预览';
    }
  } finally {
    knowledgePreview = undefined;
    setKnowledgeBusy(false);
  }
}

function knowledgeFormData() {
  const department = document.getElementById('knowledgeDepartment').value.trim();
  const role = document.getElementById('knowledgeRole').value;
  const version = document.getElementById('knowledgeVersion').value.trim();
  if (!department || !role || !version) throw new Error('请填写部门、可见角色和版本');
  const form = new FormData();
  form.append('file', pendingKnowledgeFile);
  form.append('strategy', document.getElementById('knowledgeStrategy').value);
  if (selectedKnowledgeDocument?.documentId) form.append('documentId', selectedKnowledgeDocument.documentId);
  form.append('department', department);
  form.append('role', role);
  form.append('version', version);
  return form;
}

async function previewKnowledgeFile() {
  if (!pendingKnowledgeFile) return;
  knowledgePreview = undefined;
  setKnowledgeBusy(true, '正在解析 PDF 并切分预览…');
  try {
    knowledgePreview = await api('/vector/milvus/documents/preview/pdf', {
      method: 'POST', body: knowledgeFormData(),
    });
    selectedKnowledgeDocument = {
      ...selectedKnowledgeDocument,
      filename: knowledgePreview.filename,
      documentId: knowledgePreview.documentId,
      metadata: knowledgePreview.metadata,
      strategy: knowledgePreview.strategy,
      contentType: knowledgePreview.contentType,
      chunkCount: knowledgePreview.chunkCount,
      averageTokenCount: knowledgePreview.averageTokenCount,
      maxTokenCount: knowledgePreview.maxTokenCount,
      overlapTokenCount: knowledgePreview.overlapTokens,
      originalFileSize: pendingKnowledgeFile.size,
      fileSha256: '', importStatus: 'PREVIEW', importedAt: null,
    };
    document.getElementById('knowledgeFilename').textContent = knowledgePreview.filename;
    document.getElementById('knowledgeMetadata').textContent = knowledgeMetadataLine(
      knowledgePreview.metadata, '待确认预览');
    setKnowledgeMetadataInputs(knowledgePreview.metadata);
    setKnowledgeRecordSummary(selectedKnowledgeDocument);
    renderKnowledgeChunks(knowledgePreview);
    document.getElementById('knowledgeNotice').textContent = '预览完成，尚未写入 Milvus';
  } finally {
    setKnowledgeBusy(false);
  }
}

async function chooseKnowledgeFile(file) {
  if (!file) return;
  if (!file.name.toLowerCase().endsWith('.pdf')) throw new Error('请选择 PDF 文件');
  if (file.size > 20 * 1024 * 1024) throw new Error('PDF 文件不能超过 20 MB');
  pendingKnowledgeFile = file;
  knowledgePreview = undefined;
  const existing = knowledgeCatalog.find((item) => item.filename === file.name);
  selectedKnowledgeDocument = existing || {
    filename: file.name,
    metadata: { department: 'default', role: 'public', version: '1.0' },
    strategy: 'CONTENT_TYPE_AWARE', contentType: 'PDF', chunkCount: 0,
    averageTokenCount: 0, maxTokenCount: 0, overlapTokenCount: 0,
    originalFileSize: file.size, importStatus: 'PREVIEW',
  };
  renderKnowledgeCatalog();
  document.getElementById('knowledgeFilename').textContent = file.name;
  document.getElementById('knowledgeMetadata').textContent = knowledgeMetadataLine(
    selectedKnowledgeDocument.metadata, '新版本待预览');
  setKnowledgeMetadataInputs(selectedKnowledgeDocument.metadata);
  setKnowledgeRecordSummary(selectedKnowledgeDocument);
  document.getElementById('knowledgeStrategy').value = selectedKnowledgeDocument.strategy || 'CONTENT_TYPE_AWARE';
  await previewKnowledgeFile();
}

async function importKnowledgeFile() {
  if (!pendingKnowledgeFile || !knowledgePreview) return;
  const filename = pendingKnowledgeFile.name;
  setKnowledgeBusy(true, '正在生成 Embedding 并写入 Milvus…');
  try {
    const result = await api('/vector/milvus/documents/pdf', {
      method: 'POST', body: knowledgeFormData(),
    });
    pendingKnowledgeFile = undefined;
    knowledgePreview = undefined;
    document.getElementById('knowledgeNotice').textContent = `已入库 ${result.chunkCount} 个 Chunk`;
    await loadKnowledgeCatalog(filename);
  } finally {
    setKnowledgeBusy(false);
  }
}

function chunkTitle(chunk) {
  if (chunk.titlePath) return chunk.titlePath.split(' > ').at(-1) || chunk.titlePath;
  const firstLine = chunk.content?.split(/\r?\n/).map((line) => line.trim()).find(Boolean) || '无标题 Chunk';
  return firstLine.length > 52 ? `${firstLine.slice(0, 52)}…` : firstLine;
}

function chunkIndexLabel(chunk) {
  if (chunk.chunkLevel === 'PARENT') return `P${chunk.chunkIndex}`;
  if (chunk.chunkLevel === 'CHILD') return `C${chunk.chunkIndex}`;
  return `#${chunk.chunkIndex}`;
}

function detailField(label, value) {
  const field = document.createElement('div');
  const name = document.createElement('span'); name.textContent = label;
  const data = document.createElement('strong'); data.textContent = text(value);
  field.append(name, data);
  return field;
}

function appendChunkCopy(copy, chunk, previous) {
  const overlapLength = previous && chunk.startOffset < previous.endOffset
    ? Math.min(chunk.content.length, previous.endOffset - chunk.startOffset) : 0;
  if (overlapLength > 0 && chunk.chunkLevel !== 'PARENT') {
    const mark = document.createElement('mark');
    mark.textContent = chunk.content.slice(0, overlapLength);
    copy.append(mark, document.createTextNode(chunk.content.slice(overlapLength)));
  } else {
    copy.textContent = chunk.content;
  }
}

function createKnowledgeChunk(chunk, previous) {
  const row = document.createElement('article');
  row.className = 'knowledge-chunk';
  if (chunk.chunkLevel === 'PARENT') row.classList.add('parent');
  if (chunk.chunkLevel === 'CHILD') row.classList.add('child');

  const head = document.createElement('div'); head.className = 'knowledge-chunk-head';
  const index = document.createElement('span'); index.className = 'knowledge-chunk-index'; index.textContent = chunkIndexLabel(chunk);
  const title = document.createElement('div'); title.className = 'knowledge-chunk-title';
  const titleText = document.createElement('strong'); titleText.textContent = chunkTitle(chunk);
  const kind = document.createElement('span'); kind.textContent = `${chunk.strategy} · ${chunk.chunkLevel}`;
  title.append(titleText, kind);
  const metrics = document.createElement('span'); metrics.className = 'knowledge-chunk-metrics';
  metrics.textContent = `${chunk.tokenCount} Token · ${chunk.startOffset}–${chunk.endOffset}`;
  const toggle = document.createElement('button'); toggle.type = 'button'; toggle.className = 'knowledge-detail-toggle';
  toggle.textContent = '⌄'; toggle.setAttribute('aria-label', `展开 Chunk ${chunk.chunkIndex} 详情`);
  toggle.setAttribute('aria-expanded', 'false');
  head.append(index, title, metrics, toggle);

  const body = document.createElement('div'); body.className = 'knowledge-chunk-body';
  const copy = document.createElement('p'); copy.className = 'knowledge-chunk-copy';
  appendChunkCopy(copy, chunk, previous);
  const detail = document.createElement('div'); detail.className = 'knowledge-chunk-detail';
  detail.append(
    detailField('Chunk ID', chunk.chunkId), detailField('Document ID', chunk.documentId),
    detailField('标题路径', chunk.titlePath), detailField('偏移基准', chunk.offsetBasis),
    detailField('父 Chunk', chunk.parentId), detailField('内容类型', chunk.contentType),
    detailField('总 Chunk 数', chunk.totalChunks),
    detailField('降级原因', chunk.splitDegraded ? chunk.splitDegradedReason || '已降级' : '无'),
  );
  toggle.addEventListener('click', () => {
    const expanded = !detail.classList.contains('visible');
    detail.classList.toggle('visible', expanded);
    toggle.textContent = expanded ? '⌃' : '⌄';
    toggle.setAttribute('aria-expanded', String(expanded));
  });
  body.append(copy, detail);
  row.append(head, body);
  return row;
}

function orderedKnowledgeChunks(chunks) {
  const children = new Map();
  chunks.filter((chunk) => chunk.chunkLevel === 'CHILD').forEach((chunk) => {
    if (!children.has(chunk.parentId)) children.set(chunk.parentId, []);
    children.get(chunk.parentId).push(chunk);
  });
  const ordered = [];
  const included = new Set();
  chunks.filter((chunk) => chunk.chunkLevel !== 'CHILD').forEach((chunk) => {
    ordered.push(chunk); included.add(chunk.chunkId);
    (children.get(chunk.chunkId) || []).forEach((child) => { ordered.push(child); included.add(child.chunkId); });
  });
  chunks.filter((chunk) => !included.has(chunk.chunkId)).forEach((chunk) => ordered.push(chunk));
  return ordered;
}

function renderKnowledgeChunks(result) {
  const chunks = orderedKnowledgeChunks(result?.chunks || []);
  document.getElementById('knowledgeChunkCount').textContent = result?.chunkCount || 0;
  document.getElementById('knowledgeAverageTokens').textContent = result?.averageTokenCount || 0;
  document.getElementById('knowledgeMaxTokens').textContent = result?.maxTokenCount || 0;
  document.getElementById('knowledgeOverlapTokens').textContent = result?.overlapTokens || 0;
  document.getElementById('knowledgeStrategy').value = result?.strategy || 'CONTENT_TYPE_AWARE';
  const parentCount = chunks.filter((chunk) => chunk.chunkLevel === 'PARENT').length;
  const childCount = chunks.filter((chunk) => chunk.chunkLevel === 'CHILD').length;
  document.getElementById('knowledgeResultTitle').textContent = parentCount ? '父子结构' : '切分结果';
  const rendered = chunks.slice(0, MAX_RENDERED_CHUNKS);
  document.getElementById('knowledgeResultCount').textContent = parentCount
    ? `${parentCount} 个父块 / ${childCount} 个子块`
    : `显示 ${rendered.length} / ${chunks.length}`;
  const list = document.getElementById('knowledgeChunkList');
  list.replaceChildren();
  const previousByGroup = new Map();
  rendered.forEach((chunk) => {
    const group = chunk.chunkLevel === 'CHILD' ? `child:${chunk.parentId}`
      : chunk.chunkLevel === 'STANDALONE' ? 'standalone' : `parent:${chunk.chunkId}`;
    const previous = previousByGroup.get(group);
    list.appendChild(createKnowledgeChunk(chunk, previous));
    previousByGroup.set(group, chunk);
  });
  document.getElementById('knowledgeEmpty').classList.toggle('hidden', chunks.length > 0);
  list.classList.toggle('hidden', chunks.length === 0);
}

document.querySelectorAll('.tab').forEach((tab) => tab.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach((item) => item.classList.toggle('active', item === tab));
  ['users', 'tokens', 'knowledge', 'badCases', 'audit'].forEach((name) => {
    document.getElementById(`${name}Panel`).classList.toggle('hidden', tab.dataset.tab !== name);
  });
  if (tab.dataset.tab === 'tokens') loadTokens().catch(showError);
  if (tab.dataset.tab === 'knowledge') loadKnowledgeCatalog().catch(showError);
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
document.getElementById('addKnowledgeFile').addEventListener('click', () => document.getElementById('knowledgeFile').click());
document.getElementById('uploadKnowledgeVersion').addEventListener('click', () => document.getElementById('knowledgeFile').click());
document.getElementById('knowledgeFile').addEventListener('change', (event) => {
  const [file] = event.target.files;
  chooseKnowledgeFile(file).catch(showError);
  event.target.value = '';
});
document.getElementById('previewKnowledgeChunks').addEventListener('click', () => previewKnowledgeFile().catch(showError));
document.getElementById('confirmKnowledgeImport').addEventListener('click', () => importKnowledgeFile().catch(showError));
document.getElementById('knowledgeOverlapToggle').addEventListener('change', (event) => {
  document.getElementById('knowledgeChunkList').classList.toggle('knowledge-overlap-hidden', !event.target.checked);
});
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
