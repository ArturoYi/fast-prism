<script setup lang="ts">
import type { SdkEntry } from '#shared/sdks'

defineOptions({ name: 'SdkCard' })

const props = defineProps<{
  sdk: SdkEntry
}>()

const MAX_TILT = 12

const { localePath } = useDocusI18n()

const rx = ref(0)
const ry = ref(0)
const hot = ref(false)
const reduceMotion = ref(false)
const finePointer = ref(false)

const docTo = computed(() => localePath(props.sdk.docPath))

const tiltStyle = computed(() => ({
  transform: `rotateX(${rx.value}deg) rotateY(${ry.value}deg)`,
  transition: hot.value || reduceMotion.value ? 'none' : 'transform 0.4s ease',
}))

let reduceQuery: MediaQueryList | null = null
let fineQuery: MediaQueryList | null = null
let resetFrame = 0

function syncInput() {
  reduceMotion.value = !!reduceQuery?.matches
  finePointer.value = !!fineQuery?.matches
  if (reduceMotion.value || !finePointer.value) settle()
}

function settle() {
  cancelAnimationFrame(resetFrame)
  resetFrame = 0
  hot.value = false
  rx.value = 0
  ry.value = 0
}

function resetTilt() {
  hot.value = false
  cancelAnimationFrame(resetFrame)
  // 先恢复 transition，下一帧再回到 0，离开时的 0.4s 才会生效。
  resetFrame = requestAnimationFrame(() => {
    resetFrame = 0
    if (hot.value) return
    rx.value = 0
    ry.value = 0
  })
}

function onMove(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || reduceMotion.value || !finePointer.value) return
  cancelAnimationFrame(resetFrame)
  const stage = event.currentTarget
  if (!(stage instanceof HTMLElement)) return
  const box = stage.getBoundingClientRect()
  if (box.width <= 0 || box.height <= 0) return
  const px = (event.clientX - box.left) / box.width - 0.5
  const py = (event.clientY - box.top) / box.height - 0.5
  ry.value = clamp(px, -0.5, 0.5) * -2 * MAX_TILT
  rx.value = clamp(py, -0.5, 0.5) * 2 * MAX_TILT
  hot.value = true
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

onMounted(() => {
  reduceQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  fineQuery = window.matchMedia('(hover: hover) and (pointer: fine)')
  reduceQuery.addEventListener('change', syncInput)
  fineQuery.addEventListener('change', syncInput)
  syncInput()
})

onUnmounted(() => {
  cancelAnimationFrame(resetFrame)
  reduceQuery?.removeEventListener('change', syncInput)
  fineQuery?.removeEventListener('change', syncInput)
})
</script>

<template>
  <article
    class="stage"
    @pointermove="onMove"
    @pointerleave="resetTilt"
  >
    <div
      class="tilt"
      :class="{ 'is-hot': hot }"
      :style="tiltStyle"
    >
      <div
        class="halo"
        aria-hidden="true"
      >
        <div class="halo-clip">
          <div class="spin">
            <div class="disc" />
          </div>
        </div>
      </div>
      <div
        class="ring"
        aria-hidden="true"
      >
        <!-- 转盘比卡片对角线更大，圆角裁切时不会露出方边。 -->
        <div class="spin">
          <div class="disc" />
        </div>
      </div>
      <div class="face">
        <div class="head">
          <h2 class="name">
            {{ sdk.name }}
          </h2>
          <p class="version">
            {{ sdk.version }}
          </p>
        </div>
        <p class="summary">
          {{ sdk.summary }}
        </p>
        <UButton
          class="doc"
          :to="docTo"
          color="neutral"
          size="lg"
          trailing-icon="i-lucide-arrow-right"
        >
          使用文档
        </UButton>
      </div>
    </div>
  </article>
</template>

<style scoped>
.stage {
  height: 100%;
  perspective: 800px;
}

.tilt {
  position: relative;
  height: 100%;
  border-radius: 16px;
  isolation: isolate;
}

.halo,
.ring {
  position: absolute;
  border-radius: 16px;
  pointer-events: none;
}

.halo {
  z-index: 0;
  inset: -36px;
  border-radius: 52px;
  opacity: 0;
  filter: blur(16px);
  transition: opacity 0.2s ease;
}

.halo-clip {
  position: absolute;
  inset: 36px;
  overflow: hidden;
  border-radius: 16px;
}

.ring {
  z-index: 1;
  inset: 0;
  overflow: hidden;
  background: color-mix(in oklch, var(--ui-primary) 46%, transparent);
}

.spin {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 240%;
  aspect-ratio: 1;
  opacity: 0;
  transform: translate(-50%, -50%);
}

.stage:hover .spin {
  opacity: 1;
  animation: sdk-spin 4s linear infinite;
}

.disc {
  width: 100%;
  height: 100%;
  background: conic-gradient(
    from 0deg,
    transparent 0deg,
    transparent 250deg,
    color-mix(in oklch, var(--ui-primary) 75%, white) 300deg,
    var(--ui-primary) 324deg,
    transparent 360deg
  );
}

.halo .disc {
  filter: none;
  opacity: 1;
}

.stage:hover .halo {
  opacity: 1;
}

.face {
  position: absolute;
  z-index: 2;
  inset: 1px;
  display: flex;
  flex-direction: column;
  padding: 24px;
  border-radius: 15px;
  background: var(--ui-bg);
}

.head {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.name,
.version,
.summary {
  margin: 0;
}

.name {
  color: var(--ui-text-highlighted);
  font-size: 32px;
  font-weight: 600;
  line-height: 40px;
}

.version,
.summary {
  color: var(--ui-text);
  font-size: 16px;
  line-height: 24px;
}

.version {
  color: var(--ui-text-toned);
}

.summary {
  margin-top: 16px;
}

.doc {
  align-self: flex-start;
  min-height: 48px;
  margin-top: auto;
}

@media (prefers-reduced-motion: reduce) {
  .stage:hover .spin {
    animation: none;
  }

  .halo {
    display: none;
  }
}

@keyframes sdk-spin {
  from {
    transform: translate(-50%, -50%) rotate(0deg);
  }

  to {
    transform: translate(-50%, -50%) rotate(1turn);
  }
}
</style>
