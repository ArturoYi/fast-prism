import type { ContentNavigationItem } from '@nuxt/content'

interface SdkSection {
  title: string
  icon?: string
  icons?: Record<string, string>
}

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

export async function useSdkDoc() {
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

  const { data: section } = await useAsyncData(
    () => id.value ? `sdk-section-${id.value}` : 'sdk-section-idle',
    () => {
      if (!id.value) return Promise.resolve(null)
      return $fetch<SdkSection>(`/api/sdk-section/${id.value}`).catch(() => null)
    },
    { watch: [id] },
  )

  const { data: articles } = await useAsyncData(
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

  const contentPath = computed(() => slug.value ? `/sdk/${id.value}/${slug.value}` : '')
  const redirectTo = computed(() => {
    const first = ordered.value[0]
    if (!id.value || slug.value || !first) return ''
    return publicPath(first.path)
  })

  const { data: page } = await useAsyncData(
    () => contentPath.value ? `sdk-doc-${contentPath.value}` : 'sdk-doc-idle',
    () => {
      if (!contentPath.value) return Promise.resolve(null)
      return queryCollection('docs').path(contentPath.value).first()
    },
    { watch: [contentPath] },
  )

  const active = computed(() => Boolean(id.value))

  return { id, slug, page, navigation, active, redirectTo }
}
