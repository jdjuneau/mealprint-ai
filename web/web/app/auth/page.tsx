'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import AuthScreen from '../../components/AuthScreen'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

export default function AuthPage() {
  const { user } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (user) {
      router.push('/home')
    }
  }, [user, router])

  return <AuthScreen />
}
