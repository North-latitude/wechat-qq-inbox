export function sourceLabel(source) {
  return source === 'wechat' ? '微信' : source === 'qq' ? 'QQ' : '未知'
}

export function ruleTypeLabel(type) {
  return { group: '群名', contact: '联系人', keyword: '关键词' }[type] || type
}

export function parseDate(iso) {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatTime(iso) {
  const date = parseDate(iso)
  if (!date) return '—'
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) {
    return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(date)
  }
  const sameYear = date.getFullYear() === now.getFullYear()
  return new Intl.DateTimeFormat('zh-CN', {
    year: sameYear ? undefined : 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatFullTime(iso) {
  const date = parseDate(iso)
  if (!date) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

export function fromNow(seconds) {
  if (seconds === null || seconds === undefined) return '未知'
  if (seconds < 10) return '刚刚'
  if (seconds < 60) return `${seconds} 秒前`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

export function ageLabel(iso) {
  const date = parseDate(iso)
  if (!date) return '未知'
  return fromNow(Math.max(0, Math.round((Date.now() - date.getTime()) / 1000)))
}

export function truncate(text, length = 100) {
  if (!text) return ''
  return text.length > length ? `${text.slice(0, length)}…` : text
}

export function safeText(value) {
  return typeof value === 'string' ? value : ''
}
