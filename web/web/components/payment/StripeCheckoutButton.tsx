'use client'

import { useState } from 'react'
import toast from 'react-hot-toast'

interface StripeCheckoutButtonProps {
  planId: string
  userId: string
  onSuccess?: () => void
}

export default function StripeCheckoutButton({
  planId,
  userId,
  onSuccess,
}: StripeCheckoutButtonProps) {
  const [loading, setLoading] = useState(false)

  const handleCheckout = async () => {
    setLoading(true)
    try {
      console.log('🛒 Starting Stripe checkout for plan:', planId)
      
      const PaymentService = (await import('../../lib/services/paymentService')).default
      const paymentService = PaymentService.getInstance()

      const successUrl = `${window.location.origin}/subscription?success=true&session_id={CHECKOUT_SESSION_ID}`
      const cancelUrl = `${window.location.origin}/subscription?canceled=true`

      console.log('📋 Checkout URLs:', { successUrl, cancelUrl })

      // Direct Stripe integration - this will redirect immediately
      const session = await paymentService.createStripeCheckoutSession(
        userId,
        planId,
        successUrl,
        cancelUrl
      )

      console.log('✅ Session created:', session)

      // If we get a session with a checkoutUrl, redirect manually (in case service didn't redirect)
      if (session?.checkoutUrl) {
        console.log('🔗 Redirecting to:', session.checkoutUrl)
        window.location.href = session.checkoutUrl
        // Don't set loading to false - we're redirecting
        return
      } else {
        // If we reach here without a redirect, there was an error
        console.error('❌ No checkout URL in session:', session)
        toast.error('Failed to start checkout - no URL returned')
        setLoading(false)
      }

    } catch (error: any) {
      console.error('❌ Error creating checkout:', error)
      const errorMessage = error?.message || 'Failed to start checkout'
      toast.error(errorMessage)
      setLoading(false)
    }
  }

  return (
    <button
      onClick={handleCheckout}
      disabled={loading}
      className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium"
    >
      {loading ? 'Processing...' : '💳 Pay with Stripe'}
    </button>
  )
}
