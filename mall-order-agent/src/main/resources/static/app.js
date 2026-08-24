const $ = (id) => document.getElementById(id);

const state = {
  currentPersona: null,
  conversationId: createConversationId(),
  awaitingConfirm: false,
  pendingThreadId: null,
  sending: false,
  csrf: null,
  activeAbortController: null,
};

const CATEGORY_META = {
  HR: { label: 'HR', workspace: '员工知识', eyebrow: 'HR 工作区' },
  ENGINEERING: { label: '研发', workspace: '技术知识', eyebrow: '研发工作区' },
  SALES: { label: '销售', workspace: '客户订单', eyebrow: '销售工作区' },
  CUSTOMER: { label: '客户', workspace: '我的订单', eyebrow: '客户工作区' },
};

const CAPABILITY_LABELS = {
  KNOWLEDGE_SEARCH: '知识检索', OWN_ORDER_READ: '本人订单',
  ASSIGNED_ORDER_READ: '分配客户订单', ORDER_CANCEL: '取消订单',
  AFTER_SALES_CREATE: '发起售后',
};

const STATUS_MAP = {
  0: { text: '待付款', cls: 'status-pending' }, 1: { text: '已付款', cls: 'status-paid' },
  2: { text: '已发货', cls: 'status-shipped' }, 3: { text: '已完成', cls: 'status-completed' },
  4: { text: '已取消', cls: 'status-cancelled' },
};

const FEEDBACK_REASONS = [
  ['INCORRECT', '回答错误'],
  ['IRRELEVANT', '答非所问'],
  ['INCOMPLETE', '信息不完整'],
  ['TOOL_FAILURE', '工具执行失败'],
  ['HARD_TO_UNDERSTAND', '难以理解'],
  ['SAFETY_RISK', '存在安全风险'],
  ['OTHER', '其他'],
];

function createConversationId() {
  const suffix = window.crypto?.randomUUID ? window.crypto.randomUUID()
    : Date.now() + '-' + Math.random().toString(16).slice(2);
  return 'web-' + suffix;
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

async function loadCsrf() {
  const response = await fetch('/auth/csrf', { cache: 'no-store' });
  const body = await response.json();
  if (!response.ok || body.code !== 200 || !body.data?.headerName || !body.data?.token) {
    throw new Error(body.message || '请求校验信息获取失败');
  }
  state.csrf = body.data;
  return state.csrf;
}

async function api(path, options = {}, retryCsrf = true) {
  const request = { ...options, headers: { ...(options.headers || {}) } };
  const method = (request.method || 'GET').toUpperCase();
  const requiresCsrf = !['GET', 'HEAD', 'OPTIONS'].includes(method);
  if (requiresCsrf) {
    const csrf = await loadCsrf();
    request.headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(path, request);
  if (response.status === 401) {
    window.location.replace('/login.html');
    throw new Error('登录已失效');
  }
  if (response.status === 428) {
    window.location.replace('/change-password.html');
    throw new Error('需要先修改密码');
  }
  const responseText = await response.text();
  let body;
  try {
    body = JSON.parse(responseText);
  } catch {
    const contentType = response.headers.get('content-type') || 'unknown';
    const summary = responseText.replace(/\s+/g, ' ').trim().slice(0, 160);
    throw new Error(`HTTP ${response.status}，响应类型 ${contentType}`
      + (summary ? `：${summary}` : '，响应内容为空'));
  }
  if (response.status === 403 && body.error === 'CSRF_TOKEN_INVALID' && requiresCsrf && retryCsrf) {
    state.csrf = null;
    return api(path, options, false);
  }
  if (!response.ok || body.code !== 200) throw new Error(body.message || ('HTTP ' + response.status));
  return body.data;
}

async function streamApi(path, options, onEvent, retryCsrf = true) {
  const request = { ...options, headers: { ...(options.headers || {}) } };
  const csrf = await loadCsrf();
  request.headers[csrf.headerName] = csrf.token;

  const response = await fetch(path, request);
  if (response.status === 401) {
    window.location.replace('/login.html');
    throw new Error('登录已失效');
  }
  if (response.status === 428) {
    window.location.replace('/change-password.html');
    throw new Error('需要先修改密码');
  }

  const contentType = response.headers.get('content-type') || '';
  if (!response.ok || !contentType.includes('text/event-stream')) {
    const responseText = await response.text();
    let body = null;
    try { body = JSON.parse(responseText); } catch { /* 使用下面的 HTTP 错误 */ }
    if (response.status === 403 && body?.error === 'CSRF_TOKEN_INVALID' && retryCsrf) {
      state.csrf = null;
      return streamApi(path, options, onEvent, false);
    }
    throw new Error(body?.message || `HTTP ${response.status}`);
  }
  if (!response.body) throw new Error('浏览器无法读取流式响应');

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const dispatchFrame = (frame) => {
    let eventName = 'message';
    const dataLines = [];
    frame.split(/\r?\n/).forEach((line) => {
      if (!line || line.startsWith(':')) return;
      if (line.startsWith('event:')) eventName = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
    });
    if (!dataLines.length) return;
    const rawData = dataLines.join('\n');
    let data;
    try { data = JSON.parse(rawData); }
    catch { throw new Error('流式响应格式错误'); }
    onEvent(eventName, data);
  };

  const drainFrames = (flush = false) => {
    let boundary = buffer.search(/\r?\n\r?\n/);
    while (boundary >= 0) {
      const match = buffer.slice(boundary).match(/^(?:\r?\n){2}/)[0];
      const frame = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + match.length);
      if (frame.trim()) dispatchFrame(frame);
      boundary = buffer.search(/\r?\n\r?\n/);
    }
    if (flush && buffer.trim()) {
      dispatchFrame(buffer);
      buffer = '';
    }
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    drainFrames();
  }
  buffer += decoder.decode();
  drainFrames(true);
}

function appendMessage(role, text) {
  const message = el('div', 'message ' + role, text);
  $('messages').appendChild(message);
  $('messages').scrollTop = $('messages').scrollHeight;
  return message;
}

function updateMessage(message, text) {
  message.textContent = text;
  $('messages').scrollTop = $('messages').scrollHeight;
}

function appendFeedbackControls(message, response) {
  if (!response.feedbackEnabled || !response.responseId) return;
  const feedbackState = { rating: null, busy: false };
  const actions = el('div', 'feedback-actions');
  const up = el('button', 'feedback-button', '👍');
  const down = el('button', 'feedback-button', '👎');
  const status = el('span', 'feedback-status');
  const panel = el('form', 'feedback-panel hidden');
  up.type = down.type = 'button';
  up.title = '赞';
  up.setAttribute('aria-label', '赞');
  down.title = '踩';
  down.setAttribute('aria-label', '踩');

  const reasonList = el('div', 'feedback-reasons');
  FEEDBACK_REASONS.forEach(([value, label]) => {
    const option = el('label', 'feedback-reason');
    const checkbox = el('input');
    checkbox.type = 'checkbox';
    checkbox.value = value;
    option.append(checkbox, el('span', '', label));
    reasonList.appendChild(option);
  });
  const comment = el('textarea', 'feedback-comment');
  comment.maxLength = 500;
  comment.rows = 3;
  comment.placeholder = '补充说明（选填）';
  const submit = el('button', 'button button-quiet feedback-submit', '提交补充');
  submit.type = 'submit';
  panel.append(reasonList, comment, submit);

  const render = () => {
    up.classList.toggle('selected', feedbackState.rating === 'UP');
    down.classList.toggle('selected', feedbackState.rating === 'DOWN');
    up.setAttribute('aria-pressed', String(feedbackState.rating === 'UP'));
    down.setAttribute('aria-pressed', String(feedbackState.rating === 'DOWN'));
    up.disabled = down.disabled = submit.disabled = feedbackState.busy;
    panel.classList.toggle('hidden', feedbackState.rating !== 'DOWN');
  };

  const save = async (rating, reasons = [], feedbackComment = '') => {
    feedbackState.busy = true;
    status.classList.remove('error');
    status.textContent = '';
    render();
    try {
      const saved = await api('/agent/feedback', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ responseId: response.responseId, rating, reasons, comment: feedbackComment }),
      });
      feedbackState.rating = saved.rating;
      status.textContent = rating === 'DOWN' ? '已记录，可补充原因' : '感谢反馈';
    } catch (error) {
      status.classList.add('error');
      status.textContent = error.message;
    } finally {
      feedbackState.busy = false;
      render();
    }
  };

  const cancel = async () => {
    feedbackState.busy = true;
    status.classList.remove('error');
    status.textContent = '';
    render();
    try {
      await api(`/agent/feedback/${response.responseId}`, { method: 'DELETE' });
      feedbackState.rating = null;
      reasonList.querySelectorAll('input').forEach((input) => { input.checked = false; });
      comment.value = '';
      status.textContent = '已取消';
    } catch (error) {
      status.classList.add('error');
      status.textContent = error.message;
    } finally {
      feedbackState.busy = false;
      render();
    }
  };

  up.addEventListener('click', () => feedbackState.rating === 'UP' ? cancel() : save('UP'));
  down.addEventListener('click', () => feedbackState.rating === 'DOWN' ? cancel() : save('DOWN'));
  panel.addEventListener('submit', (event) => {
    event.preventDefault();
    const reasons = [...reasonList.querySelectorAll('input:checked')].map((input) => input.value);
    save('DOWN', reasons, comment.value.trim());
  });

  actions.append(up, down, status);
  message.append(actions, panel);
  render();
}

function setSending(active) {
  state.sending = active;
  $('sendBtn').disabled = active;
  $('refreshWorkspace').disabled = active;
}

function updateComposer() {
  $('queryInput').placeholder = state.awaitingConfirm ? '回复“确认”或“取消”' : '输入问题';
}

function updateIdentity() {
  const persona = state.currentPersona;
  $('currentAvatar').textContent = persona.displayName.slice(0, 1);
  $('currentPersonaName').textContent = persona.displayName;
  $('currentPersonaMeta').textContent = (CATEGORY_META[persona.category]?.label || persona.category)
    + ' · ' + persona.actorUserId;
  $('workspaceEyebrow').textContent = CATEGORY_META[persona.category]?.eyebrow || '工作区';
  $('workspaceTitle').textContent = CATEGORY_META[persona.category]?.workspace || '工作区';
  renderSuggestions(persona.suggestions || []);
  renderPersonaSummary(persona);
}

function renderSuggestions(suggestions) {
  const container = $('suggestions');
  container.replaceChildren();
  suggestions.slice(0, 3).forEach((suggestion) => {
    const button = el('button', 'suggestion-button', suggestion);
    button.type = 'button';
    button.addEventListener('click', () => handleUserInput(suggestion));
    container.appendChild(button);
  });
}

function renderPersonaSummary(persona) {
  const summary = $('personaSummary');
  summary.replaceChildren();
  const title = el('div', 'persona-summary-title');
  title.append(el('strong', '', persona.jobTitle), el('span', '', persona.department));
  summary.append(title, el('p', '', persona.description));
  const capabilities = el('div', 'capability-list');
  (persona.capabilities || []).forEach((capability) =>
    capabilities.appendChild(el('span', 'capability', CAPABILITY_LABELS[capability] || capability)));
  summary.appendChild(capabilities);
}

function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN');
}

function renderOrder(order) {
  const status = STATUS_MAP[order.orderStatus] || { text: '未知', cls: 'status-completed' };
  const button = el('button', 'order-item');
  button.type = 'button';
  const top = el('span', 'order-top');
  top.append(el('strong', '', order.orderId || '-'), el('span', 'order-status ' + status.cls, status.text));
  const middle = el('span', 'order-middle');
  middle.append(el('strong', '', '¥' + (order.totalAmount ?? '-')), el('span', '', formatDate(order.orderTime)));
  const product = order.orderDetails?.[0]?.productName;
  const details = el('span', 'order-details', product || order.shippingAddress || '订单详情');
  const contact = el('span', 'order-contact', [order.shippingAddress, order.contactPhone].filter(Boolean).join(' · '));
  button.append(top, middle, details);
  if (contact.textContent) button.appendChild(contact);
  button.addEventListener('click', () => handleUserInput('查询订单 ' + order.orderId + ' 的详情'));
  return button;
}

function renderCustomerOrders(customer) {
  const section = el('section', 'customer-orders');
  const heading = el('div', 'customer-heading');
  heading.append(el('h3', '', customer.customerName), el('span', '', customer.customerUserId));
  section.appendChild(heading);
  if (!customer.orders?.length) section.appendChild(el('p', 'empty-state compact', '暂无订单'));
  else customer.orders.forEach((order) => section.appendChild(renderOrder(order)));
  return section;
}

function renderWorkspace(workspace) {
  const content = $('workspaceContent');
  content.replaceChildren();
  if (workspace.customers?.length) {
    workspace.customers.forEach((customer) => content.appendChild(renderCustomerOrders(customer)));
    return;
  }
  const scopeSection = el('section', 'knowledge-section');
  scopeSection.appendChild(el('h3', '', '可用知识范围'));
  const scopes = el('div', 'scope-list');
  (workspace.knowledgeScopes || []).forEach((scope) => scopes.appendChild(el('span', 'scope', scope)));
  scopeSection.appendChild(scopes);
  const promptSection = el('section', 'workspace-prompts');
  promptSection.appendChild(el('h3', '', '常用问题'));
  (workspace.suggestions || []).forEach((suggestion) => {
    const button = el('button', 'workspace-prompt', suggestion);
    button.type = 'button';
    button.addEventListener('click', () => handleUserInput(suggestion));
    promptSection.appendChild(button);
  });
  content.append(scopeSection, promptSection);
}

async function loadWorkspace() {
  $('workspaceContent').replaceChildren(el('p', 'empty-state', '加载中'));
  try { renderWorkspace(await api('/agent/workspace/me')); }
  catch (error) { $('workspaceContent').replaceChildren(el('p', 'empty-state error', error.message)); }
}

function parseConfirmIntent(text) {
  const value = text.trim().replace(/[？?。！!，,、\s]/g, '');
  if (['取消', '不要', '算了', '不用', '放弃', '不确认'].some((word) => value.includes(word))) return 'cancel';
  if (['确认', '确定', '同意', '是的', '好的', '执行'].some((word) => value.includes(word))) return 'confirm';
  return 'unknown';
}

function handleAskResponse(data, message = null) {
  state.awaitingConfirm = Boolean(data.awaitingUserConfirm
    || (data.interrupted && data.planStrategy === 'DANGEROUS_ORDER_OP'));
  state.pendingThreadId = state.awaitingConfirm ? data.threadId : null;
  updateComposer();
  const answer = data.answer || '当前没有可展示的回答。';
  if (message) {
    message.classList.remove('progress');
    updateMessage(message, answer);
  } else message = appendMessage('assistant', answer);
  appendFeedbackControls(message, data);
}

async function ask(query) {
  setSending(true);
  appendMessage('user', query);
  $('queryInput').value = '';
  const answerMessage = appendMessage('assistant progress', '处理中');
  const abortController = new AbortController();
  state.activeAbortController = abortController;
  let receivedDelta = false;
  let completed = false;
  let streamedAnswer = '';
  let renderScheduled = false;
  const renderStreamedAnswer = () => {
    renderScheduled = false;
    if (completed) return;
    updateMessage(answerMessage, streamedAnswer);
  };
  try {
    await streamApi('/agent/order/ask/stream', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, conversationId: state.conversationId }),
      signal: abortController.signal,
    }, (eventName, data) => {
      if (eventName === 'delta') {
        if (!receivedDelta) {
          receivedDelta = true;
          answerMessage.classList.remove('progress');
          updateMessage(answerMessage, '');
        }
        streamedAnswer += data.text || '';
        if (!renderScheduled) {
          renderScheduled = true;
          window.requestAnimationFrame(renderStreamedAnswer);
        }
      } else if (eventName === 'complete') {
        completed = true;
        streamedAnswer = data.answer || streamedAnswer;
        handleAskResponse(data, answerMessage);
      } else if (eventName === 'error') {
        throw new Error(data.message || '请求处理失败');
      }
    });
    if (!completed) throw new Error('连接已结束，但回答未完成');
  } catch (error) {
    answerMessage.classList.remove('progress');
    const prefix = receivedDelta && streamedAnswer
      ? streamedAnswer + '\n\n回答中断：' : '请求失败：';
    streamedAnswer = prefix + error.message;
    updateMessage(answerMessage, streamedAnswer);
  } finally {
    if (state.activeAbortController === abortController) state.activeAbortController = null;
    setSending(false);
  }
}

async function resume(approved) {
  setSending(true);
  const answerMessage = appendMessage('assistant progress', '处理中');
  try {
    const data = await api('/agent/order/resume', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ threadId: state.pendingThreadId, approved }),
    });
    handleAskResponse(data, answerMessage);
    await loadWorkspace();
  } catch (error) {
    answerMessage.classList.remove('progress');
    updateMessage(answerMessage, '操作失败：' + error.message);
  } finally { setSending(false); }
}

async function handleUserInput(input) {
  const query = input.trim();
  if (!query || state.sending) return;
  if (state.awaitingConfirm) {
    const intent = parseConfirmIntent(query);
    if (intent === 'unknown') { appendMessage('meta', '当前操作等待确认或取消。'); return; }
    appendMessage('user', query);
    $('queryInput').value = '';
    await resume(intent === 'confirm');
  } else await ask(query);
}

async function logout() {
  try { await api('/auth/logout', { method: 'POST' }); } finally { window.location.replace('/login.html'); }
}

function showImpersonationBanner(me) {
  if (!me.impersonating) return;
  const banner = el('div', 'impersonation-banner');
  const expires = me.impersonationExpiresAt
    ? new Date(me.impersonationExpiresAt * 1000).toLocaleTimeString('zh-CN', { hour12: false }) : '';
  banner.appendChild(el('strong', '', `演示身份：${me.displayName}${expires ? ` · 至 ${expires}` : ''}`));
  const exit = el('button', 'button button-quiet', '退出演示身份');
  exit.type = 'button';
  exit.addEventListener('click', async () => {
    const result = await api('/auth/impersonation/exit', { method: 'POST' });
    window.location.replace(result.redirect || '/admin.html');
  });
  banner.appendChild(exit);
  document.body.prepend(banner);
}

async function init() {
  $('chatForm').addEventListener('submit', (event) => {
    event.preventDefault();
    handleUserInput($('queryInput').value);
  });
  $('refreshWorkspace').addEventListener('click', loadWorkspace);
  $('logoutBtn').addEventListener('click', logout);
  updateComposer();
  try {
    await loadCsrf();
    const me = await api('/auth/me');
    if (me.passwordChangeRequired) { window.location.replace('/change-password.html'); return; }
    state.currentPersona = me.persona;
    showImpersonationBanner(me);
    updateIdentity();
    appendMessage('assistant', state.currentPersona.welcomeMessage);
    await loadWorkspace();
    await api('/agent/order/health');
    $('statusBadge').classList.add('online');
    $('statusText').textContent = 'Agent 在线';
  } catch (error) {
    $('statusBadge').classList.add('offline');
    $('statusText').textContent = 'Agent 离线';
    appendMessage('assistant', error.message);
  }
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
else init();

window.addEventListener('beforeunload', () => state.activeAbortController?.abort());
