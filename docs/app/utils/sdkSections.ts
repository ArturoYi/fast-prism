export interface SdkSection {
  title: string
  icon?: string
  icons: Record<string, string>
}

const navigationFiles = import.meta.glob('../../content/sdk/*/.navigation.yml', {
  query: '?raw',
  import: 'default',
  eager: true,
  exhaustive: true,
}) as Record<string, string>

const markdownFiles = import.meta.glob('../../content/sdk/*/*.md', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

function field(raw: string, key: string) {
  const match = raw.match(new RegExp(`^${key}:\\s*['"]?([^'"\\n#]+?)['"]?\\s*$`, 'm'))
  return match?.[1]?.trim()
}

function iconFromMarkdown(raw: string) {
  const match = raw.match(/^---\r?\n([\s\S]*?)\r?\n---/)
  if (!match) return
  return field(match[1], 'icon')
}

function idFromPath(path: string) {
  return path.match(/content\/sdk\/([^/]+)\//)?.[1] ?? ''
}

const sections: Record<string, SdkSection> = {}

for (const [path, raw] of Object.entries(navigationFiles)) {
  const id = idFromPath(path)
  if (!id) continue
  sections[id] = {
    title: field(raw, 'title') || id,
    icon: field(raw, 'icon'),
    icons: {},
  }
}

for (const [path, raw] of Object.entries(markdownFiles)) {
  const id = idFromPath(path)
  if (!id) continue
  const section = sections[id] ?? (sections[id] = { title: id, icons: {} })
  const icon = iconFromMarkdown(raw)
  const stem = path.split('/').pop()?.replace(/\.md$/, '')
  if (icon && stem) section.icons[stem] = icon
}

export function sdkSection(id: string) {
  return sections[id] ?? null
}
