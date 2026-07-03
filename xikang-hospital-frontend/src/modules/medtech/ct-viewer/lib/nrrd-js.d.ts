declare module 'nrrd-js' {
  const nrrd: {
    parse(buffer: ArrayBuffer): Record<string, unknown>
  }
  export default nrrd
}
