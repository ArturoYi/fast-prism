<script setup lang="ts">
import { useInfiniteScroll, useResizeObserver } from '@vueuse/core'
import { useVirtualizer, type VirtualItem } from '@tanstack/vue-virtual'
import { sdkCatalog, type SdkEntry } from '#shared/sdks'

defineOptions({ name: 'SdkCatalog' })

const COLUMN_GAP = 16
const ROW_GAP = 24
const MIN_CARD_WIDTH = 240
const CARD_HEIGHT = 288

const scroller = ref<HTMLElement | null>(null)
const contentWidth = ref(0)
const viewportHeight = ref(0)
const modules = ref<SdkEntry[]>([])
const total = ref(0)
const settled = ref(false)
const failed = ref(false)

const ready = computed(() => contentWidth.value > 0 && viewportHeight.value > 0)
const columns = computed(() => columnsFor(contentWidth.value))

useResizeObserver(scroller, (entries) => {
  const entry = entries[0]
  if (!entry) return
  contentWidth.value = entry.contentRect.width
  viewportHeight.value = entry.contentRect.height
})

const virtualizer = useVirtualizer(computed(() => ({
  count: modules.value.length,
  getScrollElement: () => scroller.value,
  estimateSize: () => CARD_HEIGHT,
  lanes: columns.value,
  gap: ROW_GAP,
  overscan: 2,
  paddingStart: 32,
  paddingEnd: 48,
  getItemKey: (index: number) => modules.value[index]?.id ?? index,
})))

const totalSize = computed(() => virtualizer.value.getTotalSize())
const cells = computed(() => {
  const list = modules.value
  return virtualizer.value.getVirtualItems().flatMap((item) => {
    const sdk = list[item.index]
    if (!sdk) return []
    return [{ key: item.key, item, sdk }]
  })
})

const { isLoading, reset } = useInfiniteScroll(
  scroller,
  () => loadMore(),
  {
    distance: 160,
    canLoadMore: () => {
      if (!ready.value || failed.value) return false
      if (!settled.value) return true
      return modules.value.length < total.value
    },
  },
)

function columnsFor(width: number) {
  if (width <= 0) return 1
  return Math.max(1, Math.floor((width + COLUMN_GAP) / (MIN_CARD_WIDTH + COLUMN_GAP)))
}

function pageLimit() {
  const cols = Math.max(1, columns.value)
  const stride = CARD_HEIGHT + ROW_GAP
  const rows = Math.max(1, Math.ceil(viewportHeight.value / stride))
  return cols * rows
}

function loadMore() {
  if (failed.value) return
  if (settled.value && modules.value.length >= total.value) return
  const offset = modules.value.length
  const limit = pageLimit()
  const page = sdkCatalog.slice(offset, offset + limit)
  if (modules.value.length !== offset) return
  const seen = new Set(modules.value.map(item => item.id))
  const next = page.filter(item => !seen.has(item.id))
  modules.value = modules.value.concat(next)
  total.value = sdkCatalog.length
  settled.value = true
}

function retry() {
  failed.value = false
  reset()
}

function cellStyle(item: VirtualItem) {
  const cols = columns.value
  return {
    top: `${item.start}px`,
    left: `calc(${item.lane} * (100% + ${COLUMN_GAP}px) / ${cols})`,
    width: `calc((100% - ${(cols - 1) * COLUMN_GAP}px) / ${cols})`,
    height: `${item.size}px`,
  }
}
</script>

<template>
  <div class="catalog">
    <header class="bar">
      <div>
        <h1 class="title">
          SDK
        </h1>
        <p class="sub">
          仓库里的 SDK
        </p>
      </div>
      <p
        v-if="settled"
        class="count"
        aria-live="polite"
      >
        {{ modules.length }} / {{ total }}
      </p>
    </header>

    <div
      ref="scroller"
      class="scroller"
      :aria-busy="isLoading"
    >
      <DocsLoader
        v-if="!modules.length && !failed"
        label="正在加载"
      />
      <div
        v-else-if="failed && !modules.length"
        class="note"
      >
        <p>列表没有加载出来</p>
        <UButton
          class="retry"
          color="neutral"
          variant="outline"
          size="lg"
          @click="retry"
        >
          重试
        </UButton>
      </div>
      <p
        v-else-if="!modules.length"
        class="note"
      >
        还没有可列出的 SDK。
      </p>
      <div
        v-else
        class="canvas"
        role="list"
        :style="{ height: `${totalSize}px` }"
      >
        <div
          v-for="cell in cells"
          :key="String(cell.key)"
          class="cell"
          role="listitem"
          :style="cellStyle(cell.item)"
        >
          <SdkCard :sdk="cell.sdk" />
        </div>
      </div>
    </div>

    <div
      v-if="modules.length && (failed || isLoading)"
      class="status"
      role="status"
    >
      <template v-if="failed">
        <span>列表没有加载出来</span>
        <UButton
          class="retry"
          color="neutral"
          variant="ghost"
          size="lg"
          @click="retry"
        >
          重试
        </UButton>
      </template>
      <DocsLoader
        v-else
        compact
        label="正在加载"
      />
    </div>
  </div>
</template>

<style scoped>
.catalog {
  --sdk-inline: 48px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ui-bg);
}

@media (min-width: 768px) {
  .catalog {
    --sdk-inline: 80px;
  }
}

.bar {
  display: flex;
  flex: none;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 56px var(--sdk-inline) 40px;
}

.title,
.sub,
.count,
.note {
  margin: 0;
}

.title {
  color: var(--ui-text-highlighted);
  font-size: 32px;
  font-weight: 600;
  line-height: 40px;
}

.sub,
.count,
.note,
.status {
  color: var(--ui-text-toned);
  font-size: 16px;
  line-height: 24px;
}

.sub {
  margin-top: 8px;
}

.count {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.scroller {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-inline: var(--sdk-inline);
  overflow-anchor: none;
  scrollbar-gutter: stable;
}

.canvas {
  position: relative;
  width: 100%;
}

.cell {
  position: absolute;
  z-index: 0;
  overflow: visible;
}

.cell:hover {
  z-index: 1;
}

.note {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px 0;
  text-align: center;
}

.status {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 48px;
}

.retry {
  min-width: 48px;
  min-height: 48px;
}
</style>
