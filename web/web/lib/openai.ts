import OpenAI from 'openai'

if (!process.env.NEXT_PUBLIC_OPENAI_API_KEY) {
  throw new Error('Missing NEXT_PUBLIC_OPENAI_API_KEY environment variable')
}

export const openai = new OpenAI({
  apiKey: process.env.NEXT_PUBLIC_OPENAI_API_KEY,
  dangerouslyAllowBrowser: true, // Required for client-side usage
})

// Cache for responses to reduce API calls
const responseCache = new Map<string, { response: string; timestamp: number }>()
const CACHE_DURATION = 60 * 60 * 1000 // 1 hour

export const getCachedResponse = (key: string): string | null => {
  const cached = responseCache.get(key)
  if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
    return cached.response
  }
  if (cached) {
    responseCache.delete(key) // Remove expired cache
  }
  return null
}

export const setCachedResponse = (key: string, response: string) => {
  responseCache.set(key, { response, timestamp: Date.now() })

  // Limit cache size to prevent memory issues
  if (responseCache.size > 50) {
    const firstKey = responseCache.keys().next().value
    if (firstKey) {
      responseCache.delete(firstKey)
    }
  }
}
