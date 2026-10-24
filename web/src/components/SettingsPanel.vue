<script setup>
import { ref } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  notifyEnabled: { type: Boolean, default: false },
  soundEnabled: { type: Boolean, default: false },
  canInstall: { type: Boolean, default: false },
})
const emit = defineEmits([
  'close',
  'logout',
  'enable-notifications',
  'disable-notifications',
  'toggle-sound',
  'change-token',
  'install',
])

const newToken = ref('')
const notificationSupported = typeof window !== 'undefined' && 'Notification' in window
const permission = notificationSupported ? Notification.permission : 'unsupported'

function submitToken() {
  if (!newToken.value.trim()) return
  emit('change-token', newToken.value.trim())
  newToken.value = ''
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="drawer-layer" @click.self="emit('close')">
      <aside class="drawer settings" role="dialog" aria-modal="true" aria-label="设置">
        <header class="drawer-head">
          <div>
            <p class="eyebrow">Settings</p>
            <h2>设置</h2>
          </div>
          <button type="button" class="icon-btn" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="drawer-body">
          <section class="diag-section">
            <h3>浏览器通知</h3>
            <p class="muted small">只选这一种提示方式。通知仅在页面打开（或 PWA 在后台仍存活）时有效；本项目没有 Web Push 服务。</p>
            <div class="setting-row">
              <div>
                <strong>{{ notifyEnabled ? '已开启' : '已关闭' }}</strong>
                <small v-if="permission === 'denied'">浏览器已拒绝通知权限，请在站点设置里恢复。</small>
                <small v-else-if="permission === 'unsupported'">当前浏览器不支持通知。</small>
              </div>
              <button
                v-if="!notifyEnabled"
                type="button"
                class="primary-btn small"
                :disabled="permission === 'denied' || permission === 'unsupported'"
                @click="emit('enable-notifications')"
              >
                开启
              </button>
              <button v-else type="button" class="ghost small" @click="emit('disable-notifications')">关闭</button>
            </div>
            <div class="setting-row">
              <div>
                <strong>提示音</strong>
                <small>新消息到达时播放一声轻提示，仅页面打开时。</small>
              </div>
              <label class="switch">
                <input type="checkbox" :checked="soundEnabled" @change="emit('toggle-sound')" />
                <span></span>
              </label>
            </div>
          </section>

          <section class="diag-section">
            <h3>安装到桌面</h3>
            <p class="muted small">安装后可以像 App 一样打开，仍通过浏览器访问同一套本地服务。</p>
            <button type="button" class="ghost" :disabled="!canInstall" @click="emit('install')">
              {{ canInstall ? '安装 PWA' : '当前浏览器未提供安装入口' }}
            </button>
          </section>

          <section class="diag-section">
            <h3>更换 Token</h3>
            <p class="muted small">Token 存在本机浏览器里，等同于密码。更换后旧 Token 立即失效。</p>
            <form class="token-form" @submit.prevent="submitToken">
              <input
                v-model="newToken"
                type="password"
                autocomplete="off"
                placeholder="输入新的 APP_TOKEN"
                aria-label="新的 APP_TOKEN"
              />
              <button type="submit" class="primary-btn" :disabled="!newToken.trim()">验证并保存</button>
            </form>
          </section>

          <section class="diag-section">
            <h3>安全说明</h3>
            <ul class="plain-list">
              <li>只读取 Android 系统通知，不登录、不扫码、不注入、不碰协议。</li>
              <li>只有命中本地规则的消息会上传；其余消息连正文都不会写入本机数据库。</li>
              <li>生产环境请只通过 HTTPS 暴露服务，并给 Token 使用足够长的随机值。</li>
              <li>不要把 Token 发到群里或截图里。</li>
            </ul>
          </section>

          <section class="diag-section danger-zone">
            <button type="button" class="danger-btn" @click="emit('logout')">清除本机 Token 并退出</button>
            <p class="muted small">不会删除服务器上的消息；重新输入 Token 即可恢复。</p>
          </section>
        </div>
      </aside>
    </div>
  </Teleport>
</template>
