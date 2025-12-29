'use client'

interface NavigationTileCardProps {
  title: string
  description: string
  icon: string
  iconTint: string
  backgroundColor: string
  onClick: () => void
  score?: number
  isProOnly?: boolean
}

export default function NavigationTileCard({
  title,
  description,
  icon,
  iconTint,
  backgroundColor,
  onClick,
  score,
  isProOnly = false
}: NavigationTileCardProps) {
  return (
    <div
      className="bg-white/20 rounded-lg border p-6 cursor-pointer hover:bg-white/30 transition-colors"
      style={{
        borderColor: `${backgroundColor}66`,
        backgroundColor: `${backgroundColor}33`
      }}
      onClick={onClick}
    >
      <div className="flex items-center gap-5">
        {/* Icon */}
        <div
          className="w-16 h-16 rounded-2xl flex items-center justify-center flex-shrink-0"
          style={{ backgroundColor: `${backgroundColor}26` }}
        >
          <span className="text-3xl">{icon}</span>
        </div>

        {/* Content */}
        <div className="flex-1">
          <div className="flex items-center justify-between mb-1">
            <div className="flex items-center gap-2">
              <h3 className="text-xl font-bold text-white">{title}</h3>
              {isProOnly && (
                <span className="px-2 py-0.5 bg-gradient-to-r from-purple-500 to-pink-500 text-white text-xs font-bold rounded-full">
                  PRO
                </span>
              )}
            </div>
            {score !== undefined && (
              <div
                className="px-3 py-1.5 rounded-xl text-white font-bold"
                style={{ backgroundColor: `${backgroundColor}33` }}
              >
                {score}
              </div>
            )}
          </div>
          <p className="text-white/90 text-sm">{description}</p>
        </div>

        {/* Arrow */}
        <svg className="w-6 h-6 text-white/70 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
      </div>
    </div>
  )
}

