'use client'

interface MorningBriefInsightCardProps {
  greeting: string
  insight: string
  isSpeaking?: boolean
  onPlay?: () => void
  onStop?: () => void
  onClick?: () => void
  subscriptionTier?: 'FREE' | 'PRO'
  onUpgrade?: () => void
}

export default function MorningBriefInsightCard({
  greeting,
  insight,
  isSpeaking = false,
  onPlay,
  onStop,
  onClick,
  subscriptionTier,
  onUpgrade
}: MorningBriefInsightCardProps) {
  // Show brief for all users (free and pro)
  // If no brief is available, show a message encouraging interaction
  return (
    <div
      className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-3 cursor-pointer hover:bg-white/20 transition-colors"
      onClick={onClick}
    >
      <div className="flex items-center justify-between mb-1">
        <h3 className="text-base font-bold text-white">AI Coach {greeting} Brief</h3>
        <span className="text-lg">✨</span>
      </div>

      {insight ? (
        <p className="text-white/90 text-sm leading-relaxed">{insight}</p>
      ) : (
        <p className="text-white/70 text-xs">💬 Tap to chat with AI Coachie for personalized insights!</p>
      )}

      {isSpeaking && (
        <p className="text-white/70 text-xs mt-1">🔊 Speaking...</p>
      )}
    </div>
  )
}

