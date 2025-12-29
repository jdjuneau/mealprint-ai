'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter, useSearchParams } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'
import StripeCheckoutButton from '../../components/payment/StripeCheckoutButton'
import PayPalCheckoutButton from '../../components/payment/PayPalCheckoutButton'

export default function SubscriptionPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const [loading, setLoading] = useState(true)
  const [isPro, setIsPro] = useState(false)
  const [plans, setPlans] = useState<any[]>([])
  const [selectedProvider, setSelectedProvider] = useState<'stripe' | 'paypal'>('stripe')

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadSubscription()
      loadPlans()
      handlePaymentCallback()
    }
  }, [user, userProfile, router])

  const handlePaymentCallback = async () => {
    if (!user) return
    
    const success = searchParams.get('success')
    const canceled = searchParams.get('canceled')
    const sessionId = searchParams.get('session_id') // Stripe session ID
    const token = searchParams.get('token') // PayPal order token
    const payerId = searchParams.get('PayerID') // PayPal payer ID

    if (success && (sessionId || token)) {
      try {
        const PaymentService = (await import('../../lib/services/paymentService')).default
        const paymentService = PaymentService.getInstance()
        
        // Determine provider from URL params
        // Stripe uses session_id, PayPal uses token
        const provider = token ? 'paypal' : 'stripe'
        const paymentId = sessionId || token || ''
        
        const result = await paymentService.handlePaymentSuccess(provider, paymentId, user.uid)
        
        if (result.success) {
          toast.success('Subscription activated! Welcome to Pro!')
          
          // Update subscription in SubscriptionService
          const SubscriptionService = (await import('../../lib/services/subscriptionService')).default
          const subscriptionService = SubscriptionService.getInstance()
          await subscriptionService.updateSubscription(user.uid, {
            tier: 'pro',
            status: 'active',
            paymentProvider: provider,
            startDate: new Date(),
            platforms: ['web'], // Will be merged with existing platforms
          })
          
          loadSubscription() // Reload subscription status
          
          // Clean URL
          router.replace('/subscription')
        } else {
          toast.error(result.error || 'Payment verification failed')
        }
      } catch (error) {
        console.error('Error handling payment callback:', error)
        toast.error('Failed to verify payment')
      }
    } else if (canceled) {
      toast.error('Payment canceled')
      router.replace('/subscription')
    }
  }

  const loadPlans = async () => {
    try {
      const PaymentService = (await import('../../lib/services/paymentService')).default
      const paymentService = PaymentService.getInstance()
      const loadedPlans = await paymentService.getPlans()
      setPlans(loadedPlans)
    } catch (error) {
      console.error('Error loading plans:', error)
    }
  }

  const loadSubscription = async () => {
    if (!user) return
    try {
      const SubscriptionService = (await import('../../lib/services/subscriptionService')).default
      const subscriptionService = SubscriptionService.getInstance()
      const isProUser = await subscriptionService.isPro(user.uid)
      setIsPro(isProUser)
    } catch (error) {
      console.error('Error loading subscription:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSubscribe = async (planId: string = 'pro_monthly_stripe', provider: 'stripe' | 'paypal' = 'stripe') => {
    if (!user) return
    try {
      const PaymentService = (await import('../../lib/services/paymentService')).default
      const paymentService = PaymentService.getInstance()
      
      const successUrl = `${window.location.origin}/subscription?success=true&session_id={CHECKOUT_SESSION_ID}`
      const cancelUrl = `${window.location.origin}/subscription?canceled=true`

      let session: any = null
      
      if (provider === 'stripe') {
        session = await paymentService.createStripeCheckoutSession(
          user.uid,
          planId,
          successUrl,
          cancelUrl
        )
      } else {
        const returnUrl = `${window.location.origin}/subscription?success=true&token={TOKEN}`
        const cancelUrl = `${window.location.origin}/subscription?canceled=true`
        session = await paymentService.createPayPalOrder(user.uid, planId, returnUrl, cancelUrl)
      }

      if (session && session.checkoutUrl) {
        // Redirect to payment provider checkout
        window.location.href = session.checkoutUrl
      } else {
        toast.error('Failed to create checkout session')
      }
    } catch (error) {
      console.error('Error creating subscription:', error)
      toast.error('Failed to start subscription')
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto py-8 px-4">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Subscription</h1>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Free Tier */}
          <div className={`bg-white rounded-lg shadow-sm p-6 border-2 ${!isPro ? 'border-blue-500' : 'border-gray-200'}`}>
            <h2 className="text-xl font-bold text-gray-900 mb-2">Free</h2>
            <div className="text-3xl font-bold mb-4">$0<span className="text-lg text-gray-600">/month</span></div>
            <ul className="space-y-2 mb-6">
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Basic meal logging
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Workout tracking
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Basic AI chat
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✗</span> Advanced AI features
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✗</span> Weekly meal blueprints
              </li>
            </ul>
            {!isPro && (
              <div className="px-4 py-2 bg-blue-100 text-blue-700 rounded-lg text-center font-medium">
                Current Plan
              </div>
            )}
          </div>

          {/* Pro Tier */}
          <div className={`bg-white rounded-lg shadow-sm p-6 border-2 ${isPro ? 'border-blue-500' : 'border-gray-200'}`}>
            <div className="flex items-center justify-between mb-2">
              <h2 className="text-xl font-bold text-gray-900">Pro</h2>
              {isPro && (
                <span className="px-2 py-1 bg-green-100 text-green-700 rounded text-xs font-medium">
                  Active
                </span>
              )}
            </div>
            <div className="text-3xl font-bold mb-4">$9.99<span className="text-lg text-gray-600">/month</span></div>
            <ul className="space-y-2 mb-6">
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Everything in Free
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Advanced AI features
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Weekly meal blueprints
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Recipe analysis
              </li>
              <li className="flex items-center text-sm text-gray-600">
                <span className="mr-2">✓</span> Priority support
              </li>
            </ul>
            {!isPro && (
              <>
                <div className="mb-4">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Payment Method
                  </label>
                  <div className="flex gap-2 mb-2">
                    <button
                      onClick={() => setSelectedProvider('stripe')}
                      className={`flex-1 px-4 py-2 rounded-lg transition-colors ${
                        selectedProvider === 'stripe'
                          ? 'bg-blue-600 text-white'
                          : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                      }`}
                    >
                      💳 Stripe
                    </button>
                    <button
                      onClick={() => setSelectedProvider('paypal')}
                      className={`flex-1 px-4 py-2 rounded-lg transition-colors ${
                        selectedProvider === 'paypal'
                          ? 'bg-blue-600 text-white'
                          : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                      }`}
                    >
                      💰 PayPal
                    </button>
                  </div>
                </div>
                <div className="space-y-3">
                  {plans.length > 0 ? (
                    plans
                      .filter((p) => p.provider === selectedProvider)
                      .map((plan) => (
                        <div key={plan.id} className="border border-gray-200 rounded-lg p-3">
                          <div className="flex items-center justify-between mb-2">
                            <span className="font-semibold text-gray-900">{plan.name}</span>
                            <span className="text-lg font-bold">
                              ${plan.price}/{plan.interval === 'month' ? 'mo' : 'yr'}
                            </span>
                          </div>
                          {plan.interval === 'year' && (
                            <p className="text-sm text-green-600 mb-2">Save 17% vs monthly</p>
                          )}
                          {selectedProvider === 'stripe' ? (
                            <StripeCheckoutButton
                              planId={plan.id}
                              userId={user.uid}
                              onSuccess={() => {
                                toast.success('Subscription activated!')
                                loadSubscription()
                              }}
                            />
                          ) : (
                            <PayPalCheckoutButton
                              planId={plan.id}
                              userId={user.uid}
                              onSuccess={() => {
                                toast.success('Subscription activated!')
                                loadSubscription()
                              }}
                            />
                          )}
                        </div>
                      ))
                  ) : (
                    <div className="space-y-2">
                      <div className="border border-gray-200 rounded-lg p-3">
                        <div className="flex items-center justify-between mb-2">
                          <span className="font-semibold text-gray-900">Pro Monthly</span>
                          <span className="text-lg font-bold">$9.99/mo</span>
                        </div>
                        {selectedProvider === 'stripe' ? (
                          <StripeCheckoutButton
                            planId="pro_monthly_stripe"
                            userId={user.uid}
                            onSuccess={() => {
                              toast.success('Subscription activated!')
                              loadSubscription()
                            }}
                          />
                        ) : (
                          <PayPalCheckoutButton
                            planId="pro_monthly_paypal"
                            userId={user.uid}
                            onSuccess={() => {
                              toast.success('Subscription activated!')
                              loadSubscription()
                            }}
                          />
                        )}
                      </div>
                      <div className="border border-gray-200 rounded-lg p-3">
                        <div className="flex items-center justify-between mb-2">
                          <span className="font-semibold text-gray-900">Pro Yearly</span>
                          <span className="text-lg font-bold">$99.99/yr</span>
                        </div>
                        <p className="text-sm text-green-600 mb-2">Save 17% vs monthly</p>
                        {selectedProvider === 'stripe' ? (
                          <StripeCheckoutButton
                            planId="pro_yearly_stripe"
                            userId={user.uid}
                            onSuccess={() => {
                              toast.success('Subscription activated!')
                              loadSubscription()
                            }}
                          />
                        ) : (
                          <PayPalCheckoutButton
                            planId="pro_yearly_paypal"
                            userId={user.uid}
                            onSuccess={() => {
                              toast.success('Subscription activated!')
                              loadSubscription()
                            }}
                          />
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
