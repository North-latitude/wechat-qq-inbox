<script setup>
import { computed } from 'vue'

const props = defineProps({
  filters: { type: Object, required: true },
  conversations: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  messageCount: { type: Number, default: 0 },
})

const emit = defineEmits(['update:q', 'update:conversation', 'update:unread', 'refresh'])

const conversationOptions = computed(() => {
  const seen = new Set()
  return props.conversations.filter((item) => {
    const key = `${item.source}:${item.conversation}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
})

function changeConversation(event) {
  emit('update:conversation', event.target.value)
}
</script>

<template>
  <div class="filter-bar">
    <div class="search-box">
      <span class="search-icon" aria-hidden="true">⌕</span>
      <input
        type="search"
        :value="filters.q"
        placeholder="搜索内容、群名、联系人"
        aria-label="搜索内容、群名、联系人"
        @input="emit('update:q', $event.target.value)"
      />
      <button v-if="filters.q" type="button" class="clear-search" aria-label="清空搜索" @click="emit('update:q', '')">
        ×
      </button>
    </div>

    <select class="conversation-select" :value="filters.conversation" aria-label="按会话筛选" @change="changeConversation">
      <option value="">全部会话</option>
      <option v-for="item in conversationOptions" :key="`${item.source}-${item.conversation}`" :value="item.conversation">
        {{ item.source === 'wechat' ? '微信' : 'QQ' }} · {{ item.conversation }}{{ item.unread_count ? ` (${item.unread_count})` : '' }}
      </option>
    </select>

    <label class="check-pill">
      <input type="checkbox" :checked="filters.unread" @change="emit('update:unread', $event.target.checked)" />
      只看未读
    </label>

    <span class="result-count">{{ messageCount }} 条</span>

    <button type="button" class="refresh-btn" :class="{ spinning: loading }" :disabled="loading" @click="emit('refresh')">
      <span aria-hidden="true">⟳</span> 刷新
    </button>
  </div>
</template>
