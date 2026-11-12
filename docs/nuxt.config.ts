const baseURL = process.env.NUXT_APP_BASE_URL || '/'
const siteUrl = process.env.NUXT_SITE_URL || 'http://localhost:3000'

export default defineNuxtConfig({
  site: {
    name: 'FastPrism',
    url: siteUrl,
  },
  app: {
    baseURL,
  },
  // @nuxtjs/robots 不能在带基路径的站点上生成 robots.txt。
  robots: {
    robotsTxt: baseURL === '/',
  },
})
