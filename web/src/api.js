const TOKEN_KEY = 'unified_inbox_token'
const API_BASE = '/api/v1'

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let memoryToken = ''

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || memoryToken
  } catch {
    return memoryToken
  }
}

export function setToken(value) {
  memoryToken = value
  try {
    localStorage.setItem(TOKEN_KEY, value)
  } catch {
    // Private mode can refuse localStorage; the in-memory token still works for this tab.
  }
}

export function clearToken() {
  memoryToken = ''
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // ignore
  }
}

function detailMessage(data, fallback) {
  if (!data) return fallback
  if (typeof data.detail === 'string') return data.detail
  if (Array.isArray(data.detail)) {
    return data.detail
      .map((item) => (item?.loc ? `${item.loc.join('.')}: ${item.msg}` : String(item?.msg || item)))
      .join('；')
  }
  return fallback
}

async function request(path, options = {}) {
  const { method = 'GET', body, params, token = getToken() } = options
  const url = new URL(`${API_BASE}${path}`, window.location.origin)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
    }
  }
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError('无法连接后端，请确认服务在运行且手机与电脑在同一网络', 0)
  }

  let data = null
  const text = await response.text()
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }
  if (response.status === 401) {
    throw new ApiError('Token 不正确或已失效', 401)
  }
  if (!response.ok) {
    throw new ApiError(detailMessage(data, `请求失败 (${response.status})`), response.status)
  }
  return data
}

export async function testToken(token) {
  return request('/ping', { token: token.trim() })
}

export function getStatus() {
  return request('/status')
}

export function getMessages(filters = {}) {
  return request('/messages', {
    params: {
      source: filters.source,
      conversation: filters.conversation,
      q: filters.q,
      unread: filters.unread ? 'true' : undefined,
      limit: filters.limit || 50,
      before_time: filters.before_time,
      before_id: filters.before_id,
    },
  })
}

export function getConversations() {
  return request('/conversations')
}

export function markRead(id) {
  return request(`/messages/${id}/read`, { method: 'POST' })
}

export function markReadMany(ids) {
  return request('/messages/read', { method: 'POST', body: { ids } })
}

export function markAllRead(filters = {}) {
  return request('/messages/read-all', {
    method: 'POST',
    body: {
      source: filters.source || null,
      conversation: filters.conversation || null,
      q: filters.q || null,
    },
  })
}

export function getWsTicket() {
  return request('/ws-ticket', { method: 'POST' })
}
