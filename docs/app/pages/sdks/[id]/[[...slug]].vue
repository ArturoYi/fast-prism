<script setup lang="ts">
definePageMeta({
  layout: 'default',
})

const { isOpen } = useAssistant()
const { page, navigation, redirectTo, settled, ready } = useSdkDoc()

useSeoMeta({
  title: () => page.value?.title,
  description: () => page.value?.description,
})

if (import.meta.client) {
  watch([settled, redirectTo, page], () => {
    if (!settled.value) return
    if (redirectTo.value) {
      navigateTo(redirectTo.value, { replace: true })
      return
    }
    if (!page.value) {
      showError(createError({ statusCode: 404, statusMessage: '没有这份 SDK 文档', fatal: true }))
    }
  }, { immediate: true })
}

if (import.meta.server) {
  await ready
  if (redirectTo.value) {
    await navigateTo(redirectTo.value, { replace: true })
  }
  else if (!page.value) {
    throw createError({ statusCode: 404, statusMessage: '没有这份 SDK 文档', fatal: true })
  }
}
</script>

<template>
  <div
    v-if="!page"
    class="sdk-loading"
  >
    <DocsLoader label="正在加载" />
  </div>
  <UContainer v-else>
    <UPage>
      <template
        v-if="!isOpen"
        #left
      >
        <UPageAside>
          <SdkOutlineNav :navigation="navigation" />
        </UPageAside>
      </template>

      <UPageHeader
        :title="page.title"
        :description="page.description"
        :ui="{
          wrapper: 'flex-row items-center flex-wrap justify-between',
        }"
      />

      <UPageBody>
        <ContentRenderer :value="page" />
      </UPageBody>

      <template
        v-if="!isOpen"
        #right
      >
        <DocsAsideRight :page="page" />
      </template>
    </UPage>
  </UContainer>
</template>

<style scoped>
.sdk-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - var(--ui-header-height, 64px));
}
</style>
