'use client'

interface WellnessQuickAccessCardProps {
  onJournal: () => void
  onMeditation: () => void
  onHabits: () => void
}

export default function WellnessQuickAccessCard({
  onJournal,
  onMeditation,
  onHabits
}: WellnessQuickAccessCardProps) {
  const WellnessQuickButton = ({ icon, label, onClick }: { icon: string; label: string; onClick: () => void }) => (
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
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-lg font-bold text-gray-900">Wellness & Habits</h3>
        <svg className="w-6 h-6 text-purple-600" fill="currentColor" viewBox="0 0 20 20">
          <path fillRule="evenodd" d="M3 3a1 1 0 000 2v8a2 2 0 002 2h2.586l-1.293 1.293a1 1 0 101.414 1.414L10 15.414l2.293 2.293a1 1 0 001.414-1.414L12.414 15H15a2 2 0 002-2V5a1 1 0 100-2H3zm11.707 4.707a1 1 0 00-1.414-1.414L10 9.586 8.707 8.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
        </svg>
      </div>

      <div className="flex gap-2">
        <WellnessQuickButton icon="📝" label="Journal" onClick={onJournal} />
        <WellnessQuickButton icon="🧘" label="Meditate" onClick={onMeditation} />
        <WellnessQuickButton icon="✅" label="Habits" onClick={onHabits} />
      </div>
    </div>
  )
}

