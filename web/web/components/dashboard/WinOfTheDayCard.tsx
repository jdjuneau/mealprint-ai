'use client'

import { useState } from 'react'
import SharePlatformDialog from '../SharePlatformDialog'
import ShareService from '../../lib/services/shareService'
import toast from 'react-hot-toast'

interface WinOfTheDayCardProps {
  win: string
  onShare?: () => void
  onClick?: () => void
}

export default function WinOfTheDayCard({
  win,
  onShare,
  onClick
}: WinOfTheDayCardProps) {
  const [showShareDialog, setShowShareDialog] = useState(false)

  const handleShareClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    setShowShareDialog(true)
  }

  return (
    <>
    <div
      className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-4 cursor-pointer hover:bg-white/20 transition-colors"
      onClick={onClick}
    >
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <h3 className="text-lg font-bold text-white mb-1">Win of the Day</h3>
          <p className="text-white/90 text-sm mb-2 line-clamp-2">{win}</p>
          <p className="text-xs text-white/70">Tap for more details</p>
        </div>
        
        <div className="flex items-center gap-2">
          <button
            onClick={handleShareClick}
            className="p-1.5 hover:bg-white/20 rounded transition-colors"
            title="Share win"
          >
            <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.885 12.938 9 12.482 9 12c0-.482-.115-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
            </svg>
          </button>
          <svg className="w-8 h-8 text-yellow-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
          </svg>
        </div>
      </div>

      {showShareDialog && (
        <SharePlatformDialog
          onDismiss={() => setShowShareDialog(false)}
          onShareToPlatform={async (platform) => {
            setShowShareDialog(false)
            try {
              const shareService = ShareService.getInstance()
              const shareText = `🏆 ${win}\n\nTracked with Coachie → coachieai.playspace.games`
              
              if (platform) {
                if (navigator.share) {
                  await navigator.share({
                    title: 'My Win of the Day!',
                    text: shareText,
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Win details copied to clipboard!')
                }
              } else {
                if (navigator.share) {
                  await navigator.share({
                    title: 'My Win of the Day!',
                    text: shareText,
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Win details copied to clipboard!')
                }
              }
            } catch (error) {
              console.error('Error sharing:', error)
              toast.error('Failed to share win')
            }
          }}
        />
      )}
    </div>
    </>
  )
}

