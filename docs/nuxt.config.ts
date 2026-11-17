import { readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const baseURL = process.env.NUXT_APP_BASE_URL || '/'
const siteUrl = process.env.NUXT_SITE_URL || 'http://localhost:3000'
const contentSdkDir = fileURLToPath(new URL('./content/sdk', import.meta.url))

// 文档页在 pages/sdks 下，列表又是客户端渲染，爬虫看不到链接。
// 不预渲染时，GitHub Pages 会用 404.html 接住这些地址。
function sdkDocRoutes() {
  let ids: string[] = []
  try {
    ids = readdirSync(contentSdkDir, { withFileTypes: true })
      .filter(entry => entry.isDirectory())
      .map(entry => entry.name)
  }
  catch {
    return []
  }
  return ids.flatMap((id) => {
    const slugs = readdirSync(join(contentSdkDir, id))
      .filter(file => file.endsWith('.md'))
      .map(file => file.replace(/^\d+\./, '').replace(/\.md$/, ''))
    return [`/sdks/${id}`, ...slugs.map(slug => `/sdks/${id}/${slug}`)]
  })
}

export default defineNuxtConfig({
  site: {
    name: 'FastPrism',
    url: siteUrl,
  },
  app: {
    baseURL,
  },
  // GitHub Pages 没有图片服务。页头 logo 会先按 baseURL 改写路径，
  // 再交给 IPX 就会请求 /_ipx/.../<base>/logo.svg，静态站点上是 404。
  image: {
    provider: 'none',
  },
  // @nuxtjs/robots 不能在带基路径的站点上生成 robots.txt。
  robots: {
    robotsTxt: baseURL === '/',
  },
  hooks: {
    'nitro:config'(nitroConfig) {
      nitroConfig.prerender ||= {}
      nitroConfig.prerender.routes ||= []
      nitroConfig.prerender.routes.push(...sdkDocRoutes())
    },
  },
})
