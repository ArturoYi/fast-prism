import type { ContentNavigationItem } from '@nuxt/content'
import { sdkSection } from '../utils/sdkSections'

interface SdkArticle {
  title: string
  path: string
  stem?: string
  navigation?: false | { icon?: string }
}

function articleIcon(navigation: SdkArticle['navigation']) {
  if (!navigation || typeof navigation !== 'object') return undefined
  return navigation.icon
}

export function useSdkDoc() {
  const route = useRoute()
  const { localePath } = useDocusI18n()
  const sdkRoot = computed(() => localePath('/sdks').replace(/\/$/, ''))
  const location = computed(() => {
    const root = sdkRoot.value
    const path = route.path.replace(/\/$/, '')
    if (path === root || !path.startsWith(`${root}/`)) return { id: '', slug: '' }
    const [id = '', ...rest] = path.slice(root.length + 1).split('/')
    return { id, slug: rest.join('/') }
  })
  const id = computed(() => location.value.id)
  const slug = computed(() => location.value.slug)

  const section = computed(() => id.value ? sdkSection(id.value) : null)

  // 两次 useAsyncData 必须在 await 之前同步调用。
  // 中间一旦 await，客户端导航会在组件挂载后才执行第二次请求，触发 NUXT_E3003。
  const articlesData = useAsyncData(
    () => id.value ? `sdk-articles-${id.value}` : 'sdk-articles-idle',
    () => {
      if (!id.value) return Promise.resolve([] as SdkArticle[])
      return queryCollection('docs')
        .where('path', 'LIKE', `/sdk/${id.value}/%`)
        .select('title', 'path', 'stem', 'navigation')
        .all() as Promise<SdkArticle[]>
    },
    { watch: [id] },
  )

  const contentPath = computed(() => slug.value ? `/sdk/${id.value}/${slug.value}` : '')

  const pageData = useAsyncData(
    () => contentPath.value ? `sdk-doc-${contentPath.value}` : 'sdk-doc-idle',
    () => {
      if (!contentPath.value) return Promise.resolve(null)
      return queryCollection('docs').path(contentPath.value).first()
    },
    { watch: [contentPath] },
  )

  const articles = articlesData.data
  const page = pageData.data
  const ready = Promise.all([articlesData, pageData])
  const settled = computed(() => {
    const states = [articlesData.status.value, pageData.status.value]
    return states.every(state => state === 'success' || state === 'error')
  })

  const ordered = computed(() => {
    const prefix = `/sdk/${id.value}/`
    return [...(articles.value || [])]
      .filter(article => article.path.startsWith(prefix) && !article.path.endsWith('/.navigation'))
      .sort((a, b) => (a.stem || a.path).localeCompare(b.stem || b.path, undefined, { numeric: true }))
  })

  function publicPath(contentPath: string) {
    const prefix = `/sdk/${id.value}`
    const rest = contentPath.startsWith(prefix) ? contentPath.slice(prefix.length) : ''
    return `${sdkRoot.value}/${id.value}${rest}`
  }

  const navigation = computed<ContentNavigationItem[]>(() => {
    const pages = ordered.value
    if (!id.value || !pages.length) return []
    return [{
      title: section.value?.title || id.value,
      icon: section.value?.icon,
      path: publicPath(pages[0].path),
      children: pages.map(article => {
        const file = article.stem?.split('/').pop()
        return {
          title: article.title,
          icon: (file && section.value?.icons?.[file]) || articleIcon(article.navigation),
          path: publicPath(article.path),
        }
      }),
    }]
  })

  const redirectTo = computed(() => {
    const first = ordered.value[0]
    if (!id.value || slug.value || !first) return ''
    return publicPath(first.path)
  })

  const active = computed(() => Boolean(id.value))

  return { id, slug, page, navigation, active, redirectTo, settled, ready }
}
