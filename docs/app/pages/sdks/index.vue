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
        <p class="sdk-page__wait">
          正在加载
        </p>
      </template>
    </ClientOnly>
  </div>
</template>

<style scoped>
.sdk-page {
  height: calc(100vh - var(--ui-header-height));
  overflow: hidden;
}

.sdk-page__wait {
  margin: 0;
  padding: 32px 48px;
  color: var(--ui-text-toned);
  font-size: 16px;
  line-height: 24px;
}
</style>
