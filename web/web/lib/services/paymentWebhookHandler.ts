/**
 * Payment Webhook Handler (Client-side helper)
 * Handles payment callbacks from Stripe and PayPal
 * Note: Actual webhook processing happens in Cloud Functions
 */

import { functions } from '../firebase'
import { httpsCallable } from 'firebase/functions'

export interface WebhookEvent {
  type: string
  data: any
  provider: 'stripe' | 'paypal'
}

class PaymentWebhookHandler {
  private static instance: PaymentWebhookHandler

  private constructor() {}

  static getInstance(): PaymentWebhookHandler {
    if (!PaymentWebhookHandler.instance) {
      PaymentWebhookHandler.instance = new PaymentWebhookHandler()
    }
    return PaymentWebhookHandler.instance
  }

  /**
   * Process Stripe webhook event (called from Cloud Function)
   */
  async processStripeWebhook(event: WebhookEvent): Promise<boolean> {
    try {
      const processWebhook = httpsCallable(functions, 'processStripeWebhook')
      const result = await processWebhook({
        event: event.data,
        platform: 'web',
      })

      return (result.data as any)?.success || false
    } catch (error) {
      console.error('Error processing Stripe webhook:', error)
      return false
    }
  }

  /**
   * Process PayPal webhook event (called from Cloud Function)
   */
  async processPayPalWebhook(event: WebhookEvent): Promise<boolean> {
    try {
      const processWebhook = httpsCallable(functions, 'processPayPalWebhook')
      const result = await processWebhook({
        event: event.data,
        platform: 'web',
      })

      return (result.data as any)?.success || false
    } catch (error) {
      console.error('Error processing PayPal webhook:', error)
      return false
    }
  }
}

export default PaymentWebhookHandler
