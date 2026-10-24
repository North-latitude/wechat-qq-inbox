import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  ApiError,
  clearToken,
  getConversations,
  getMessages,
  getStatus,
  getToken,
  getWsTicket,
  markAllRead,
  markRead,
  markReadMany,
  setToken,
  testToken,
} from './api'

const NOTIFY_KEY = 'unified_inbox_notify'
const SOUND_KEY = 'unified_inbox_sound'

function storedFlag(key) {
  try {
    return localStorage.getItem(key) === '1'
  } catch {
    return false
  }
}

function storeFlag(key, value) {
  try {
    if (value) localStorage.setItem(key, '1')
    else localStorage.removeItem(key)
  } catch {
    // ignore
  }
}

export function useInbox() {
  const authed = ref(Boolean(getToken()))
  const authError = ref('')
  const authBusy = ref(false)
  const status = ref(null)
  const messages = ref([])
  const conversations = ref([])
  const filters = reactive({ source: '', conversation: '', q: '', unread: false })
  const selected = ref(new Set())
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  const cursor = ref(null)
  const error = ref('')
  const wsState = ref('offline')
  const toasts = ref([])
  const notifyEnabled = ref(storedFlag(NOTIFY_KEY))
  const soundEnabled = ref(storedFlag(SOUND_KEY))

  let ws = null
  let reconnectTimer = null
  let pollTimer = null
  let statusTimer = null
  let qTimer = null
  let requestSeq = 0
  let reconnectAttempt = 0
  let started = false
  let authTimer = null
  let toastSeq = 0
  let audioContext = null

  const totalUnread = computed(() => status.value?.messages?.total_unread ?? 0)
  const todayUnread = computed(() => status.value?.messages?.today_unread ?? 0)
  const todayCount = computed(() => status.value?.messages?.today_count ?? 0)
  const selectedCount = computed(() => selected.value.size)

  const conversationsForSource = computed(() => {
    const list = conversations.value
    if (!filters.source) return list
    return list.filter((item) => item.source === filters.source)
  })

  const currentConversationLabel = computed(() => {
    if (!filters.conversation) return '全部会话'
    const found = conversations.value.find(
      (item) => item.conversation === filters.conversation && (!filters.source || item.source === filters.source),
    )
    return found ? `${found.conversation}` : filters.conversation
  })

  const allVisibleUnreadSelected = computed(() => {
    const unread = messages.value.filter((item) => !item.is_read)
    return unread.length > 0 && unread.every((item) => selected.value.has(item.id))
  })

  const wsLabel = computed(() => {
    if (wsState.value === 'live') return '实时连接'
    if (wsState.value === 'connecting') return '连接中'
    if (wsState.value === 'polling') return '轮询模式'
    return '未连接'
  })

  function pushToast(text, type = 'info', timeout = 4500) {
    const id = ++toastSeq
    toasts.value = [...toasts.value, { id, text, type }]
    window.setTimeout(() => dismissToast(id), timeout)
  }

  function dismissToast(id) {
    toasts.value = toasts.value.filter((item) => item.id !== id)
  }

  function setCounts(payload) {
    if (!status.value || !payload) return
    if (typeof payload.today_unread === 'number') status.value.messages.today_unread = payload.today_unread
    if (typeof payload.total_unread === 'number') status.value.messages.total_unread = payload.total_unread
  }

  function handleError(err) {
    if (err instanceof ApiError && err.status === 401) {
      clearToken()
      authed.value = false
      authError.value = err.message
      stop()
      return true
    }
    error.value = err?.message || '请求失败'
    return false
  }

  async function login(token) {
    const value = String(token || '').trim()
    if (!value) {
      authError.value = '请输入 Token'
      return false
    }
    authBusy.value = true
    authError.value = ''
    try {
      await testToken(value)
      setToken(value)
      authed.value = true
      stop()
      started = false
      messages.value = []
      selected.value = new Set()
      await start()
      return true
    } catch (err) {
      authError.value = err?.message || '连接失败'
      return false
    } finally {
      authBusy.value = false
    }
  }

  function logout() {
    clearToken()
    authed.value = false
    authError.value = ''
    stop()
    status.value = null
    messages.value = []
    conversations.value = []
    selected.value = new Set()
  }

  async function refreshStatus() {
    try {
      status.value = await getStatus()
    } catch (err) {
      handleError(err)
    }
  }

  async function refreshConversations() {
    try {
      conversations.value = (await getConversations()).items || []
    } catch (err) {
      handleError(err)
    }
  }

  async function refreshMessages(reset = true, silent = false) {
    const seq = ++requestSeq
    if (reset) {
      if (!silent) loading.value = true
    } else {
      loadingMore.value = true
    }
    try {
      const data = await getMessages({
        ...filters,
        limit: 50,
        before_time: reset ? undefined : cursor.value?.before_time,
        before_id: reset ? undefined : cursor.value?.before_id,
      })
      if (seq !== requestSeq) return
      if (reset) {
        messages.value = data.items || []
      } else {
        const existing = new Set(messages.value.map((item) => item.id))
        messages.value = [...messages.value, ...(data.items || []).filter((item) => !existing.has(item.id))]
      }
      hasMore.value = Boolean(data.has_more)
      cursor.value = data.next_cursor || null
      error.value = ''
    } catch (err) {
      if (seq === requestSeq) handleError(err)
    } finally {
      if (seq === requestSeq) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  async function refreshAll() {
    await Promise.allSettled([refreshStatus(), refreshMessages(true), refreshConversations()])
  }

  async function loadMore() {
    if (!hasMore.value || loadingMore.value) return
    await refreshMessages(false)
  }

  async function readMessage(message) {
    if (message.is_read) return
    message.is_read = true
    try {
      const result = await markRead(message.id)
      setCounts(result)
      refreshConversations()
    } catch (err) {
      message.is_read = false
      handleError(err)
    }
  }

  function toggleSelect(id) {
    const next = new Set(selected.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    selected.value = next
  }

  function selectAllVisible() {
    const unread = messages.value.filter((item) => !item.is_read)
    if (allVisibleUnreadSelected.value) {
      selected.value = new Set()
    } else {
      selected.value = new Set(unread.map((item) => item.id))
    }
  }

  async function markSelectedRead() {
    const ids = [...selected.value].filter((id) => {
      const message = messages.value.find((item) => item.id === id)
      return message && !message.is_read
    })
    if (!ids.length) {
      selected.value = new Set()
      return
    }
    const previous = new Map(messages.value.map((item) => [item.id, item.is_read]))
    for (const message of messages.value) {
      if (ids.includes(message.id)) message.is_read = true
    }
    try {
      const result = await markReadMany(ids)
      setCounts(result)
      selected.value = new Set()
      refreshConversations()
    } catch (err) {
      for (const message of messages.value) {
        if (previous.has(message.id)) message.is_read = previous.get(message.id)
      }
      handleError(err)
    }
  }

  async function markAllVisibleRead() {
    if (!totalUnread.value) return
    try {
      const result = await markAllRead(filters)
      setCounts(result)
      for (const message of messages.value) message.is_read = true
      selected.value = new Set()
      refreshConversations()
      pushToast(`已标记 ${result.updated || 0} 条为已读`, 'success')
    } catch (err) {
      handleError(err)
    }
  }

  function matchesFilters(message) {
    if (filters.source && message.source !== filters.source) return false
    if (filters.conversation && message.conversation !== filters.conversation) return false
    if (filters.unread && message.is_read) return false
    const query = filters.q.trim().toLowerCase()
    if (query) {
      const haystack = `${message.content}\n${message.conversation}\n${message.sender || ''}`.toLowerCase()
      if (!haystack.includes(query)) return false
    }
    return true
  }

  function handleIncoming(message) {
    if (!message || messages.value.some((item) => item.id === message.id)) return
    if (matchesFilters(message)) {
      messages.value = [message, ...messages.value]
    }
    pushToast(`${message.source === 'wechat' ? '微信' : 'QQ'} · ${message.conversation}`, 'message')
    if (notifyEnabled.value) notifyBrowser(message)
    if (soundEnabled.value) playChime()
    refreshStatus()
    refreshConversations()
  }

  function handleReadEvent(data) {
    const ids = new Set(data.ids || [])
    for (const message of messages.value) {
      if (ids.has(message.id)) message.is_read = true
    }
    setCounts(data)
  }

  function notifyBrowser(message) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return
    if (!document.hidden) return
    try {
      const notification = new Notification(`${message.source === 'wechat' ? '微信' : 'QQ'} · ${message.conversation}`, {
        body: message.content.length > 120 ? `${message.content.slice(0, 120)}…` : message.content,
        tag: `inbox-${message.id}`,
        icon: '/icons/icon-192.png',
      })
      notification.onclick = () => {
        window.focus()
        notification.close()
      }
    } catch {
      // Some browsers block constructor use outside a service worker; the page still updates.
    }
  }

  function playChime() {
    try {
      audioContext ||= new AudioContext()
      if (audioContext.state === 'suspended') audioContext.resume()
      const now = audioContext.currentTime
      const oscillator = audioContext.createOscillator()
      const gain = audioContext.createGain()
      oscillator.type = 'sine'
      oscillator.frequency.setValueAtTime(660, now)
      oscillator.frequency.setValueAtTime(880, now + 0.09)
      gain.gain.setValueAtTime(0.0001, now)
      gain.gain.exponentialRampToValueAtTime(0.08, now + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.28)
      oscillator.connect(gain).connect(audioContext.destination)
      oscillator.start(now)
      oscillator.stop(now + 0.3)
    } catch {
      // Audio is optional; ignore autoplay restrictions.
    }
  }

  async function enableNotifications() {
    if (!('Notification' in window)) {
      pushToast('此浏览器不支持通知', 'warn')
      return
    }
    const permission = await Notification.requestPermission()
    if (permission === 'granted') {
      notifyEnabled.value = true
      storeFlag(NOTIFY_KEY, true)
      pushToast('浏览器通知已开启（页面打开时有效）', 'success')
    } else {
      notifyEnabled.value = false
      storeFlag(NOTIFY_KEY, false)
      pushToast('浏览器通知未授权', 'warn')
    }
  }

  function disableNotifications() {
    notifyEnabled.value = false
    storeFlag(NOTIFY_KEY, false)
  }

  function toggleSound() {
    soundEnabled.value = !soundEnabled.value
    storeFlag(SOUND_KEY, soundEnabled.value)
    if (soundEnabled.value) playChime()
  }

  function wsUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/ws`
  }

  function scheduleReconnect() {
    if (!authed.value) return
    window.clearTimeout(reconnectTimer)
    const delay = Math.min(30000, 1000 * 2 ** Math.min(reconnectAttempt, 5)) + Math.random() * 400
    reconnectAttempt += 1
    wsState.value = 'connecting'
    reconnectTimer = window.setTimeout(connectWS, delay)
  }

  async function connectWS() {
    if (!authed.value) return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
    wsState.value = 'connecting'
    let ticket
    try {
      const result = await getWsTicket()
      ticket = result.ticket
    } catch (err) {
      handleError(err)
      wsState.value = 'offline'
      startPolling()
      scheduleReconnect()
      return
    }

    const socket = new WebSocket(wsUrl())
    ws = socket
    authTimer = window.setTimeout(() => {
      if (socket.readyState === WebSocket.OPEN) socket.close(4408, 'auth timeout')
    }, 6000)

    socket.onopen = () => {
      socket.send(JSON.stringify({ type: 'auth', ticket }))
    }

    socket.onmessage = (event) => {
      let payload
      try {
        payload = JSON.parse(event.data)
      } catch {
        return
      }
      if (payload.type === 'hello') {
        window.clearTimeout(authTimer)
        wsState.value = 'live'
        reconnectAttempt = 0
        stopPolling()
        return
      }
      if (payload.type === 'message') handleIncoming(payload.data)
      else if (payload.type === 'read') handleReadEvent(payload.data)
      else if (payload.type === 'collector' && status.value) status.value.collector = payload.data
    }

    socket.onerror = () => {
      try {
        socket.close()
      } catch {
        // ignore
      }
    }

    socket.onclose = () => {
      window.clearTimeout(authTimer)
      if (ws !== socket) return
      ws = null
      wsState.value = 'offline'
      if (authed.value) {
        startPolling()
        scheduleReconnect()
      }
    }
  }

  function startPolling() {
    if (pollTimer || !authed.value) return
    if (wsState.value === 'live') return
    wsState.value = 'polling'
    pollTimer = window.setInterval(async () => {
      await refreshStatus()
      await refreshMessages(true, true)
    }, 15000)
  }

  function stopPolling() {
    window.clearInterval(pollTimer)
    pollTimer = null
  }

  function startStatusTimer() {
    window.clearInterval(statusTimer)
    statusTimer = window.setInterval(refreshStatus, 30000)
  }

  function onVisibility() {
    if (!authed.value) return
    if (document.visibilityState === 'visible') {
      refreshStatus()
      if (wsState.value !== 'live') connectWS()
    }
  }

  function onOnline() {
    if (!authed.value) return
    connectWS()
    refreshAll()
  }

  async function start() {
    if (started || !authed.value) return
    started = true
    await Promise.allSettled([refreshStatus(), refreshMessages(true), refreshConversations()])
    if (!authed.value) return
    connectWS()
    startStatusTimer()
    window.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', onOnline)
  }

  function stop() {
    started = false
    window.clearTimeout(reconnectTimer)
    window.clearTimeout(qTimer)
    window.clearTimeout(authTimer)
    stopPolling()
    window.clearInterval(statusTimer)
    statusTimer = null
    window.removeEventListener('visibilitychange', onVisibility)
    window.removeEventListener('online', onOnline)
    if (ws) {
      const socket = ws
      ws = null
      try {
        socket.close()
      } catch {
        // ignore
      }
    }
    wsState.value = 'offline'
  }

  watch(
    () => [filters.source, filters.conversation, filters.unread],
    () => {
      if (!authed.value) return
      selected.value = new Set()
      refreshMessages(true)
    },
  )

  watch(
    () => filters.q,
    () => {
      if (!authed.value) return
      window.clearTimeout(qTimer)
      qTimer = window.setTimeout(() => {
        selected.value = new Set()
        refreshMessages(true)
      }, 350)
    },
  )

  watch(
    () => filters.source,
    () => {
      if (filters.conversation && !conversationsForSource.value.some((item) => item.conversation === filters.conversation)) {
        filters.conversation = ''
      }
    },
  )

  onBeforeUnmount(stop)

  return {
    authed,
    authError,
    authBusy,
    status,
    messages,
    conversations,
    conversationsForSource,
    currentConversationLabel,
    filters,
    selected,
    selectedCount,
    allVisibleUnreadSelected,
    loading,
    loadingMore,
    hasMore,
    error,
    wsState,
    wsLabel,
    toasts,
    totalUnread,
    todayUnread,
    todayCount,
    notifyEnabled,
    soundEnabled,
    login,
    logout,
    start,
    refreshAll,
    refreshStatus,
    refreshMessages,
    refreshConversations,
    loadMore,
    readMessage,
    toggleSelect,
    selectAllVisible,
    markSelectedRead,
    markAllVisibleRead,
    enableNotifications,
    disableNotifications,
    toggleSound,
    dismissToast,
    pushToast,
  }
}
