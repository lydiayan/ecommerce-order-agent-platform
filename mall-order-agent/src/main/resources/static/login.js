const accounts = [
  ['hr.linyue', '林悦', 'HRBP'], ['hr.chenchen', '陈晨', '招聘专员'],
  ['dev.zhouhang', '周航', '后端工程师'], ['dev.zhaoning', '赵宁', '平台工程师'],
  ['sales.wanglei', '王磊', '华东区销售'], ['sales.liuting', '刘婷', '大客户销售'],
  ['customer.zhangwei', '张伟', '个人客户'], ['customer.lina', '李娜', '个人客户'],
];

let csrf;
async function loadCsrf() {
  const response = await fetch('/auth/csrf');
  const body = await response.json();
  csrf = body.data;
}

function renderAccounts() {
  const container = document.getElementById('demoAccounts');
  accounts.forEach(([username, name, role]) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'account';
    const title = document.createElement('strong');
    title.textContent = name + ' · ' + role;
    const detail = document.createElement('span');
    detail.textContent = username;
    button.append(title, detail);
    button.addEventListener('click', () => {
      document.getElementById('username').value = username;
      document.getElementById('password').focus();
    });
    container.appendChild(button);
  });
}

document.getElementById('loginForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const error = document.getElementById('loginError');
  const button = document.getElementById('loginButton');
  error.textContent = '';
  button.disabled = true;
  try {
    if (!csrf) await loadCsrf();
    const payload = new URLSearchParams();
    payload.set('username', document.getElementById('username').value.trim());
    payload.set('password', document.getElementById('password').value);
    const response = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token },
      body: payload,
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || '登录失败');
    window.location.replace(body.redirect || '/');
  } catch (e) {
    error.textContent = e.message;
    await loadCsrf().catch(() => {});
  } finally {
    button.disabled = false;
  }
});

renderAccounts();
loadCsrf().catch(() => {
  document.getElementById('loginError').textContent = '认证服务暂不可用';
});
