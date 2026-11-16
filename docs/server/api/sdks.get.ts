import { sdkCatalog, type SdkPage } from '#shared/sdks'

function readInt(value: unknown, fallback: number) {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = typeof raw === 'number' ? raw : typeof raw === 'string' ? Number.parseInt(raw, 10) : Number.NaN
  if (!Number.isInteger(parsed) || parsed < 0) return fallback
  return parsed
}

export default defineEventHandler((event): SdkPage => {
  const query = getQuery(event)
  const offset = Math.min(readInt(query.offset, 0), sdkCatalog.length)
  const limit = Math.min(96, Math.max(1, readInt(query.limit, 12)))
  return {
    items: sdkCatalog.slice(offset, offset + limit),
    total: sdkCatalog.length,
    offset,
    limit,
  }
})
