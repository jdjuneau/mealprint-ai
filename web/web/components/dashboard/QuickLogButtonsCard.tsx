'use client'

interface QuickLogButtonsCardProps {
  onLogMeal: () => void
  onLogSupplement: () => void
  onLogWorkout: () => void
  onLogSleep: () => void
  onLogWater: () => void
  onLogSunshine?: () => void
  onMealIdea?: () => void
  onLogWeight?: () => void
  onVoiceLogging?: () => void
}

export default function QuickLogButtonsCard({
  onLogMeal,
  onLogSupplement,
  onLogWorkout,
  onLogSleep,
  onLogWater,
  onLogSunshine,
  onMealIdea,
  onLogWeight,
  onVoiceLogging
}: QuickLogButtonsCardProps) {
  const QuickLogButton = ({ icon, label, onClick }: { icon: string; label: string; onClick: () => void }) => (
    <button
      onClick={onClick}
      className="flex-1 h-16 border border-blue-500/25 rounded-xl bg-white/16 hover:bg-white/24 transition-colors flex flex-col items-center justify-center gap-1.5"
    >
      <span className="text-2xl">{icon}</span>
      <span className="text-xs font-medium text-gray-900">{label}</span>
    </button>
  )

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <h3 className="text-lg font-bold text-gray-900 mb-4">Quick Log</h3>
      
      <div className="space-y-2">
        {/* Row 1: Core daily activities */}
        <div className="flex gap-2">
          <QuickLogButton icon="🍽️" label="Meal" onClick={onLogMeal} />
          <QuickLogButton icon="💊" label="Supplement" onClick={onLogSupplement} />
          <QuickLogButton icon="💪" label="Workout" onClick={onLogWorkout} />
        </div>

        {/* Row 2: Health & wellness tracking */}
        <div className="flex gap-2">
          <QuickLogButton icon="💧" label="Water" onClick={onLogWater} />
          {onLogSunshine && <QuickLogButton icon="☀️" label="Sunshine" onClick={onLogSunshine} />}
          <QuickLogButton icon="😴" label="Sleep" onClick={onLogSleep} />
        </div>

        {/* Row 3: Additional tools */}
        <div className="flex gap-2">
          {onMealIdea && <QuickLogButton icon="💡" label="Meal Idea" onClick={onMealIdea} />}
          {onLogWeight && <QuickLogButton icon="⚖️" label="Weight" onClick={onLogWeight} />}
          {onVoiceLogging && <QuickLogButton icon="🎤" label="Voice" onClick={onVoiceLogging} />}
        </div>
      </div>
    </div>
  )
}
