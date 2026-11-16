<script setup lang="ts">
defineOptions({ name: 'LogoMark' })

useHead({
  title: 'FastPrism',
  titleTemplate: '%s',
})
</script>

<template>
  <UColorModeImage
    light="/logo.svg"
    dark="/logo-on-dark.svg"
    alt="FastPrism"
    width="93"
    height="56"
    class="h-14 w-auto"
  />
</template>
