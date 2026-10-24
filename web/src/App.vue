<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import FilterBar from './components/FilterBar.vue'
import MessageCard from './components/MessageCard.vue'
import StaleBanner from './components/StaleBanner.vue'
import StatusStrip from './components/StatusStrip.vue'
import DiagnosticsPanel from './components/DiagnosticsPanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import ToastStack from './components/ToastStack.vue'
import { useInbox } from './useInbox'

const {
  authed,
  authError,
  authBusy,
  status,
  messages,
  conversationsForSource,
  filters,
  selected,
  selectedCount,
  allVisibleUnreadSelected,
  loading,
  loadingMore,
  hasMore,
  error,
  wsLabel,
  wsState,
  toasts,
  totalUnread,
  todayUnread,
  notifyEnabled,
  soundEnabled,
  login,
  logout,
  start,
  refreshAll,
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
} = useInbox()

const tokenInput = ref('')
const diagOpen = ref(false)
const settingsOpen = ref(false)
const installEvent = ref(null)
const canInstall = ref(false)

const sourceTabs = [
  { value: '', label: '全部' },
  { value: 'wechat', label: '微信' },
  { value: 'qq', label: 'QQ' },
]

const unreadBySource = computed(() => {
  const map = { '': totalUnread.value, wechat: 0, qq: 0 }
  for (const item of conversationsForSource.value) {
    const source = item.source
    if (source === 'wechat' || source === 'qq') map[source] += item.unread_count || 0
  }
  return map
})

const hasFilters = computed(
  () => Boolean(filters.source || filters.conversation || filters.q || filters.unread),
)

const emptyTitle = computed(() => {
  if (status.value?.collector?.rules_count === 0) return '还没有配置规则'
  if (hasFilters.value) return '当前筛选下没有消息'
  return '收件箱是空的'
})

const emptyDetail = computed(() => {
  if (status.value?.collector?.rules_count === 0) {
    return '这是有意的隐私设计：没有规则就不会上传任何消息。请在手机上添加群名、联系人或关键词规则。'
  }
  if (hasFilters.value) return '清空筛选或搜索词，看看其他消息。'
  return '等第一条命中规则的消息。也可以打开诊断，确认采集端是否在线、通知是否真的到达。'
})

const selectedIds = computed(() => selected.value)

async function submitLogin() {
  const ok = await login(tokenInput.value)
  if (ok) {
    tokenInput.value = ''
    pushToast('已连接采集端', 'success')
  }
}

async function changeToken(value) {
  const ok = await login(value)
  if (ok) {
    settingsOpen.value = false
    pushToast('Token 已更新', 'success')
  } else {
    pushToast(authError.value || 'Token 校验失败', 'error')
  }
}

function confirmLogout() {
  if (!window.confirm('清除本机保存的 Token 并退出？服务器上的消息不会被删除。')) return
  logout()
  settingsOpen.value = false
  diagOpen.value = false
}

function confirmMarkAll() {
  if (!totalUnread.value) return
  const scope = hasFilters.value ? '当前筛选条件' : '全部'
  if (!window.confirm(`把${scope}的未读消息都标为已读？未加载到列表里的消息也会被标记。`)) return
  markAllVisibleRead()
}

function clearFilters() {
  filters.source = ''
  filters.conversation = ''
  filters.q = ''
  filters.unread = false
}

async function installPwa() {
  if (!installEvent.value) return
  installEvent.value.prompt()
  try {
    await installEvent.value.userChoice
  } finally {
    installEvent.value = null
    canInstall.value = false
  }
}

function onBeforeInstall(event) {
  event.preventDefault()
  installEvent.value = event
  canInstall.value = true
}

function onInstalled() {
  installEvent.value = null
  canInstall.value = false
  pushToast('已安装到桌面', 'success')
}

watch(totalUnread, (value) => {
  document.title = value > 0 ? `(${value}) 统一收件箱` : '统一收件箱'
})

onMounted(() => {
  window.addEventListener('beforeinstallprompt', onBeforeInstall)
  window.addEventListener('appinstalled', onInstalled)
  if (authed.value) start()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeinstallprompt', onBeforeInstall)
  window.removeEventListener('appinstalled', onInstalled)
})
</script>

<template>
  <div v-if="!authed" class="auth-page">
    <div class="auth-orbit orbit-one"></div>
    <div class="auth-orbit orbit-two"></div>
    <main class="auth-card">
      <div class="brand large">
        <img src="/icons/icon.svg" alt="" width="44" height="44" />
        <div>
          <span class="brand-kicker">PERSONAL INBOX</span>
          <h1>统一收件箱</h1>
        </div>
      </div>
      <p class="auth-lead">把特定微信群、QQ 群、联系人和关键词消息收进同一页。只读取系统通知，不登录、不注入、不碰协议。</p>

      <form class="auth-form" @submit.prevent="submitLogin">
        <label for="token">访问 Token</label>
        <input
          id="token"
          v-model="tokenInput"
          type="password"
          autocomplete="current-password"
          placeholder="粘贴 APP_TOKEN"
          :disabled="authBusy"
        />
        <button type="submit" class="primary-btn" :disabled="authBusy || !tokenInput.trim()">
          {{ authBusy ? '正在验证…' : '进入收件箱' }}
        </button>
        <p v-if="authError" class="form-error" role="alert">{{ authError }}</p>
      </form>

      <ol class="auth-steps">
        <li><span>1</span>在电脑上启动后端服务。</li>
        <li><span>2</span>打开 <code>backend/data/token.txt</code> 复制 Token。</li>
        <li><span>3</span>粘贴到上面。Token 只保存在本机浏览器。</li>
      </ol>

      <p class="auth-security">仅限本机/私有网络使用。生产环境请通过 HTTPS 访问，不要把 Token 发到公开群或截图里。</p>
    </main>
    <ToastStack :toasts="toasts" @dismiss="dismissToast" />
  </div>

  <div v-else class="app-shell">
    <header class="topbar">
      <div class="brand">
        <img src="/icons/icon.svg" alt="" width="34" height="34" />
        <div class="brand-text">
          <span class="brand-kicker">UNIFIED INBOX</span>
          <h1>统一收件箱</h1>
        </div>
      </div>
      <div class="top-actions">
        <span class="ws-pill" :class="`ws-${wsState}`" :title="`实时连接状态：${wsLabel}`">
          <span class="ws-dot"></span><span class="ws-text">{{ wsLabel }}</span>
        </span>
        <button type="button" class="icon-btn" title="刷新" aria-label="刷新" @click="refreshAll">⟳</button>
        <button type="button" class="ghost" @click="diagOpen = true">诊断</button>
        <button type="button" class="ghost" @click="settingsOpen = true">设置</button>
      </div>
    </header>

    <main class="page">
      <section class="overview">
        <div class="overview-copy">
          <p class="eyebrow">TODAY</p>
          <h2>今天需要留意的，都在这一页。</h2>
          <p class="muted">只显示命中规则的微信 / QQ 通知。没命中就不上传，也不出现在这里。</p>
        </div>
        <StatusStrip :status="status" />
      </section>

      <StaleBanner :status="status" @diagnostics="diagOpen = true" />

      <div class="workspace">
        <aside class="sidebar">
          <div class="side-block">
            <p class="side-label">来源</p>
            <div class="source-tabs" role="tablist" aria-label="按来源筛选">
              <button
                v-for="tab in sourceTabs"
                :key="tab.value"
                type="button"
                role="tab"
                :aria-selected="filters.source === tab.value"
                :class="{ active: filters.source === tab.value }"
                @click="filters.source = tab.value"
              >
                <span>{{ tab.label }}</span>
                <em v-if="unreadBySource[tab.value]">{{ unreadBySource[tab.value] }}</em>
              </button>
            </div>
          </div>

          <div class="side-block side-summary">
            <p class="side-label">今日概览</p>
            <div class="mini-stat"><span>命中并上传</span><strong>{{ status?.messages?.today_count ?? 0 }}</strong></div>
            <div class="mini-stat"><span>今日未读</span><strong class="accent-text">{{ todayUnread }}</strong></div>
            <div class="mini-stat"><span>采集端规则</span><strong>{{ status?.collector?.rules_count ?? 0 }}</strong></div>
          </div>

          <div class="side-block">
            <button type="button" class="diagnose-btn" @click="diagOpen = true">
              <span class="diagnose-icon">?</span>
              <span><strong>漏消息了？</strong><small>看采集端在线状态和规则计数</small></span>
            </button>
          </div>

          <p class="side-note">通知监听是唯一采集方式。没有规则，就没有上传。</p>
        </aside>

        <section class="inbox-panel">
          <FilterBar
            :filters="filters"
            :conversations="conversationsForSource"
            :loading="loading"
            :message-count="messages.length"
            @update:q="filters.q = $event"
            @update:conversation="filters.conversation = $event"
            @update:unread="filters.unread = $event"
            @refresh="refreshAll"
          />

          <div class="bulk-bar">
            <label class="select-all">
              <input
                type="checkbox"
                :checked="allVisibleUnreadSelected"
                :disabled="!messages.some((item) => !item.is_read)"
                @change="selectAllVisible"
              />
              全选未读
            </label>
            <span v-if="selectedCount" class="selection-count">{{ selectedCount }} 条已选</span>
            <span v-else class="muted small">勾选后可批量操作</span>
            <div class="bulk-actions">
              <button type="button" class="ghost small" :disabled="!selectedCount" @click="markSelectedRead">
                标记已选
              </button>
              <button type="button" class="ghost small" :disabled="!totalUnread" @click="confirmMarkAll">
                全部已读
              </button>
            </div>
          </div>

          <div v-if="error" class="error-banner" role="alert">
            <span>{{ error }}</span>
            <button type="button" class="link-btn" @click="refreshAll">重试</button>
          </div>

          <div v-if="loading" class="message-list" aria-busy="true">
            <div v-for="n in 4" :key="n" class="message-skeleton">
              <span class="sk-line short"></span>
              <span class="sk-line"></span>
              <span class="sk-line medium"></span>
            </div>
          </div>

          <div v-else-if="!messages.length" class="empty-state">
            <div class="empty-mark" aria-hidden="true"><span></span><span></span></div>
            <h3>{{ emptyTitle }}</h3>
            <p>{{ emptyDetail }}</p>
            <div class="empty-actions">
              <button v-if="hasFilters" type="button" class="ghost" @click="clearFilters">清空筛选</button>
              <button type="button" class="primary-btn small" @click="diagOpen = true">打开诊断</button>
            </div>
          </div>

          <div v-else class="message-list">
            <MessageCard
              v-for="message in messages"
              :key="message.id"
              :message="message"
              :selected="selectedIds.has(message.id)"
              @select="toggleSelect(message.id)"
              @read="readMessage(message)"
            />
          </div>

          <button v-if="hasMore" type="button" class="load-more" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
        </section>
      </div>
    </main>

    <DiagnosticsPanel
      :open="diagOpen"
      :status="status"
      :recent-messages="messages"
      @close="diagOpen = false"
      @refresh="refreshAll"
    />
    <SettingsPanel
      :open="settingsOpen"
      :notify-enabled="notifyEnabled"
      :sound-enabled="soundEnabled"
      :can-install="canInstall"
      @close="settingsOpen = false"
      @logout="confirmLogout"
      @enable-notifications="enableNotifications"
      @disable-notifications="disableNotifications"
      @toggle-sound="toggleSound"
      @change-token="changeToken"
      @install="installPwa"
    />
    <ToastStack :toasts="toasts" @dismiss="dismissToast" />
  </div>
</template>
