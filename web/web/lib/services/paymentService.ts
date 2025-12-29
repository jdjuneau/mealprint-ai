import { httpsCallable } from 'firebase/functions';
import { functions } from '../firebase';

interface PaymentSession {
  sessionId?: string;
  provider: 'stripe' | 'paypal';
  checkoutUrl?: string;
}

export default class PaymentService {
  private static instance: PaymentService

  private constructor() {}

  static getInstance(): PaymentService {
    if (!PaymentService.instance) {
      PaymentService.instance = new PaymentService()
    }
    return PaymentService.instance
  }

  /**
   * Create Stripe checkout session
   * Creates checkout session via API route, then redirects
   */
  async createStripeCheckoutSession(
    userId: string,
    planId: string,
    successUrl: string,
    cancelUrl: string
  ): Promise<PaymentSession | null> {
    try {
      console.log('🚀 Creating Stripe checkout session:', { planId, userId });
      
      // Create checkout session via API route
      const response = await fetch('/api/stripe/create-checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ planId, userId, successUrl, cancelUrl }),
      });

      console.log('📡 Stripe API response status:', response.status);

      if (!response.ok) {
        let error;
        try {
          error = await response.json();
        } catch {
          error = { error: `HTTP ${response.status}: ${response.statusText}` };
        }
        console.error('Stripe API error:', error);
        throw new Error(error.error || error.details || 'Failed to create Stripe checkout session');
      }

      const data = await response.json();
      console.log('Stripe API response:', data);
      
      const { sessionId, url } = data;

      if (!url) {
        console.error('No URL in Stripe response:', data);
        throw new Error('No checkout URL returned from Stripe');
      }

      console.log('Redirecting to Stripe checkout:', url);
      // Redirect to Stripe Checkout
      window.location.href = url;

      // Return session info (won't execute due to redirect, but for type safety)
      return {
        sessionId,
        provider: 'stripe',
        checkoutUrl: url,
      };

    } catch (error) {
      console.error('Error creating Stripe checkout session:', error)
      throw error
    }
  }

  /**
   * Create PayPal order
   * Creates order via API route, then redirects to PayPal approval
   */
  async createPayPalOrder(
    userId: string,
    planId: string,
    returnUrl?: string,
    cancelUrl?: string
  ): Promise<PaymentSession | null> {
    try {
      // Create order via API route
      const response = await fetch('/api/paypal/create-order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ planId, userId }),
      });

      if (!response.ok) {
        let error;
        try {
          error = await response.json();
        } catch {
          error = { error: `HTTP ${response.status}: ${response.statusText}` };
        }
        console.error('PayPal API error:', error);
        throw new Error(error.error || error.details || 'Failed to create PayPal order');
      }

      const order = await response.json();

      if (!order.id) {
        throw new Error('No order ID returned from PayPal');
      }

      // Redirect to PayPal approval URL if available
      if (order.approvalUrl) {
        window.location.href = order.approvalUrl;
      } else {
        // Fallback: redirect to success page with order ID
        const successUrl = returnUrl || `${window.location.origin}/subscription?success=true&token=${order.id}`;
        window.location.href = successUrl;
      }

      return {
        sessionId: order.id,
        provider: 'paypal',
        checkoutUrl: order.approvalUrl || `${window.location.origin}/subscription?success=true&token=${order.id}`,
      };

    } catch (error) {
      console.error('Error creating PayPal order:', error)
      throw error
    }
  }

  /**
   * Get subscription plans
   */
  async getSubscriptionPlans(): Promise<any[]> {
    try {
      const getPlans = httpsCallable(functions, 'getSubscriptionPlans')
      const result = await getPlans({ platform: 'web' })
      return result.data as any[]
    } catch (error) {
      console.error('Error getting subscription plans:', error)
      throw error
    }
  }

  /**
   * Verify Stripe payment
   */
  async verifyStripePayment(sessionId: string): Promise<any> {
    try {
      // Check if this is a mock payment
      if (sessionId.startsWith('MOCK_STRIPE_')) {
        console.log('✅ Mock Stripe payment verified:', sessionId)
        return {
          success: true,
          sessionId,
          status: 'paid',
          amount: 999, // $9.99
          currency: 'usd'
        }
      }

      const verifyPayment = httpsCallable(functions, 'verifyStripePayment')
      const result = await verifyPayment({ sessionId })
      return result.data
    } catch (error) {
      console.error('Error verifying Stripe payment:', error)
      throw error
    }
  }

  /**
   * Verify PayPal payment
   */
  async verifyPayPalPayment(orderId: string): Promise<any> {
    try {
      // Check if this is a mock payment
      if (orderId.startsWith('MOCK_')) {
        console.log('✅ Mock PayPal payment verified:', orderId)
        return {
          success: true,
          orderId,
          status: 'COMPLETED',
          amount: 999, // $9.99
          currency: 'USD'
        }
      }

      const verifyPayment = httpsCallable(functions, 'verifyPayPalPayment')
      const result = await verifyPayment({ orderId })
      return result.data
    } catch (error) {
      console.error('Error verifying PayPal payment:', error)
      throw error
    }
  }

  /**
   * Get subscription status
   */
  async getSubscriptionStatus(userId: string): Promise<any> {
    try {
      const getStatus = httpsCallable(functions, 'getSubscriptionStatus')
      const result = await getStatus({ userId })
      return result.data
    } catch (error) {
      console.error('Error getting subscription status:', error)
      throw error
    }
  }

  /**
   * Cancel Stripe subscription
   */
  async cancelStripeSubscription(subscriptionId: string): Promise<any> {
    try {
      const cancelSubscription = httpsCallable(functions, 'cancelStripeSubscription')
      const result = await cancelSubscription({ subscriptionId })
      return result.data
    } catch (error) {
      console.error('Error canceling Stripe subscription:', error)
      throw error
    }
  }

  /**
   * Cancel PayPal subscription
   */
  async cancelPayPalSubscription(subscriptionId: string): Promise<any> {
    try {
      const cancelSubscription = httpsCallable(functions, 'cancelPayPalSubscription')
      const result = await cancelSubscription({ subscriptionId })
      return result.data
    } catch (error) {
      console.error('Error canceling PayPal subscription:', error)
      throw error
    }
  }

  /**
   * Get subscription plans (alias for getSubscriptionPlans)
   */
  async getPlans(): Promise<any[]> {
    return this.getSubscriptionPlans()
  }

  /**
   * Handle payment success
   */
  async handlePaymentSuccess(provider: 'stripe' | 'paypal', paymentId: string, userId: string): Promise<any> {
    try {
      if (provider === 'stripe') {
        return await this.verifyStripePayment(paymentId)
      } else {
        return await this.verifyPayPalPayment(paymentId)
      }
    } catch (error) {
      console.error('Error handling payment success:', error)
      throw error
    }
  }
}