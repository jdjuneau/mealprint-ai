# Security Fix: OpenAI API Key Leak

## What Happened
An OpenAI API key was leaked in the git history and has been disabled by OpenAI.

## Current Status
✅ **Code is now secure** - All API keys are using environment variables
✅ **Stripe key removed** - No longer hardcoded
✅ **OpenAI key removed** - Using environment variables only

## Required Actions

### 1. Create New OpenAI API Key
1. Go to https://platform.openai.com/api-keys
2. Click "Create new secret key"
3. Copy the key (you won't see it again!)

### 2. Update Environment Variables

#### Web App (`web/web/.env.local`)
```env
NEXT_PUBLIC_OPENAI_API_KEY=sk-proj-your-new-key-here
```

#### Firebase Functions
```bash
firebase functions:config:set openai.key="sk-proj-your-new-key-here"
```

#### Android App (`android/local.properties`)
```properties
openai_api_key=sk-proj-your-new-key-here
```

### 3. Optional: Clean Git History
If you want to remove the old key from git history (the key is already disabled, so this is optional):

```bash
# WARNING: This rewrites history - only do this if you're the only one using this repo
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch -r ." \
  --prune-empty --tag-name-filter cat -- --all

# Force push (only if you're sure!)
git push origin --force --all
```

**Note:** The old key is already disabled, so cleaning history is optional.

## Best Practices Going Forward

1. ✅ **Never commit API keys** - Always use environment variables
2. ✅ **Use `.env.local` files** - Add to `.gitignore`
3. ✅ **Use secrets management** - For production, use proper secrets managers
4. ✅ **Review before pushing** - Check for secrets before committing

## Files That Are Safe to Commit
- Firebase API keys (public keys, domain-restricted)
- Example/template files with placeholder keys
- Configuration files with environment variable references

## Files That Should NEVER Be Committed
- `.env` or `.env.local` files with real keys
- `local.properties` with real API keys
- Any file with `sk-`, `sk_live_`, `sk_test_` followed by actual keys
- Any file with actual OpenAI keys (`sk-proj-...`)

