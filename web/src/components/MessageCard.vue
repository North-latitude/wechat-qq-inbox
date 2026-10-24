<script setup>
import { computed } from 'vue'
import SourceBadge from './SourceBadge.vue'
import { formatFullTime, formatTime, ruleTypeLabel } from '../utils'

const props = defineProps({
  message: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})
const emit = defineEmits(['select', 'read'])

const rules = computed(() => props.message.matched_rules || [])
const senderVisible = computed(
  () => props.message.sender && props.message.sender !== props.message.conversation,
)
</script>

<template>
  <article class="message-card" :class="[{ unread: !message.is_read, selected }, `from-${message.source}`]">
    <div class="select-col">
      <input
        type="checkbox"
        :checked="selected"
        :aria-label="`选择来自${message.conversation}的消息`"
        @change="emit('select')"
      />
    </div>
    <div class="message-main">
      <header class="message-head">
        <SourceBadge :source="message.source" />
        <h3 class="conversation">{{ message.conversation }}</h3>
        <span v-if="senderVisible" class="sender">{{ message.sender }}</span>
        <time class="message-time" :title="formatFullTime(message.message_time)">{{ formatTime(message.message_time) }}</time>
      </header>

      <p class="message-content">{{ message.content }}</p>

      <div v-if="rules.length" class="rule-chips" aria-label="命中的规则">
        <span v-for="rule in rules" :key="`${rule.id}-${rule.pattern}`" class="rule-chip">
          <span class="rule-type">{{ ruleTypeLabel(rule.type) }}</span>{{ rule.pattern }}
        </span>
      </div>

      <footer class="message-foot">
        <span class="read-state" :class="{ unread: !message.is_read }">
          <span class="read-dot"></span>{{ message.is_read ? '已读' : '未读' }}
        </span>
        <button v-if="!message.is_read" type="button" class="link-btn" @click="emit('read')">标为已读</button>
        <span class="message-id">#{{ message.id }}</span>
      </footer>
    </div>
  </article>
</template>
