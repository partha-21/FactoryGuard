(function () {
  const $ = id => document.getElementById(id);
  let mode = 'login';

  function setMode(m) {
    mode = m;
    const reg = m === 'register';
    $('tab-login').className = reg ? '' : 'on';
    $('tab-register').className = reg ? 'on' : '';
    $('f-email-wrap').hidden = !reg;
    $('f-confirm-wrap').hidden = !reg;
    $('pw-hint').hidden = !reg;
    $('submit').textContent = reg ? 'Create account' : 'Sign in';
    $('password').autocomplete = reg ? 'new-password' : 'current-password';
    $('err').textContent = '';
  }

  async function submit() {
    $('err').textContent = '';
    const username = $('username').value.trim();
    const password = $('password').value;
    const body = { username, password };
    if (mode === 'register') {
      if ($('confirm').value !== password) { $('err').textContent = 'The two passwords do not match.'; return; }
      body.email = $('email').value.trim();
    }
    $('submit').disabled = true;
    try {
      const r = await fetch('/api/auth/' + mode, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      const j = await r.json().catch(() => ({}));
      if (r.ok) { window.location.href = '/'; return; }
      $('err').textContent = j.error || 'Something went wrong. Please try again.';
    } catch (e) {
      $('err').textContent = 'Could not reach the server. Is FactoryGuard running?';
    }
    $('submit').disabled = false;
  }

  $('tab-login').addEventListener('click', () => setMode('login'));
  $('tab-register').addEventListener('click', () => setMode('register'));
  $('submit').addEventListener('click', submit);
  document.querySelectorAll('input').forEach(i => i.addEventListener('keydown', e => { if (e.key === 'Enter') submit(); }));

  // Already signed in? Go straight to the dashboard.
  fetch('/api/auth/me').then(r => { if (r.ok) window.location.href = '/'; }).catch(() => {});
})();
