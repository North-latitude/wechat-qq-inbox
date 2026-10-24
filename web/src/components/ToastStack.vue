<script setup>
defineProps({ toasts: { type: Array, default: () => [] } })
const emit = defineEmits(['dismiss'])
</script>

<template>
  <Teleport to="body">
    <div class="toast-stack" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="toast">
        <div v-for="toast in toasts" :key="toast.id" class="toast" :class="`toast-${toast.type}`">
          <span>{{ toast.text }}</span>
          <button type="button" aria-label="关闭提示" @click="emit('dismiss', toast.id)">×</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
