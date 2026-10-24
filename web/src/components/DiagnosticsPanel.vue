<script setup>
import { computed } from 'vue'
import { ageLabel, formatFullTime, ruleTypeLabel, truncate } from '../utils'

const props = defineProps({
  open: { type: Boolean, default: false },
  status: { type: Object, default: null },
  recentMessages: { type: Array, default: () => [] },
})
const emit = defineEmits(['close', 'refresh'])

const collector = computed(() => props.status?.collector || null)
const counters = computed(() => collector.value?.counters || {})
const rules = computed(() => collector.value?.rules || [])
const enabledRules = computed(() => rules.value.filter((rule) => rule.enabled))
const ruleSummary = computed(() => {
  const counts = { group: 0, contact: 0, keyword: 0 }
  for (const rule of enabledRules.value) {
    if (rule.type in counts) counts[rule.type] += 1
  }
  return counts
})

const counterItems = computed(() => [
  { key: 'notifications_seen', label: '收到通知', value: counters.value.notifications_seen ?? 0, hint: '系统通知条数，不是消息条数' },
  { key: 'matched', label: '命中规则', value: counters.value.matched ?? 0, hint: '已进入本地队列并上传' },
  { key: 'skipped_no_rule', label: '规则未命中', value: counters.value.skipped_no_rule ?? 0, hint: '在本机被过滤，内容没有上传' },
  { key: 'duplicates', label: '重复拦截', value: counters.value.duplicates ?? 0, hint: '同一条通知没有重复上传' },
  { key: 'parse_errors', label: '解析失败', value: counters.value.parse_errors ?? 0, hint: '通知缺少可用的会话或正文' },
  { key: 'summary_skipped', label: '折叠摘要跳过', value: counters.value.summary_skipped ?? 0, hint: '只收到系统折叠摘要，没有单条详情' },
])

const hints = computed(() => {
  const out = []
  if (!collector.value?.configured) {
    out.push({ tone: 'bad', text: '采集端从未上报心跳。先在手机上配置地址和 Token，并确认通知使用权。' })
    return out
  }
  if (collector.value.verdict === 'offline') {
    out.push({ tone: 'bad', text: `采集端离线，最后心跳在 ${ageLabel(collector.value.last_seen)}。检查省电策略、后台限制和网络。` })
  }
  if (collector.value.listener_connected === false) {
    out.push({ tone: 'bad', text: '通知监听未连接。到系统「通知使用权」里重新开启本应用。' })
  }
  if ((counters.value.notifications_seen ?? 0) === 0) {
    out.push({ tone: 'warn', text: '今天没有收到任何微信/QQ 通知。可能是通知没弹出、被免打扰折叠，或监听服务没生效。' })
  }
  if ((counters.value.skipped_no_rule ?? 0) > 0) {
    out.push({ tone: 'warn', text: `有 ${counters.value.skipped_no_rule} 条通知/消息被本地规则过滤。若其中包含想收的消息，检查群名/联系人是否完全一致、关键词是否开启。` })
  }
  if ((counters.value.parse_errors ?? 0) > 0) {
    out.push({ tone: 'warn', text: `有 ${counters.value.parse_errors} 条通知无法提取会话或正文。群免打扰且不显示详情时，系统通知本身就没有内容。` })
  }
  if ((counters.value.summary_skipped ?? 0) > 0) {
    out.push({ tone: 'warn', text: `跳过了 ${counters.value.summary_skipped} 条折叠摘要。摘要通常不含完整内容和准确会话，不能可靠归属。` })
  }
  if ((collector.value.queue_size ?? 0) > 0) {
    out.push({ tone: 'warn', text: `手机本地还有 ${collector.value.queue_size} 条待补传。恢复网络后会自动上传，也可在采集端点「立即重试」。` })
  }
  if (enabledRules.value.length === 0) {
    out.push({ tone: 'bad', text: '没有启用的规则，所以采集端不会上传任何消息。这是有意的隐私设计。' })
  }
  if (props.status?.stale?.no_recent_messages && collector.value.verdict === 'ok') {
    out.push({ tone: 'info', text: '监听在线但长时间没有命中消息。先看「规则未命中」计数，再看微信/QQ 是否把该群设为免打扰且关闭了通知详情。' })
  }
  if (!out.length) out.push({ tone: 'ok', text: '当前没有发现明显异常。' })
  return out
})

const recentHits = computed(() => props.recentMessages.filter((message) => (message.matched_rules || []).length).slice(0, 6))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="drawer-layer" @click.self="emit('close')">
      <aside class="drawer diagnostics" role="dialog" aria-modal="true" aria-label="采集诊断">
        <header class="drawer-head">
          <div>
            <p class="eyebrow">Diagnostics</p>
            <h2>采集诊断</h2>
          </div>
          <button type="button" class="icon-btn" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="drawer-body">
          <section class="diag-section">
            <div class="section-head">
              <h3>采集端状态</h3>
              <button type="button" class="link-btn" @click="emit('refresh')">刷新</button>
            </div>
            <div class="kv-grid">
              <div><span>设备</span><strong>{{ collector?.device_id || '未上报' }}</strong></div>
              <div><span>版本</span><strong>{{ collector?.app_version || '—' }}</strong></div>
              <div><span>最后心跳</span><strong>{{ collector?.last_seen ? ageLabel(collector.last_seen) : '从未' }}</strong></div>
              <div><span>最后通知</span><strong>{{ collector?.last_notification_at ? ageLabel(collector.last_notification_at) : '从未' }}</strong></div>
              <div><span>待补传</span><strong>{{ collector?.queue_size ?? 0 }} 条</strong></div>
              <div><span>电池优化</span><strong>{{ collector?.battery_optimization_ignored ? '已忽略' : '未忽略' }}</strong></div>
            </div>
            <p v-if="collector?.last_seen" class="muted small">最后心跳：{{ formatFullTime(collector.last_seen) }}</p>
          </section>

          <section class="diag-section">
            <h3>今日计数</h3>
            <div class="counter-grid">
              <div v-for="item in counterItems" :key="item.key" class="counter-card">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <small>{{ item.hint }}</small>
              </div>
            </div>
            <p class="muted small">计数由手机本地维护；只上传数字、时间和规则类型，不包含消息内容、群名或联系人。</p>
          </section>

          <section class="diag-section">
            <h3>漏消息排查</h3>
            <ul class="hint-list">
              <li v-for="(hint, index) in hints" :key="index" :class="`hint-${hint.tone}`">
                <span class="hint-dot" aria-hidden="true"></span>{{ hint.text }}
              </li>
            </ul>
          </section>

          <section class="diag-section">
            <div class="section-head">
              <h3>启用的规则（{{ enabledRules.length }}）</h3>
            </div>
            <div v-if="enabledRules.length" class="rule-cloud">
              <span v-for="(count, type) in ruleSummary" :key="type" class="rule-tag" :class="{ disabled: !count }">
                <em>{{ ruleTypeLabel(type) }}</em>{{ count }} 条
              </span>
            </div>
            <p v-else-if="rules.length" class="muted">规则全部关闭，采集端不会上传消息。</p>
            <p v-else class="muted">采集端还没有上报规则。先在手机上添加规则。</p>
            <p class="muted small">规则的具体群名、联系人和关键词只保存在手机；只有命中消息时，命中项才会随消息上传。</p>
          </section>

          <section class="diag-section">
            <h3>最近命中</h3>
            <div v-if="recentHits.length" class="hit-list">
              <div v-for="message in recentHits" :key="message.id" class="hit-row">
                <div>
                  <strong>{{ message.conversation }}</strong>
                  <small>{{ formatFullTime(message.message_time) }} · {{ truncate(message.content, 36) }}</small>
                </div>
                <span class="hit-rules">{{ (message.matched_rules || []).map((rule) => rule.pattern).join(' / ') }}</span>
              </div>
            </div>
            <p v-else class="muted">当前列表里还没有带规则信息的消息。</p>
          </section>

          <section class="diag-section privacy-note">
            <h3>隐私边界</h3>
            <p>采集端只读取 Android 系统通知副本，不登录微信/QQ、不注入、不逆向协议。未命中规则的正文不会离开手机；本页的群名和内容只来自已上传的命中消息。</p>
          </section>
        </div>
      </aside>
    </div>
  </Teleport>
</template>
