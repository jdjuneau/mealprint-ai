'use client'

interface ProgressRingCardProps {
  caloriesConsumed: number
  caloriesBurned: number
  dailyGoal: number
  onClick?: () => void
}

export default function ProgressRingCard({
  caloriesConsumed,
  caloriesBurned,
  dailyGoal,
  onClick
}: ProgressRingCardProps) {
  const caloriesRemaining = dailyGoal - caloriesConsumed

  return (
    <div
      className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-3 cursor-pointer hover:bg-white/20 transition-colors min-h-[120px] flex flex-col items-center justify-center"
      onClick={onClick}
    >
      <div className="text-2xl mb-1">⚡</div>
      
      <div className={`text-3xl font-bold mb-1 ${caloriesRemaining >= 0 ? 'text-white' : 'text-red-300'}`}>
        {caloriesRemaining}
      </div>
      
      <div className="text-xs text-white/80 mb-1">Calories Remaining</div>
      <div className="text-xs text-white/60 mb-2">To reach your daily goal</div>
      
      <div className="flex justify-around w-full text-center text-xs">
        <div>
          <div className="text-xs font-semibold text-white">{caloriesConsumed}</div>
          <div className="text-xs text-white/70">from meals</div>
        </div>
        <div>
          <div className="text-xs font-semibold text-white">{dailyGoal}</div>
          <div className="text-xs text-white/70">daily goal</div>
        </div>
        <div>
          <div className="text-xs font-semibold text-red-300">{caloriesBurned}</div>
          <div className="text-xs text-white/70">from workouts</div>
        </div>
      </div>
    </div>
  )
}
