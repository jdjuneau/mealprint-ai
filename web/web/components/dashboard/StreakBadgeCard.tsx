'use client'

interface Streak {
  currentStreak: number
  longestStreak?: number
  totalLogs?: number
  totalDays?: number
  lastLogDate?: string
}

interface StreakBadgeCardProps {
  streak: Streak | null
  hasActualLogData?: boolean
  onClick?: () => void
}

export default function StreakBadgeCard({
  streak,
  hasActualLogData = false,
  onClick
}: StreakBadgeCardProps) {
  const today = new Date().toISOString().split('T')[0]
  const lastLogDate = streak?.lastLogDate
  const hasActivityToday = hasActualLogData || (lastLogDate === today)
  
  // Remove the hasBadData check - it was incorrectly filtering out valid streaks
  // The streak service properly calculates currentStreak, so we should trust it
  const displayStreak = streak?.currentStreak || 0

  return (
    <div
      className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 shadow-lg p-3 cursor-pointer hover:bg-white/20 transition-colors min-h-[120px] flex flex-col items-center justify-center"
      onClick={onClick}
    >
      <div className="text-2xl mb-1">🔥</div>
      
      <div className="text-3xl font-bold text-white mb-1">
        {displayStreak}
      </div>
      
      <div className="text-xs text-white/80 mb-1">Day Streak</div>
      
      {displayStreak > 0 && streak?.longestStreak && streak.longestStreak > 0 && (
        <div className="text-xs text-white/60">
          Best: {streak.longestStreak} days
        </div>
      )}
    </div>
  )
}

