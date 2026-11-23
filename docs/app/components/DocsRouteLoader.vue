<script setup lang="ts">
defineOptions({ name: 'DocsRouteLoader' })

const { isLoading } = useLoadingIndicator()
</script>

<template>
  <Teleport to="body">
    <Transition name="docs-route">
      <div
        v-if="isLoading"
        class="route"
      >
        <DocsLoader
          compact
          label="正在加载"
        />
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.route {
  position: fixed;
  z-index: 40;
  top: calc(var(--ui-header-height, 64px) + 16px);
  left: 50%;
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 8px 16px;
  border: 1px solid color-mix(in oklch, var(--ui-primary) 32%, var(--ui-border));
  border-radius: 16px;
  background: var(--ui-bg);
  transform: translateX(-50%);
  pointer-events: none;
}

.docs-route-enter-active,
.docs-route-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.docs-route-enter-from,
.docs-route-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}

@media (prefers-reduced-motion: reduce) {
  .docs-route-enter-active,
  .docs-route-leave-active {
    transition: none;
  }
}
</style>
