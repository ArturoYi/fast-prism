import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { parse } from 'yaml'

function iconFromMarkdown(raw: string) {
  const match = raw.match(/^---\r?\n([\s\S]*?)\r?\n---/)
  if (!match) return
  const data = parse(match[1]) as { icon?: string, navigation?: { icon?: string } | false }
  if (typeof data.icon === 'string') return data.icon
  if (data.navigation && typeof data.navigation === 'object' && typeof data.navigation.icon === 'string') {
    return data.navigation.icon
  }
}

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id') || ''
  if (!/^[a-z0-9-]+$/.test(id)) {
    throw createError({ statusCode: 400, statusMessage: '无效的 SDK' })
  }

  const dir = join(process.cwd(), 'content/sdk', id)
  try {
    const [sectionRaw, files] = await Promise.all([
      readFile(join(dir, '.navigation.yml'), 'utf8'),
      readdir(dir),
    ])
    const section = parse(sectionRaw) as { title?: string, icon?: string }
    const icons: Record<string, string> = {}
    await Promise.all(files.filter(file => file.endsWith('.md')).map(async (file) => {
      const icon = iconFromMarkdown(await readFile(join(dir, file), 'utf8'))
      if (icon) icons[file.slice(0, -3)] = icon
    }))
    return {
      title: section.title || id,
      icon: typeof section.icon === 'string' ? section.icon : undefined,
      icons,
    }
  }
  catch {
    throw createError({ statusCode: 404, statusMessage: '没有这份 SDK 文档' })
  }
})
