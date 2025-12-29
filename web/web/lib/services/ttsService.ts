/**
 * Web Text-to-Speech Service
 * Uses Web Speech API speechSynthesis for browser-based TTS
 * Matches Android TTS functionality with voice styles
 */

export type VoiceStyle = 'morning' | 'wins' | 'mindfulness' | 'insights' | 'journal'

interface VoiceConfig {
  rate: number
  pitch: number
}

const VOICE_STYLES: Record<VoiceStyle, VoiceConfig> = {
  morning: { rate: 1.1, pitch: 1.08 },
  wins: { rate: 1.1, pitch: 1.08 },
  mindfulness: { rate: 0.9, pitch: 0.98 },
  insights: { rate: 1.05, pitch: 1.02 },
  journal: { rate: 1.05, pitch: 1.02 },
}

const CACHE_KEY_PREFIX = 'tts_cache_'
const CACHE_DURATION_MS = 5 * 60 * 1000 // 5 minutes

interface CachedPhrase {
  text: string
  timestamp: number
}

class WebTTSService {
  private isSpeaking = false
  private lastPhrases: CachedPhrase[] = []
  private currentUtterance: SpeechSynthesisUtterance | null = null

  constructor() {
    this.loadCache()
    // Clean up on page unload
    if (typeof window !== 'undefined') {
      window.addEventListener('beforeunload', () => this.stop())
      window.addEventListener('visibilitychange', () => {
        if (document.hidden) {
          this.stop()
        }
      })
    }
  }

  private loadCache() {
    try {
      if (typeof window === 'undefined') return
      const cached = localStorage.getItem(`${CACHE_KEY_PREFIX}phrases`)
      if (cached) {
        this.lastPhrases = JSON.parse(cached)
        // Remove expired entries
        const now = Date.now()
        this.lastPhrases = this.lastPhrases.filter(
          (phrase) => now - phrase.timestamp < CACHE_DURATION_MS
        )
        this.saveToCache()
      }
    } catch (error) {
      console.error('Error loading TTS cache:', error)
    }
  }

  private saveToCache() {
    try {
      if (typeof window === 'undefined') return
      localStorage.setItem(`${CACHE_KEY_PREFIX}phrases`, JSON.stringify(this.lastPhrases))
    } catch (error) {
      console.error('Error saving TTS cache:', error)
    }
  }

  private async addToCache(text: string) {
    try {
      const now = Date.now()
      this.lastPhrases.push({ text, timestamp: now })
      // Keep only last 5 phrases
      if (this.lastPhrases.length > 5) {
        this.lastPhrases = this.lastPhrases.slice(-5)
      }
      this.saveToCache()
    } catch (error) {
      console.error('Error adding to TTS cache:', error)
    }
  }

  private isRecentlySpoken(text: string): boolean {
    const now = Date.now()
    return this.lastPhrases.some(
      (phrase) => phrase.text === text && now - phrase.timestamp < CACHE_DURATION_MS
    )
  }

  /**
   * Check if TTS is supported in this browser
   */
  isSupported(): boolean {
    if (typeof window === 'undefined') return false
    return 'speechSynthesis' in window
  }

  /**
   * Get available voices
   */
  getVoices(): SpeechSynthesisVoice[] {
    if (typeof window === 'undefined' || !this.isSupported()) return []
    return speechSynthesis.getVoices()
  }

  /**
   * Speak text with specified voice style
   */
  async speak(
    text: string,
    style: VoiceStyle = 'insights',
    options?: {
      onStart?: () => void
      onDone?: () => void
      onError?: (error: Error) => void
    }
  ): Promise<void> {
    if (typeof window === 'undefined' || !this.isSupported()) {
      console.warn('Speech synthesis not supported in this browser')
      return
    }

    if (!text || text.trim().length === 0) {
      return
    }

    // Check cache - skip if recently spoken
    if (this.isRecentlySpoken(text)) {
      console.log('Skipping TTS - recently spoken')
      return
    }

    // Stop any current speech
    this.stop()

    const config = VOICE_STYLES[style]

    return new Promise((resolve, reject) => {
      try {
        this.isSpeaking = true
        options?.onStart?.()

        const utterance = new SpeechSynthesisUtterance(text)
        this.currentUtterance = utterance

        // Set voice properties
        utterance.rate = config.rate
        utterance.pitch = config.pitch
        utterance.lang = 'en-US'

        // Try to use a high-quality voice (prefer neural voices if available)
        const voices = this.getVoices()
        if (voices.length > 0) {
          // Prefer Google voices or neural voices
          const preferredVoice = voices.find(
            (v) => v.name.toLowerCase().includes('google') || v.name.toLowerCase().includes('neural')
          ) || voices.find((v) => v.lang.startsWith('en'))
          
          if (preferredVoice) {
            utterance.voice = preferredVoice
          }
        }

        // Event handlers
        utterance.onstart = () => {
          this.isSpeaking = true
        }

        utterance.onend = () => {
          this.isSpeaking = false
          this.currentUtterance = null
          this.addToCache(text)
          options?.onDone?.()
          resolve()
        }

        utterance.onerror = (event) => {
          this.isSpeaking = false
          this.currentUtterance = null
          const error = new Error(`Speech synthesis error: ${event.error}`)
          options?.onError?.(error)
          reject(error)
        }

        // Speak
        speechSynthesis.speak(utterance)
      } catch (error) {
        this.isSpeaking = false
        this.currentUtterance = null
        const err = error instanceof Error ? error : new Error(String(error))
        options?.onError?.(err)
        reject(err)
      }
    })
  }

  stop(): void {
    if (typeof window === 'undefined' || !this.isSupported()) return
    
    if (this.isSpeaking) {
      speechSynthesis.cancel()
      this.isSpeaking = false
      this.currentUtterance = null
    }
  }

  isCurrentlySpeaking(): boolean {
    return this.isSpeaking
  }

  cleanup(): void {
    this.stop()
  }
}

// Singleton instance
export const ttsService = new WebTTSService()

// Load voices when they become available (some browsers load them asynchronously)
if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
  // Chrome loads voices asynchronously
  if (speechSynthesis.onvoiceschanged !== undefined) {
    speechSynthesis.onvoiceschanged = () => {
      // Voices loaded
    }
  }
}

