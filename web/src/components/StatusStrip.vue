<script setup>
import { computed } from 'vue'
import { ageLabel, formatFullTime } from '../utils'

const props = defineProps({ status: { type: Object, default: null } })

const collector = computed(() => props.status?.collector || null)
const verdict = computed(() => collector.value?.verdict || 'never_seen')
const verdictText = computed(
  () =>
    ({
      ok: '在线',
      offline: '离线',
      listener_disconnected: '监听断开',
      never_seen: '未连接',
    })[verdict.value] || '未知',
)
const verdictClass = computed(() => (verdict.value === 'ok' ? 'ok' : verdict.value === 'never_seen' ? 'muted' : 'bad'))
const lastSeen = computed(() =>
  collector.value?.last_seen ? ageLabel(collector.value.last_seen) : '还没有心跳',
)
const lastMessage = computed(() => {
  if (!props.status?.messages?.last_message_at) return '还没有消息'
  return ageLabel(props.status.messages.last_message_at)
})
const lastMessageTitle = computed(() =>
  props.status?.messages?.last_message_at ? formatFullTime(props.status.messages.last_message_at) : '',
)
</script>

<template>
  <div class="status-strip">
    <article class="status-card">
      <div class="status-label">采集端</div>
      <div class="status-value">
        <span class="status-dot" :class="verdictClass"></span>
        {{ verdictText }}
      </div>
      <div class="status-meta" :title="collector?.last_seen || ''">最后心跳：{{ lastSeen }}</div>
    </article>
    <article class="status-card">
      <div class="status-label">最后收到消息</div>
      <div class="status-value" :title="lastMessageTitle">{{ lastMessage }}</div>
      <div class="status-meta">
        {{ status?.messages?.today_count ?? 0 }} 条今日消息
      </div>
    </article>
    <article class="status-card accent">
      <div class="status-label">今日未读</div>
      <div class="status-value big">{{ status?.messages?.today_unread ?? 0 }}</div>
      <div class="status-meta">全部未读 {{ status?.messages?.total_unread ?? 0 }} 条</div>
    </article>
  </div>
</template>
