/**
 * Share service for web app - handles social media sharing
 * Matches Android ShareService functionality
 */

class ShareService {
  private static instance: ShareService

  private constructor() {}

  static getInstance(): ShareService {
    if (!ShareService.instance) {
      ShareService.instance = new ShareService()
    }
    return ShareService.instance
  }

  /**
   * Generate hashtags for social media sharing
   */
  private getHashtags(): string {
    return '#coachie #health #healthy #fitness #wellness #nutrition #workout #fit #healthylifestyle #wellnessjourney #healthtracking #aihealth #coachieai'
  }

  /**
   * Generate share text with hashtags
   */
  private getShareTextWithHashtags(baseText?: string): string {
    const base = baseText || 'Tracked with Coachie → coachieai.playspace.games'
    return `${base}\n\n${this.getHashtags()}`
  }

  /**
   * Share image to Instagram (opens in new tab with instructions)
   * Note: Web browsers can't directly open Instagram app, so we provide download + instructions
   */
  async shareToInstagram(imageUrl: string, isStory: boolean = false): Promise<void> {
    try {
      // Download the image
      const link = document.createElement('a')
      link.href = imageUrl
      link.download = `coachie-share-${Date.now()}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)

      // Copy hashtags to clipboard
      const hashtags = this.getHashtags()
      await navigator.clipboard.writeText(hashtags)

      // Show instructions
      const message = isStory
        ? 'Image downloaded! Hashtags copied to clipboard. Open Instagram Story and upload the image, then paste the hashtags.'
        : 'Image downloaded! Hashtags copied to clipboard. Open Instagram and upload the image, then paste the hashtags in your caption.'
      
      alert(message)
    } catch (error) {
      console.error('Error sharing to Instagram:', error)
      alert('Failed to prepare Instagram share. Please try downloading the image manually.')
    }
  }

  /**
   * Share image to Facebook
   */
  async shareToFacebook(imageUrl: string): Promise<void> {
    try {
      // Facebook Web Share API
      if (navigator.share) {
        const blob = await fetch(imageUrl).then(r => r.blob())
        const file = new File([blob], 'coachie-share.png', { type: 'image/png' })
        
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({
            files: [file],
            title: 'Check out my Coachie progress!',
            text: this.getShareTextWithHashtags(),
          })
          return
        }
      }

      // Fallback: Download image and copy text
      const link = document.createElement('a')
      link.href = imageUrl
      link.download = `coachie-share-${Date.now()}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)

      const shareText = this.getShareTextWithHashtags()
      await navigator.clipboard.writeText(shareText)
      alert('Image downloaded! Text copied to clipboard. Open Facebook and upload the image, then paste the text.')
    } catch (error) {
      console.error('Error sharing to Facebook:', error)
      alert('Failed to share to Facebook. Please try downloading the image manually.')
    }
  }

  /**
   * Share image to TikTok
   */
  async shareToTikTok(imageUrl: string): Promise<void> {
    try {
      // Download image and copy hashtags
      const link = document.createElement('a')
      link.href = imageUrl
      link.download = `coachie-share-${Date.now()}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)

      const hashtags = this.getHashtags()
      await navigator.clipboard.writeText(hashtags)
      alert('Image downloaded! Hashtags copied to clipboard. Open TikTok and upload the image, then paste the hashtags in your caption.')
    } catch (error) {
      console.error('Error sharing to TikTok:', error)
      alert('Failed to prepare TikTok share. Please try downloading the image manually.')
    }
  }

  /**
   * Share image to X (Twitter)
   */
  async shareToX(imageUrl: string): Promise<void> {
    try {
      // Twitter Web Share API
      const shareText = this.getShareTextWithHashtags()
      const twitterUrl = `https://twitter.com/intent/tweet?text=${encodeURIComponent(shareText)}`
      
      // Open Twitter in new window
      window.open(twitterUrl, '_blank', 'width=550,height=420')
      
      // Also download image for user to attach
      setTimeout(() => {
        const link = document.createElement('a')
        link.href = imageUrl
        link.download = `coachie-share-${Date.now()}.png`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
      }, 500)
    } catch (error) {
      console.error('Error sharing to X:', error)
      alert('Failed to share to X. Please try again.')
    }
  }

  /**
   * Generic image sharing using Web Share API
   */
  async shareImage(imageUrl: string, title: string = 'Share', message: string = 'Check this out!'): Promise<void> {
    try {
      if (navigator.share) {
        const blob = await fetch(imageUrl).then(r => r.blob())
        const file = new File([blob], 'coachie-share.png', { type: 'image/png' })
        
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({
            files: [file],
            title,
            text: this.getShareTextWithHashtags(message),
          })
          return
        }
      }

      // Fallback: Download image
      const link = document.createElement('a')
      link.href = imageUrl
      link.download = `coachie-share-${Date.now()}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)

      const shareText = this.getShareTextWithHashtags(message)
      await navigator.clipboard.writeText(shareText)
      alert('Image downloaded! Share text copied to clipboard.')
    } catch (error) {
      console.error('Error sharing image:', error)
      // Silent fail - user can manually download
    }
  }

  /**
   * Generate and share image with platform detection
   */
  async generateAndShare(
    imageDataUrl: string,
    platform: string | null = null, // 'instagram-story', 'instagram-feed', 'facebook', 'tiktok', 'x', 'native'
  ): Promise<void> {
    try {
      switch (platform) {
        case 'instagram-story':
          await this.shareToInstagram(imageDataUrl, true)
          break
        case 'instagram-feed':
          await this.shareToInstagram(imageDataUrl, false)
          break
        case 'facebook':
          await this.shareToFacebook(imageDataUrl)
          break
        case 'tiktok':
          await this.shareToTikTok(imageDataUrl)
          break
        case 'x':
        case 'twitter':
          await this.shareToX(imageDataUrl)
          break
        default:
          await this.shareImage(imageDataUrl, 'Share Your Accomplishment', 'Share your progress with friends!')
      }
    } catch (error) {
      console.error('Error in generateAndShare:', error)
      alert('Failed to share. Please try again.')
    }
  }
}

export default ShareService
