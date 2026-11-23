<script setup lang="ts">
const route = useRoute()
const { localePath } = useDocusI18n()
const { forced: forcedColorMode } = useDocusColorMode()

const githubUrl = 'https://github.com/ArturoYi/fast-prism'
const isHome = computed(() => route.path === localePath('/'))
const { active: sdkDoc, navigation: sdkNavigation } = useSdkDoc()
</script>

<template>
  <ClientOnly>
    <DocsRouteLoader />
  </ClientOnly>

  <UHeader
    :toggle="!isHome"
    :ui="{ center: 'flex-1' }"
  >
    <AppHeaderCenter />

    <template #left>
      <AppHeaderLeft />
    </template>

    <template #right>
      <UContentSearchButton class="lg:hidden" />

      <ClientOnly v-if="!forcedColorMode">
        <UColorModeButton />

        <template #fallback>
          <div class="h-8 w-8 animate-pulse rounded-md bg-neutral-200 dark:bg-neutral-800" />
        </template>
      </ClientOnly>

      <UButton
        icon="i-simple-icons-github"
        color="neutral"
        variant="ghost"
        :to="githubUrl"
        target="_blank"
        aria-label="GitHub"
      />
    </template>

    <template #toggle="{ open, toggle }">
      <IconMenuToggle
        v-if="!isHome"
        :open="open"
        class="lg:hidden"
        @click="toggle"
      />
    </template>

    <template
      v-if="!isHome"
      #body
    >
      <SdkOutlineNav
        v-if="sdkDoc"
        :navigation="sdkNavigation"
      />
      <AppHeaderBody v-else />
    </template>
  </UHeader>
</template>
