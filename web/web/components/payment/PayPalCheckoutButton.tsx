'use client'

import { useState } from 'react'
import toast from 'react-hot-toast'

interface PayPalCheckoutButtonProps {
  planId: string
  userId: string
  onSuccess?: () => void
}

export default function PayPalCheckoutButton({
  planId,
  userId,
  onSuccess,
}: PayPalCheckoutButtonProps) {
  const [loading, setLoading] = useState(false)

  const handleCheckout = async () => {
    setLoading(true)
    try {
      const PaymentService = (await import('../../lib/services/paymentService')).default
      const paymentService = PaymentService.getInstance()

      const session = await paymentService.createPayPalOrder(userId, planId)

      if (session && session.checkoutUrl) {
        // Redirect to PayPal approval URL
        window.location.href = session.checkoutUrl
      } else {
        toast.error('Failed to create PayPal order')
      }
    } catch (error) {
      console.error('Error creating PayPal order:', error)
      toast.error('Failed to start PayPal checkout')
    } finally {
      setLoading(false)
    }
  }

  return (
    <button
      onClick={handleCheckout}
      disabled={loading}
      className="w-full px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 font-medium"
    >
      {loading ? 'Processing...' : '💰 Pay with PayPal'}
    </button>
  )
}
