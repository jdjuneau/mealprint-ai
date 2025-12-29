'use client'

import { ReactNode } from 'react'

interface CoachieCardProps {
  children: ReactNode
  className?: string
  onClick?: () => void
  variant?: 'default' | 'elevated' | 'outlined'
}

export default function CoachieCard({
  children,
  className = '',
  onClick,
  variant = 'default'
}: CoachieCardProps) {
  const baseClasses = 'rounded-xl transition-all duration-200'

  const variantClasses = {
    default: 'bg-white/90 backdrop-blur-sm shadow-coachie border border-white/20',
    elevated: 'bg-white shadow-coachie-lg border border-white/30',
    outlined: 'bg-transparent border-2 border-primary-200 hover:border-primary-300'
  }

  const interactiveClasses = onClick
    ? 'cursor-pointer hover:shadow-coachie-lg hover:scale-[1.02] active:scale-[0.98]'
    : ''

  return (
    <div
      className={`${baseClasses} ${variantClasses[variant]} ${interactiveClasses} ${className}`}
      onClick={onClick}
    >
      {children}
    </div>
  )
}
