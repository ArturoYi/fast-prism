<script lang="ts">
type HomeBox = {
  left: number
  top: number
  right: number
  bottom: number
  cx: number
  cy: number
}

type Magnet = {
  enabled: () => boolean
  measure: () => HomeBox | null
  pull: (x: number, y: number) => void
  release: () => void
  settle: () => void
}

const RANGE = 100
const SWITCH_MARGIN = 12
const magnets = new Set<Magnet>()
let activeMagnet: Magnet | null = null
let lastPointer: { x: number, y: number } | null = null
let listening = false

function edgeDistance(px: number, py: number, home: HomeBox) {
  const dx = Math.max(home.left - px, 0, px - home.right)
  const dy = Math.max(home.top - py, 0, py - home.bottom)
  return Math.hypot(dx, dy)
}

function chooseMagnet(px: number, py: number) {
  let nearest: { magnet: Magnet, edge: number } | null = null
  let currentEdge = Number.POSITIVE_INFINITY
  for (const magnet of magnets) {
    if (!magnet.enabled()) continue
    const home = magnet.measure()
    if (!home) continue
    const edge = edgeDistance(px, py, home)
    if (edge > RANGE) continue
    if (!nearest || edge < nearest.edge) nearest = { magnet, edge }
    if (magnet === activeMagnet) currentEdge = edge
  }
  if (!nearest) return null
  if (
    activeMagnet
    && activeMagnet !== nearest.magnet
    && currentEdge <= RANGE
    && nearest.edge + SWITCH_MARGIN >= currentEdge
  ) {
    return activeMagnet
  }
  return nearest.magnet
}

function applyPointer(px: number, py: number) {
  lastPointer = { x: px, y: py }
  const winner = chooseMagnet(px, py)
  activeMagnet = winner
  for (const magnet of magnets) {
    if (magnet === winner) magnet.pull(px, py)
    else magnet.release()
  }
}

function onPointerMove(event: PointerEvent) {
  if (event.pointerType !== 'mouse') return
  applyPointer(event.clientX, event.clientY)
}

function onScroll() {
  if (!lastPointer) return
  applyPointer(lastPointer.x, lastPointer.y)
}

function onDocumentLeave() {
  lastPointer = null
  activeMagnet = null
  for (const magnet of magnets) magnet.release()
}

function listen() {
  if (listening) return
  listening = true
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('scroll', onScroll, { passive: true })
  document.documentElement.addEventListener('mouseleave', onDocumentLeave)
}

function unlisten() {
  if (!listening) return
  listening = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('scroll', onScroll)
  document.documentElement.removeEventListener('mouseleave', onDocumentLeave)
  lastPointer = null
  activeMagnet = null
}

function registerMagnet(magnet: Magnet) {
  magnets.add(magnet)
  listen()
  return () => {
    magnets.delete(magnet)
    if (activeMagnet === magnet) activeMagnet = null
    if (magnets.size === 0) unlisten()
  }
}
</script>

<script setup lang="ts">
defineOptions({ name: 'MagneticButton' })

const props = withDefaults(defineProps<{
  to: string
  trailingIcon?: string
  variant?: 'solid' | 'outline'
}>(), {
  variant: 'solid',
})

const PULL = 0.3
const FOLLOW_RATE = 16
const STIFFNESS = 260
const DAMPING = 15

const homeRef = ref<HTMLElement | null>(null)
const x = ref(0)
const y = ref(0)
const pulled = ref(false)

const transformStyle = computed(() => {
  if (x.value === 0 && y.value === 0) return undefined
  return { transform: `translate3d(${x.value}px, ${y.value}px, 0)` }
})

let mode: 'idle' | 'follow' | 'spring' = 'idle'
let targetX = 0
let targetY = 0
let vx = 0
let vy = 0
let raf = 0
let lastTime = 0
let fineMedia: MediaQueryList | null = null
let reduceMedia: MediaQueryList | null = null

function magneticEnabled() {
  return !!fineMedia?.matches && !reduceMedia?.matches
}

function measureHome() {
  const home = homeRef.value
  if (!home) return null
  const rect = home.getBoundingClientRect()
  return {
    left: rect.left,
    top: rect.top,
    right: rect.right,
    bottom: rect.bottom,
    cx: rect.left + rect.width / 2,
    cy: rect.top + rect.height / 2,
  }
}

function stop() {
  if (!raf) return
  cancelAnimationFrame(raf)
  raf = 0
}

function start() {
  if (raf) return
  lastTime = performance.now()
  raf = requestAnimationFrame(tick)
}

function settle() {
  x.value = 0
  y.value = 0
  vx = 0
  vy = 0
  targetX = 0
  targetY = 0
  mode = 'idle'
  pulled.value = false
  stop()
}

function release() {
  if (mode === 'idle' && x.value === 0 && y.value === 0) return
  if (Math.hypot(x.value, y.value) < 0.2 && Math.hypot(vx, vy) < 0.2) {
    settle()
    return
  }
  mode = 'spring'
  pulled.value = true
  start()
}

function pullToward(px: number, py: number) {
  const home = measureHome()
  if (!home) return
  targetX = (px - home.cx) * PULL
  targetY = (py - home.cy) * PULL
  mode = 'follow'
  pulled.value = true
  start()
}

function tick(now: number) {
  raf = 0
  const dt = Math.min(0.032, (now - lastTime) / 1000 || 0.016)
  lastTime = now

  if (mode === 'follow') {
    const previousX = x.value
    const previousY = y.value
    const blend = 1 - Math.exp(-FOLLOW_RATE * dt)
    const nextX = previousX + (targetX - previousX) * blend
    const nextY = previousY + (targetY - previousY) * blend
    vx = (nextX - previousX) / dt
    vy = (nextY - previousY) / dt
    x.value = nextX
    y.value = nextY
    if (Math.hypot(targetX - nextX, targetY - nextY) < 0.08) {
      x.value = targetX
      y.value = targetY
      vx = 0
      vy = 0
      return
    }
  } else if (mode === 'spring') {
    vx += (-STIFFNESS * x.value - DAMPING * vx) * dt
    vy += (-STIFFNESS * y.value - DAMPING * vy) * dt
    x.value += vx * dt
    y.value += vy * dt
    if (Math.hypot(x.value, y.value) < 0.15 && Math.hypot(vx, vy) < 0.15) {
      settle()
      return
    }
  } else {
    return
  }

  raf = requestAnimationFrame(tick)
}

const magnet: Magnet = {
  enabled: () => magneticEnabled(),
  measure: () => measureHome(),
  pull: (px, py) => pullToward(px, py),
  release: () => release(),
  settle: () => settle(),
}

let unregister = () => {}

function syncInputMode() {
  if (magneticEnabled()) return
  settle()
}

onMounted(() => {
  fineMedia = window.matchMedia('(hover: hover) and (pointer: fine)')
  reduceMedia = window.matchMedia('(prefers-reduced-motion: reduce)')
  fineMedia.addEventListener('change', syncInputMode)
  reduceMedia.addEventListener('change', syncInputMode)
  unregister = registerMagnet(magnet)
})

onUnmounted(() => {
  fineMedia?.removeEventListener('change', syncInputMode)
  reduceMedia?.removeEventListener('change', syncInputMode)
  unregister()
  stop()
})
</script>

<template>
  <span
    ref="homeRef"
    class="magnetic"
    :class="{ 'is-pulled': pulled }"
  >
    <span
      class="magnetic__shift"
      :style="transformStyle"
    >
      <UButton
        :to="to"
        color="neutral"
        size="xl"
        :variant="props.variant"
        :trailing-icon="trailingIcon"
      >
        <slot />
      </UButton>
    </span>
  </span>
</template>

<style scoped>
.magnetic {
  position: relative;
  z-index: 0;
  display: inline-flex;
  vertical-align: middle;
}

.magnetic.is-pulled {
  z-index: 2;
}

.magnetic__shift {
  display: inline-flex;
}

@media (hover: hover) and (pointer: fine) {
  .magnetic.is-pulled .magnetic__shift {
    will-change: transform;
  }
}
</style>
