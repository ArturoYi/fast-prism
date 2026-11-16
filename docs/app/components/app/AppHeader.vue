<script setup lang="ts">
const route = useRoute()
const { localePath } = useDocusI18n()
const { forced: forcedColorMode } = useDocusColorMode()

const githubUrl = 'https://github.com/ArturoYi/fast-prism'
const isHome = computed(() => route.path === localePath('/'))
const sdkPath = computed(() => localePath('/sdks'))
const isSdk = computed(() => route.path === sdkPath.value || route.path.startsWith(`${sdkPath.value}/`))
const { active: sdkDoc, navigation: sdkNavigation } = await useSdkDoc()
</script>

<template>
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

      <UButton
        :to="sdkPath"
        color="neutral"
        :variant="isSdk ? 'soft' : 'ghost'"
        :aria-current="isSdk ? 'page' : undefined"
      >
        SDK
      </UButton>

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
