# Stripe Environment Variable Setup

## Stripe Publishable Key

Your Stripe publishable key has been received. To use it in the web app, add it to your environment variables.

### For Local Development

Create or update `web/.env.local`:

```bash
# Stripe Publishable Key (for potential future client-side features)
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_51SfQtdCTYzpYqKhFDclI4dWf33uHZqhwTmW9sTYJrW8mJa8A0c1Wb5hBJ8xumWGGDnhprBSh2l9iBZoln0DfmVRs00hhsOYlrU
```

### For Production (Vercel/Deployment)

Add the environment variable in your deployment platform:

**Vercel:**
1. Go to your project settings
2. Click "Environment Variables"
3. Add:
   - **Name**: `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY`
   - **Value**: `pk_live_51SfQtdCTYzpYqKhFDclI4dWf33uHZqhwTmW9sTYJrW8mJa8A0c1Wb5hBJ8xumWGGDnhprBSh2l9iBZoln0DfmVRs00hhsOYlrU`
   - **Environment**: Production, Preview, Development (select all)

### Current Status

**Note**: The current payment implementation uses **server-side checkout** (redirects to Stripe's hosted checkout page), so the publishable key is **not currently required**. 

The key is stored in `web/lib/stripe.ts` for potential future use if you want to:
- Add client-side payment elements
- Implement Stripe Elements for custom checkout
- Add payment method management features

### What's Already Configured

✅ **Stripe Secret Key** - Configured in Firebase Functions  
✅ **Stripe Publishable Key** - Received (add to `.env.local` when ready)  
⏳ **Stripe Products** - Need to create in Stripe Dashboard  
⏳ **Webhook Setup** - Will configure after deployment  

### Next Steps

1. **Add publishable key to `.env.local`** (optional for now)
2. **Create products in Stripe Dashboard**:
   - Pro Monthly ($9.99/month)
   - Pro Yearly ($99.99/year)
3. **Deploy Cloud Functions**
4. **Set up webhook** in Stripe Dashboard

