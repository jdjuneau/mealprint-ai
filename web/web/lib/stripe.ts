/**
 * Stripe Client Configuration
 * 
 * Note: Current implementation uses server-side checkout (redirects to Stripe),
 * so the publishable key is not strictly required. However, it's stored here
 * for potential future client-side features.
 */

import { loadStripe, Stripe } from '@stripe/stripe-js'

// Get publishable key from environment variable
const STRIPE_PUBLISHABLE_KEY = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY || ''

/**
 * Initialize Stripe client
 * Only needed if implementing client-side Stripe features
 */
let stripePromise: Promise<Stripe | null> | null = null

export const getStripe = (): Promise<Stripe | null> => {
  if (!stripePromise && STRIPE_PUBLISHABLE_KEY) {
    stripePromise = loadStripe(STRIPE_PUBLISHABLE_KEY)
  }
  return stripePromise || Promise.resolve(null)
}

/**
 * Check if Stripe is configured
 */
export const isStripeConfigured = (): boolean => {
  return !!STRIPE_PUBLISHABLE_KEY
}

