export default defineEventHandler((event) => {
  const url = getRequestURL(event)
  if (!url.pathname.startsWith('/sdk/')) return
  const id = url.pathname.slice('/sdk/'.length)
  return sendRedirect(event, `/sdks/${id}${url.search}`, 301)
})
