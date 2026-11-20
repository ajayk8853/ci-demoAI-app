async function request(path, options) {
  const response = await fetch(path, options)

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message = body?.detail || body?.message || `Request failed (${response.status})`
    throw new Error(message)
  }

  return response.json()
}

export const api = {
  getDashboard: () => request('/api/dashboard'),
  getRuns: () => request('/api/pipelines'),
  getIssues: () => request('/api/issues'),
  syncRuns: () => request('/api/pipelines/sync', { method: 'POST' }),
  analyzeRun: (runId) => request(`/api/pipelines/analyze/${runId}`, { method: 'POST' }),
}

