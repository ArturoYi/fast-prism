export interface SdkEntry {
  id: string
  name: string
  version: string
  summary: string
  docPath: string
}

export interface SdkPage {
  items: SdkEntry[]
  total: number
  offset: number
  limit: number
}

// 列表只放仓库根目录上的 SDK。子模块写在各自的使用文档里。
export const sdkCatalog: readonly SdkEntry[] = [
  {
    id: 'fast-download',
    name: 'FastDownload',
    version: '1.0.0-rc',
    summary: '把文件下载到应用内部目录。对外入口是 FastDownloader，回调在主线程。',
    docPath: '/sdks/fast-download/overview',
  },
]
