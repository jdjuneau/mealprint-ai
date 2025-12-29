import { NextRequest, NextResponse } from 'next/server';

// PayPal Configuration - LIVE MODE
const PAYPAL_CLIENT_ID = process.env.PAYPAL_CLIENT_ID || 'AVAJ1CAkImxnCeod09cBE_A3SYg1nRzqtlhVAlNSqoTvEybMl3arvkeyw7nbiY6KIe7aZGTVMC9UIeZd';
const PAYPAL_CLIENT_SECRET = process.env.PAYPAL_CLIENT_SECRET || 'EOjt47l7cZ_pVBNDdHijKICTY2lN8NDryJllMHZgUzwB2BrLknZrsVDuy2JXMaYE2R_ylEzrCk0ugqbV';
const PAYPAL_BASE_URL = 'https://api.paypal.com'; // LIVE mode

/**
 * Get PayPal access token
 */
async function getPayPalAccessToken(): Promise<string> {
  const auth = Buffer.from(`${PAYPAL_CLIENT_ID}:${PAYPAL_CLIENT_SECRET}`).toString('base64');
  
  const response = await fetch(`${PAYPAL_BASE_URL}/v1/oauth2/token`, {
    method: 'POST',
    headers: {
      'Authorization': `Basic ${auth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Failed to get PayPal access token: ${error}`);
  }

  const data = await response.json();
  return data.access_token;
}

/**
 * Map plan IDs to pricing
 */
function getPlanPricing(planId: string): { amount: string; description: string } {
  const normalizedPlanId = planId.replace('_stripe', '').replace('_paypal', '');
  
  const pricing: { [key: string]: { amount: string; description: string } } = {
    'pro_monthly': {
      amount: '9.99',
      description: 'Pro Monthly Subscription',
    },
    'pro_yearly': {
      amount: '99.99',
      description: 'Pro Yearly Subscription',
    },
  };

  return pricing[normalizedPlanId] || {
    amount: '9.99',
    description: 'Pro Subscription',
  };
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { planId, userId } = body;

    if (!planId) {
      return NextResponse.json({ error: 'Missing planId' }, { status: 400 });
    }

    // userId is optional for PayPal order creation

    // Get PayPal access token
    const accessToken = await getPayPalAccessToken();

    // Get pricing for the plan
    const pricing = getPlanPricing(planId);

    // Create PayPal order
    const origin = request.headers.get('origin') || 'http://localhost:3000';
    const orderData = {
      intent: 'CAPTURE',
      purchase_units: [
        {
          reference_id: userId || `plan_${planId}_${Date.now()}`,
          amount: {
            currency_code: 'USD',
            value: pricing.amount,
          },
          description: pricing.description,
        },
      ],
      application_context: {
        brand_name: 'Coachie',
        landing_page: 'BILLING',
        user_action: 'PAY_NOW',
        return_url: `${origin}/subscription?success=true&token=PAYPAL_TOKEN`,
        cancel_url: `${origin}/subscription?canceled=true`,
      },
    };

    const response = await fetch(`${PAYPAL_BASE_URL}/v2/checkout/orders`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify(orderData),
    });

    if (!response.ok) {
      const errorText = await response.text();
      let errorData;
      try {
        errorData = JSON.parse(errorText);
      } catch {
        errorData = { message: errorText };
      }
      console.error('PayPal API error:', {
        status: response.status,
        statusText: response.statusText,
        error: errorData,
        requestData: orderData, // Log what we sent
      });
      return NextResponse.json(
        { 
          error: 'Failed to create PayPal order', 
          details: errorData,
          message: errorData.message || errorData.name || 'Unknown PayPal error'
        },
        { status: response.status }
      );
    }

    const order = await response.json();

    // Extract approval URL from order links
    const approvalUrl = order.links?.find((link: any) => link.rel === 'approve')?.href;

    return NextResponse.json({
      id: order.id,
      status: order.status,
      approvalUrl: approvalUrl || null,
    });
  } catch (error: any) {
    console.error('PayPal order creation error:', error);
    return NextResponse.json(
      { error: 'Failed to create order', details: error.message },
      { status: 500 }
    );
  }
}

