let navigating = false

function currentPath() {
  const pages = getCurrentPages()
  return pages.length ? `/${pages[pages.length - 1].route || ''}` : ''
}

export function replacePage(url: string, clearStack = false) {
  const target = url.split('?')[0]
  if (currentPath() === target || navigating) return
  navigating = true
  const done = () => setTimeout(() => { navigating = false }, 250)
  const options = { url, success:done, fail:done }
  if (clearStack) uni.reLaunch(options)
  else uni.redirectTo(options)
}
