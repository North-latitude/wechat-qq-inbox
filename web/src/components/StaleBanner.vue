<script setup>
import { computed } from 'vue'
import { ageLabel } from '../utils'

const props = defineProps({ status: { type: Object, default: null } })
const emit = defineEmits(['diagnostics'])

const collector = computed(() => props.status?.collector || null)
const stale = computed(() => props.status?.stale || null)

const alert = computed(() => {
  if (!props.status) return null
  if (!collector.value?.configured) {
    return {
      tone: 'warn',
      title: '采集端还没有连接',
      detail: '在手机上填好服务器地址和 Token，并确认通知使用权已开启。',
    }
  }
  if (collector.value.verdict === 'offline') {
    return {
      tone: 'bad',
      title: '采集端可能掉线',
      detail: `最后一次心跳在 ${ageLabel(collector.value.last_seen)}。系统可能杀掉了采集进程，或手机断网、App 被强制停止。`,
    }
  }
  if (collector.value.verdict === 'listener_disconnected') {
    return {
      tone: 'warn',
      title: '通知监听已断开',
      detail: '采集端仍能上报心跳，但通知监听服务没有连接。请在手机上重新开启通知使用权。',
    }
  }
  if (stale.value?.no_recent_messages) {
    const last = props.status.messages?.last_message_at
    return {
      tone: 'warn',
      title: '很久没有收到命中消息',
      detail: last
        ? `监听在线，但已经 ${ageLabel(last)} 没有命中消息。可能是规则没命中、群免打扰不显示详情，或确实没有新消息。`
        : '监听在线，但还没有收到过命中消息。先检查规则和通知详情设置。',
    }
  }
  return null
})
</script>

<template>
  <div v-if="alert" class="stale-banner" :class="`tone-${alert.tone}`" role="status">
    <div class="stale-icon" aria-hidden="true">!</div>
    <div class="stale-copy">
      <strong>{{ alert.title }}</strong>
      <span>{{ alert.detail }}</span>
    </div>
    <button type="button" class="ghost small" @click="emit('diagnostics')">查看原因</button>
  </div>
</template>
