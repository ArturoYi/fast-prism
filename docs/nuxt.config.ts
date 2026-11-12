const siteUrl = process.env.NUXT_SITE_URL || 'http://localhost:3000'

export default defineNuxtConfig({
  site: {
    name: 'FastPrism',
    url: siteUrl,
  },
  app: {
    baseURL: process.env.NUXT_APP_BASE_URL || '/',
  },
})
