const assetBase = (process.env.NUXT_APP_BASE_URL || '/').replace(/\/$/, '')

export default defineAppConfig({
  ui: {
    colors: {
      primary: 'green',
    },
  },
  seo: {
    title: 'FastPrism',
    description: 'FastDownload：把文件下载到应用内部目录。',
  },
  header: {
    title: 'FastPrism',
    logo: {
      light: '/logo.svg',
      dark: '/logo-on-dark.svg',
      alt: 'FastPrism',
      display: 'logo',
      class: 'h-7 w-auto',
      favicon: `${assetBase}/logo.svg`,
    },
  },
  github: false,
  toc: {
    title: '本页目录',
  },
})
