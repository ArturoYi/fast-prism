export interface SdkLink {
  label: string
  href: string
  title?: string
}

export interface SdkEntry {
  id: string
  name: string
  version: string
  summary: string
  docPath: string
  links?: readonly SdkLink[]
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
    links: [
      {
        label: '下载',
        title: '在 Maven Central 打开 fast-download-bom',
        href: 'https://central.sonatype.com/artifact/io.github.arturoyi/fast-download-bom',
      },
      {
        label: '源码',
        href: 'https://github.com/ArturoYi/fast-prism/tree/main/fast-download',
      },
      {
        label: '许可',
        href: 'https://www.apache.org/licenses/LICENSE-2.0.txt',
      },
    ],
  },
  {
    id: 'album-cleaner',
    name: 'AlbumCleaner',
    version: '0.1.0',
    summary: '开发中',
    docPath: '/sdks/album-cleaner/overview',
    links: [
      {
        label: '源码',
        href: 'https://github.com/ArturoYi/fast-prism/tree/main/album-cleaner',
      },
      {
        label: '许可',
        href: 'https://www.apache.org/licenses/LICENSE-2.0.txt',
      },
    ],
  },
]
