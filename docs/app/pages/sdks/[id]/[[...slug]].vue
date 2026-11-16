<script setup lang="ts">
definePageMeta({
  layout: 'default',
})

const { isOpen } = useAssistant()
const { page, navigation, redirectTo } = await useSdkDoc()

if (redirectTo.value) {
  await navigateTo(redirectTo.value, { replace: true })
}
else if (!page.value) {
  throw createError({ statusCode: 404, statusMessage: '没有这份 SDK 文档', fatal: true })
}

useSeoMeta({
  title: page.value?.title,
  description: page.value?.description,
})
</script>

<template>
  <UContainer v-if="page">
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
