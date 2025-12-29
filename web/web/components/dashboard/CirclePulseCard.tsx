'use client'

interface Circle {
  id: string
  name: string
  goal?: string
  createdBy?: string
  members?: string[]
}

interface CirclePulseCardProps {
  circle: Circle
  pulseScale?: number
  onNavigateToCircle: () => void
  totalCircles?: number
  hasNotifications?: boolean
}

export default function CirclePulseCard({
  circle,
  pulseScale = 1,
  onNavigateToCircle,
  totalCircles = 0,
  hasNotifications = false
}: CirclePulseCardProps) {
  const displayName = circle.name.toLowerCase().trim() === 'test' || circle.name.trim() === '' 
    ? 'Join a circle' 
    : circle.name

  return (
    <div
      className="bg-white/12 rounded-lg border border-blue-500/12 p-4 cursor-pointer hover:bg-white/20 transition-colors"
      onClick={onNavigateToCircle}
    >
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-xl font-bold text-white">My Circles</h3>
        {hasNotifications && (
          <div className="w-3 h-3 bg-red-500 rounded-full"></div>
        )}
      </div>
      
      <div className="flex items-center justify-between">
        {hasNotifications ? (
          <div className="flex items-center gap-2">
            <svg className="w-5 h-5 text-red-400" fill="currentColor" viewBox="0 0 20 20">
              <path d="M10 2a6 6 0 00-6 6v3.586l-.707.707A1 1 0 004 14h12a1 1 0 00.707-1.707L16 11.586V8a6 6 0 00-6-6zM10 18a3 3 0 01-3-3h6a3 3 0 01-3 3z" />
            </svg>
            <span className="text-sm font-medium text-red-400">New activity</span>
          </div>
        ) : (
          <span className="text-white/90">{displayName}</span>
        )}
        
        <svg className="w-8 h-8 text-white/80" fill="currentColor" viewBox="0 0 20 20">
          <path d="M13 6a3 3 0 11-6 0 3 3 0 016 0zM18 8a2 2 0 11-4 0 2 2 0 014 0zM14 15a4 4 0 00-8 0v3h8v-3zM6 8a2 2 0 11-4 0 2 2 0 014 0zM16 18v-3a5.972 5.972 0 00-.75-2.906A3.005 3.005 0 0119 15v3h-3zM4.75 12.094A5.973 5.973 0 004 15v3H1v-3a3 3 0 013.75-2.906z" />
        </svg>
      </div>
      
      {totalCircles > 0 && (
        <p className="text-xs text-white/70 mt-2">{totalCircles} {totalCircles === 1 ? 'circle' : 'circles'}</p>
      )}
    </div>
  )
}

