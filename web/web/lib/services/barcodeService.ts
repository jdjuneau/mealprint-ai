/**
 * Barcode scanning service for web
 * Uses ZXing library to scan barcodes from images
 */

class BarcodeService {
  private reader: any = null

  constructor() {
    // Only initialize on client side
    if (typeof window !== 'undefined') {
      try {
        const { BrowserMultiFormatReader } = require('@zxing/library')
        this.reader = new BrowserMultiFormatReader()
      } catch (error) {
        console.warn('Failed to load ZXing library:', error)
      }
    }
  }

  /**
   * Scan barcode from image file
   */
  async scanBarcodeFromFile(file: File): Promise<string | null> {
    try {
      const imageBitmap = await createImageBitmap(file)
      const canvas = document.createElement('canvas')
      canvas.width = imageBitmap.width
      canvas.height = imageBitmap.height
      const ctx = canvas.getContext('2d')
      if (!ctx) return null

      ctx.drawImage(imageBitmap, 0, 0)
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)

      // Try to decode barcode
      const result = await this.reader.decodeFromImageData(imageData)
      return result.getText()
    } catch (error: any) {
      // Check if it's a "not found" error (barcode not detected)
      if (error?.name === 'NotFoundException' || error?.message?.includes('not found') || error?.message?.includes('No barcode')) {
        return null // No barcode found
      }
      console.error('Barcode scanning error:', error)
      return null
    }
  }

  /**
   * Scan barcode from image URL
   */
  async scanBarcodeFromUrl(imageUrl: string): Promise<string | null> {
    if (typeof window === 'undefined' || !this.reader) {
      return null
    }

    try {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      
      return new Promise((resolve) => {
        img.onload = async () => {
          try {
            const canvas = document.createElement('canvas')
            canvas.width = img.width
            canvas.height = img.height
            const ctx = canvas.getContext('2d')
            if (!ctx) {
              resolve(null)
              return
            }

            ctx.drawImage(img, 0, 0)
            const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)

            const result = await this.reader.decodeFromImageData(imageData)
            resolve(result.getText())
          } catch (error: any) {
            // Check if it's a "not found" error (barcode not detected)
            if (error?.name === 'NotFoundException' || error?.message?.includes('not found') || error?.message?.includes('No barcode')) {
              resolve(null)
            } else {
              console.error('Barcode scanning error:', error)
              resolve(null)
            }
          }
        }
        img.onerror = () => resolve(null)
        img.src = imageUrl
      })
    } catch (error) {
      console.error('Barcode scanning error:', error)
      return null
    }
  }
}

export default new BarcodeService()
