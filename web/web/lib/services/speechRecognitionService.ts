/**
 * Web Speech Recognition Service
 * Uses Web Speech API for real-time speech-to-text
 * Provides a unified interface for voice input across the app
 */

interface SpeechRecognitionOptions {
  continuous?: boolean
  interimResults?: boolean
  lang?: string
  maxAlternatives?: number
}

interface SpeechRecognitionCallbacks {
  onResult?: (transcript: string, isFinal: boolean) => void
  onError?: (error: string) => void
  onStart?: () => void
  onEnd?: () => void
}

class SpeechRecognitionService {
  private recognition: any = null
  private isListening = false

  constructor() {
    if (typeof window !== 'undefined') {
      // Check for browser support
      const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
      if (SpeechRecognition) {
        this.recognition = new SpeechRecognition()
        this.setupRecognition()
      }
    }
  }

  private setupRecognition() {
    if (!this.recognition) return

    // Default settings
    this.recognition.continuous = false
    this.recognition.interimResults = true
    this.recognition.lang = 'en-US'
    this.recognition.maxAlternatives = 1
  }

  /**
   * Check if speech recognition is supported
   */
  isSupported(): boolean {
    if (typeof window === 'undefined') return false
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    return !!SpeechRecognition
  }

  /**
   * Check if microphone permission is available
   */
  async checkMicrophonePermission(): Promise<boolean> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      stream.getTracks().forEach(track => track.stop())
      return true
    } catch (error) {
      return false
    }
  }

  /**
   * Request microphone permission
   */
  async requestMicrophonePermission(): Promise<boolean> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      stream.getTracks().forEach(track => track.stop())
      return true
    } catch (error) {
      console.error('Microphone permission denied:', error)
      return false
    }
  }

  /**
   * Start listening for speech
   */
  startListening(
    options: SpeechRecognitionOptions = {},
    callbacks: SpeechRecognitionCallbacks = {}
  ): void {
    if (!this.isSupported()) {
      callbacks.onError?.('Speech recognition is not supported in this browser')
      return
    }

    if (this.isListening) {
      this.stopListening()
    }

    // Apply options
    if (options.continuous !== undefined) {
      this.recognition.continuous = options.continuous
    }
    if (options.interimResults !== undefined) {
      this.recognition.interimResults = options.interimResults
    }
    if (options.lang) {
      this.recognition.lang = options.lang
    }
    if (options.maxAlternatives !== undefined) {
      this.recognition.maxAlternatives = options.maxAlternatives
    }

    // Set up event handlers
    this.recognition.onstart = () => {
      this.isListening = true
      callbacks.onStart?.()
    }

    this.recognition.onresult = (event: any) => {
      let interimTranscript = ''
      let finalTranscript = ''

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript
        if (event.results[i].isFinal) {
          finalTranscript += transcript + ' '
        } else {
          interimTranscript += transcript
        }
      }

      if (finalTranscript) {
        callbacks.onResult?.(finalTranscript.trim(), true)
      } else if (interimTranscript) {
        callbacks.onResult?.(interimTranscript, false)
      }
    }

    this.recognition.onerror = (event: any) => {
      this.isListening = false
      let errorMessage = 'Speech recognition error'
      
      switch (event.error) {
        case 'no-speech':
          errorMessage = 'No speech detected. Please try again.'
          break
        case 'audio-capture':
          errorMessage = 'No microphone found. Please check your microphone.'
          break
        case 'not-allowed':
          errorMessage = 'Microphone permission denied. Please allow microphone access.'
          break
        case 'network':
          errorMessage = 'Network error. Please check your internet connection.'
          break
        case 'aborted':
          errorMessage = 'Speech recognition was aborted.'
          break
        default:
          errorMessage = `Speech recognition error: ${event.error}`
      }
      
      callbacks.onError?.(errorMessage)
    }

    this.recognition.onend = () => {
      this.isListening = false
      callbacks.onEnd?.()
    }

    // Start recognition
    try {
      this.recognition.start()
    } catch (error) {
      console.error('Error starting speech recognition:', error)
      callbacks.onError?.('Failed to start speech recognition')
    }
  }

  /**
   * Stop listening for speech
   */
  stopListening(): void {
    if (this.recognition && this.isListening) {
      try {
        this.recognition.stop()
      } catch (error) {
        // Ignore errors when stopping
      }
      this.isListening = false
    }
  }

  /**
   * Check if currently listening
   */
  getIsListening(): boolean {
    return this.isListening
  }
}

// Singleton instance
export const speechRecognitionService = new SpeechRecognitionService()

