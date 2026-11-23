<script setup lang="ts">
defineOptions({ name: 'DocsLoader' })

withDefaults(defineProps<{
  label?: string
  compact?: boolean
}>(), {
  label: '正在加载',
  compact: false,
})
</script>

<template>
  <div
    class="loader"
    :class="{ 'is-compact': compact }"
    role="status"
    aria-live="polite"
  >
    <svg
      class="mark"
      viewBox="0 0 800 480"
      aria-hidden="true"
    >
      <path
        class="face"
        fill="currentColor"
        d="M0 0H600L560 160H160V320H440L400 480H0Z"
      />
      <path
        class="edge"
        fill="currentColor"
        d="M480 160H800L760 320H440Z"
      />
    </svg>
    <p class="label">
      {{ label }}
    </p>
    <div
      v-if="!compact"
      class="lines"
      aria-hidden="true"
    >
      <span />
      <span />
      <span />
    </div>
  </div>
</template>

<style scoped>
.loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px 16px;
  color: var(--ui-text);
}

.is-compact {
  flex-direction: row;
  gap: 8px;
  padding: 0;
}

.mark {
  width: 32px;
  height: 24px;
  flex: none;
  color: var(--ui-primary);
}

.face,
.edge {
  transform-box: fill-box;
  transform-origin: center;
}

.face {
  animation: docs-face 1.6s ease-in-out infinite;
}

.edge {
  animation: docs-edge 1.6s ease-in-out infinite;
}

.label,
.lines {
  margin: 0;
}

.label {
  font-size: 16px;
  line-height: 24px;
}

.lines {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 144px;
}

.lines span {
  display: block;
  height: 8px;
  border-radius: 8px;
  background: color-mix(in oklch, var(--ui-primary) 42%, var(--ui-bg));
  transform-origin: left center;
  animation: docs-line 1.6s ease-in-out infinite;
}

.lines span:nth-child(1) {
  width: 144px;
}

.lines span:nth-child(2) {
  width: 96px;
  animation-delay: 0.15s;
}

.lines span:nth-child(3) {
  width: 128px;
  animation-delay: 0.3s;
}

@media (prefers-reduced-motion: reduce) {
  .face,
  .edge,
  .lines span {
    animation: none;
  }

  .face {
    opacity: 1;
  }

  .edge {
    opacity: 0.72;
  }
}

@keyframes docs-face {
  0%,
  100% {
    opacity: 0.4;
  }

  40% {
    opacity: 1;
  }
}

@keyframes docs-edge {
  0%,
  100% {
    opacity: 0.28;
  }

  60% {
    opacity: 0.9;
  }
}

@keyframes docs-line {
  0%,
  100% {
    opacity: 0.45;
    transform: scaleX(0.4);
  }

  50% {
    opacity: 1;
    transform: scaleX(1);
  }
}
</style>
