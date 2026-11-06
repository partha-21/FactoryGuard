export async function api(path, options) {
  const response = await fetch(path, options);
  if (response.status === 401) {
    window.location.href = '/login.html';
    throw new Error('signed out');
  }
  return response;
}

export async function getJson(path) {
  const response = await api(path);
  if (!response.ok) throw new Error('Request failed: ' + path);
  return response.json();
}

export async function postJson(path) {
  const response = await api(path, { method: 'POST' });
  return response.json().catch(() => ({}));
}
