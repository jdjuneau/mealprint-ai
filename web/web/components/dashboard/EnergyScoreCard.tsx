'use client'

interface EnergyScoreCardProps {
  score: number
  hrv?: number | null
  sleepHours?: number | null
  color: string
  scale?: number
  onShare?: () => void
  onClick?: () => void
}

export default function EnergyScoreCard({
  score,
  hrv,
  sleepHours,
  color,
  scale = 1,
  onShare,
  onClick
}: EnergyScoreCardProps) {
  return (
    <div
      className="rounded-lg shadow-lg p-6 cursor-pointer transition-transform w-full min-h-[200px] flex items-center justify-center"
      style={{
        backgroundColor: color,
        transform: `scale(${scale})`
      }}
      onClick={onClick}
    >
      <div className="text-center">
        <h3 className="text-lg font-medium text-white/90 mb-4">
          Coachie Flow Score
        </h3>
        
        <div className="mb-4">
          <div className="text-7xl font-bold text-white mb-2">
            {score}
          </div>
        </div>

        {(hrv !== null && hrv !== undefined) || (sleepHours !== null && sleepHours !== undefined) ? (
          <div className="flex justify-center gap-4 mb-4">
            {hrv !== null && hrv !== undefined && (
              <div className="text-white/80">
                HRV: {Math.round(hrv)}
              </div>
            )}
            {sleepHours !== null && sleepHours !== undefined && (
              <div className="text-white/80">
                Sleep: {Math.round(sleepHours)}h
              </div>
            )}
          </div>
        ) : null}

        {onShare && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              onShare()
            }}
            className="text-white hover:text-white/80"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
            </svg>
          </button>
        )}
      </div>
    </div>
  )
}

