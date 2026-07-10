const API_BASE = '';

const $ = (id) => document.getElementById(id);

const state = {
  conversationId: 'web-' + Date.now(),
  awaitingConfirm: false,
  pendingThreadId: null,
  sending: false,
};

const STATUS_MAP = {
  0: { text: '待付款', cls: 's0' },
  1: { text: '已付款', cls: 's1' },
  2: { text: '已发货', cls: 's2' },
  3: { text: '已完成', cls: 's3' },
  4: { text: '已取消', cls: 's4' },
};

function formatDate(value) {
  if (!value) return '-';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString('zh-CN');
}

function appendMessage(role, text) {
  const box = $('messages');
  const el = document.createElement('div');
  el.className = role === 'meta' ? 'msg meta' : 'msg ' + role;
  el.textContent = text;
  box.appendChild(el);
  box.scrollTop = box.scrollHeight;
}

function setStatus(online) {
  const badge = $('statusBadge');
  badge.classList.toggle('online', online);
  badge.classList.toggle('offline', !online);
  $('statusText').textContent = online ? 'Agent 已连接' : 'Agent 离线';
}

function updateInputPlaceholder() {
  const input = $('queryInput');
  if (!input) return;
  input.placeholder = state.awaitingConfirm
    ? '请回复「确认」或「取消」'
    : '输入问题，例如：查询订单 ORD20250101120000';
}

function parseConfirmIntent(text) {
  const normalized = text.trim().replace(/[？?。！!，,、\s]/g, '');
  if (!normalized) return 'unknown';
  const cancelWords = ['取消', '不要', '算了', '不用', '放弃', '不确认'];
  const confirmWords = ['确认', '确定', '同意', '是的', '是', '好的', '可以', '执行'];
  if (cancelWords.some((w) => normalized === w || normalized.includes(w))) return 'cancel';
  if (confirmWords.some((w) => normalized === w || normalized.endsWith(w) || normalized.includes(w + '执行'))) {
    return 'confirm';
  }
  return 'unknown';
}

function clearPendingConfirm() {
  state.awaitingConfirm = false;
  state.pendingThreadId = null;
  updateInputPlaceholder();
}

function markAwaitingConfirm(data) {
  state.awaitingConfirm = true;
  state.pendingThreadId = data.threadId || state.conversationId;
  updateInputPlaceholder();
}

async function checkHealth() {
  try {
    const res = await fetch(API_BASE + '/agent/order/health');
    const json = await res.json();
    setStatus(res.ok && json.code === 200);
  } catch {
    setStatus(false);
  }
}

async function loadOrders() {
  const userId = $('userIdInput').value.trim();
  const list = $('orderList');
  if (!userId) {
    list.innerHTML = '<p class="empty">请输入用户 ID</p>';
    return;
  }
  list.innerHTML = '<p class="empty">加载中…</p>';
  try {
    const res = await fetch(API_BASE + '/agent/order/orders/' + encodeURIComponent(userId));
    const json = await res.json();
    if (!res.ok || json.code !== 200) {
      const hint = json.message || ('HTTP ' + res.status);
      const is503 = json.code === 503;
      list.innerHTML = '<p class="empty' + (is503 ? ' warn' : '') + '">' +
        (is503 ? '⚠ ' : '') + hint + '</p>';
      return;
    }
    renderOrders(json.data || []);
  } catch {
    list.innerHTML = '<p class="empty">无法连接订单服务</p>';
  }
}

function renderOrders(orders) {
  const list = $('orderList');
  if (!orders.length) {
    list.innerHTML = '<p class="empty">暂无订单</p>';
    return;
  }
  list.innerHTML = orders.map((o) => {
    const st = STATUS_MAP[o.orderStatus] || { text: '未知', cls: 's3' };
    return `
      <article class="order-card" data-order-id="${o.orderId || ''}">
        <div class="oid">${o.orderId || '-'}</div>
        <div class="row">
          <span class="badge ${st.cls}">${st.text}</span>
          <span>¥${o.totalAmount ?? '-'}</span>
        </div>
        <div class="row" style="margin-top:6px">
          <span>${formatDate(o.orderTime)}</span>
        </div>
      </article>`;
  }).join('');

  list.querySelectorAll('.order-card').forEach((card) => {
    card.addEventListener('click', () => {
      const oid = card.dataset.orderId;
      if (oid) {
        $('queryInput').value = '查询订单 ' + oid + ' 的详情';
        $('queryInput').focus();
      }
    });
  });
}

function formatResponseSuffix(data) {
  let suffix = '';
  if (data.planStrategy) suffix += '\n\n— 策略: ' + data.planStrategy;
  if (data.grounded === false && data.planStrategy !== 'ORDER_QUERY') {
    suffix += ' · 未命中知识库';
  }
  return suffix;
}

function setSending(active) {
  state.sending = active;
  const btn = $('sendBtn');
  if (btn) btn.disabled = active;
}

function handleAskResponse(data) {
  if (data.awaitingUserConfirm || (data.interrupted && data.planStrategy === 'DANGEROUS_ORDER_OP')) {
    markAwaitingConfirm(data);
    appendMessage('assistant', (data.answer || '(无回答)') + formatResponseSuffix(data));
    return;
  }
  clearPendingConfirm();
  if (data.answer) {
    appendMessage('assistant', data.answer + formatResponseSuffix(data));
  }
}

async function ask(query) {
  const text = query.trim();
  if (!text) return;
  if (state.sending) {
    appendMessage('meta', '上一条请求处理中，请稍候…');
    return;
  }

  setSending(true);
  appendMessage('user', text);
  $('queryInput').value = '';

  try {
    appendMessage('assistant', '思考中…');
    const res = await fetch(API_BASE + '/agent/order/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        query: text,
        userId: $('userIdInput').value.trim() || 'default_user',
        conversationId: state.conversationId,
      }),
    });
    const json = await res.json();
    removeThinkingMessage();

    if (!res.ok || json.code !== 200 || !json.data) {
      appendMessage('assistant', '请求失败：' + (json.message || res.status));
      return;
    }
    handleAskResponse(json.data);
  } catch {
    removeThinkingMessage();
    appendMessage('assistant', '网络错误，请确认 mall-order-agent 已启动。');
  } finally {
    setSending(false);
  }
}

async function resume(approved) {
  if (!state.pendingThreadId || state.sending) return;

  setSending(true);
  try {
    appendMessage('assistant', '处理中…');
    const res = await fetch(API_BASE + '/agent/order/resume', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        threadId: state.pendingThreadId,
        approved,
      }),
    });
    const json = await res.json();
    removeThinkingMessage();

    if (!res.ok || json.code !== 200 || !json.data) {
      appendMessage('assistant', '操作失败：' + (json.message || res.status));
      return;
    }

    const data = json.data;
    clearPendingConfirm();
    if (data.answer) {
      appendMessage('assistant', data.answer + formatResponseSuffix(data));
    }
    if (approved) {
      loadOrders();
    }
  } catch {
    removeThinkingMessage();
    appendMessage('assistant', '操作失败，请重试。');
  } finally {
    setSending(false);
  }
}

function removeThinkingMessage() {
  const msgs = $('messages');
  const last = msgs.lastElementChild;
  if (last && (last.textContent === '思考中…' || last.textContent === '处理中…')) {
    msgs.removeChild(last);
  }
}

async function handleUserInput(query) {
  const text = query.trim();
  if (!text) return;

  if (state.awaitingConfirm && state.pendingThreadId) {
    const intent = parseConfirmIntent(text);
    if (intent === 'confirm') {
      appendMessage('user', text);
      $('queryInput').value = '';
      await resume(true);
      return;
    }
    if (intent === 'cancel') {
      appendMessage('user', text);
      $('queryInput').value = '';
      await resume(false);
      return;
    }
    appendMessage('meta', '当前有待确认操作，请回复「确认」或「取消」');
    return;
  }

  await ask(text);
}

function bindEvents() {
  $('chatForm').addEventListener('submit', (e) => {
    e.preventDefault();
    handleUserInput($('queryInput').value);
  });

  document.querySelectorAll('.chip').forEach((btn) => {
    btn.addEventListener('click', () => handleUserInput(btn.dataset.query));
  });

  $('refreshOrders').addEventListener('click', loadOrders);
  $('userIdInput').addEventListener('change', loadOrders);
}

function init() {
  bindEvents();
  updateInputPlaceholder();
  checkHealth();
  loadOrders();
  appendMessage('assistant', '你好，我是智能客服。退货/退款等操作会先询问确认，回复「确认」或「取消」后执行。');
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
