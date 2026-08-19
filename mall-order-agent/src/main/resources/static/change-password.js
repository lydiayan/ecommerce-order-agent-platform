let csrf;
async function loadCsrf() {
  const response = await fetch('/auth/csrf');
  const body = await response.json();
  csrf = body.data;
}

document.getElementById('passwordForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const currentPassword = document.getElementById('currentPassword').value;
  const newPassword = document.getElementById('newPassword').value;
  const confirmPassword = document.getElementById('confirmPassword').value;
  const error = document.getElementById('passwordError');
  const button = document.getElementById('passwordButton');
  error.textContent = '';
  if (newPassword !== confirmPassword) {
    error.textContent = '两次输入的新密码不一致';
    return;
  }
  button.disabled = true;
  try {
    if (!csrf) await loadCsrf();
    const response = await fetch('/auth/change-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify({ currentPassword, newPassword }),
    });
    const body = await response.json();
    if (!response.ok || body.code !== 200) throw new Error(body.message || '修改失败');
    window.location.replace('/login.html');
  } catch (e) {
    error.textContent = e.message;
  } finally {
    button.disabled = false;
  }
});

loadCsrf().catch(() => {
  document.getElementById('passwordError').textContent = '认证服务暂不可用';
});
