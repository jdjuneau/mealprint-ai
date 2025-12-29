import { NextRequest, NextResponse } from 'next/server';
import Stripe from 'stripe';

// Stripe Configuration - LIVE MODE
const STRIPE_SECRET_KEY = process.env.STRIPE_SECRET_KEY;

const stripe = new Stripe(STRIPE_SECRET_KEY);

/**
 * Map plan IDs to LIVE mode Stripe price IDs
 */
function getStripePriceId(planId: string): string {
  // If it's already a price ID, return it
  if (planId.startsWith('price_')) {
    return planId;
  }

  // Normalize plan ID
  const normalizedPlanId = planId.replace('_stripe', '').replace('_paypal', '');
  
  // Use the LIVE price IDs you provided
  const priceMap: { [key: string]: string } = {
    'pro_monthly': 'price_1SfRRSCTYzpYqKhFEktdOYtf',
    'pro_yearly': 'price_1SfRSCCTYzpYqKhFBu0qpAoi',
  };

  return priceMap[normalizedPlanId] || planId;
}

export async function POST(request: NextRequest) {
  try {
    console.log('🔵 Stripe checkout request received');
    
    if (!STRIPE_SECRET_KEY || !STRIPE_SECRET_KEY.startsWith('sk_')) {
      console.error('❌ Stripe secret key invalid');
      return NextResponse.json(
        { error: 'Stripe secret key not configured. Please add STRIPE_SECRET_KEY to .env.local' },
        { status: 500 }
      );
    }

    const { planId, userId, successUrl, cancelUrl } = await request.json();
    console.log('📦 Request data:', { planId, userId, hasSuccessUrl: !!successUrl, hasCancelUrl: !!cancelUrl });

    if (!planId) {
      return NextResponse.json({ error: 'Missing planId' }, { status: 400 });
    }

    if (!userId) {
      return NextResponse.json({ error: 'Missing userId' }, { status: 400 });
    }

    console.log('🔍 Getting Stripe LIVE price ID for plan:', planId);
    const priceId = getStripePriceId(planId);
    console.log('✅ Using LIVE price ID:', priceId);

    // Create Stripe Checkout Session
    console.log('💳 Creating Stripe checkout session with price:', priceId);
    const session = await stripe.checkout.sessions.create({
      payment_method_types: ['card'],
      line_items: [
        {
          price: priceId,
          quantity: 1,
        },
      ],
      mode: 'subscription',
      success_url: successUrl || `${request.headers.get('origin') || 'http://localhost:3000'}/subscription?success=true&session_id={CHECKOUT_SESSION_ID}`,
      cancel_url: cancelUrl || `${request.headers.get('origin') || 'http://localhost:3000'}/subscription?canceled=true`,
      client_reference_id: userId,
      metadata: {
        userId,
        planId,
      },
    });

    console.log('✅ Stripe session created:', { sessionId: session.id, url: session.url });

    return NextResponse.json({
      sessionId: session.id,
      url: session.url,
    });
  } catch (error: any) {
    console.error('Stripe checkout creation error:', error);
    return NextResponse.json(
      { error: 'Failed to create checkout session', details: error.message },
      { status: 500 }
    );
  }
}
