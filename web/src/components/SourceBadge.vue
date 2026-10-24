<script setup>
import { computed } from 'vue'
import { sourceLabel } from '../utils'

const props = defineProps({ source: { type: String, required: true } })
const label = computed(() => sourceLabel(props.source))
</script>

<template>
  <span class="source-badge" :class="`source-${source}`">
    <span class="source-mark">{{ source === 'wechat' ? '微' : 'Q' }}</span>
    <span>{{ label }}</span>
  </span>
</template>
