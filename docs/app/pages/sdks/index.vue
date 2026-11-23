<script setup lang="ts">
definePageMeta({
  layout: 'default',
  footer: false,
})

useSeoMeta({
  title: 'SDK',
  description: '仓库里的 SDK。每个 SDK 有自己的使用文档。',
})
</script>

<template>
  <div class="sdk-page">
    <ClientOnly>
      <SdkCatalog />
      <template #fallback>
        <div class="sdk-page__loading">
          <DocsLoader label="正在加载" />
        </div>
      </template>
    </ClientOnly>
  </div>
</template>

<style scoped>
.sdk-page {
  height: calc(100vh - var(--ui-header-height));
  overflow: hidden;
}

.sdk-page__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
