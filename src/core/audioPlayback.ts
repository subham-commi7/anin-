import { OutputVoiceProfileEntity, LanguageCode, VoiceProcessingMode } from '../types';
import { LocalStorageManager } from './storage';

export interface SynthesisStatus {
  isSpeaking: boolean;
  activeProfile: OutputVoiceProfileEntity | null;
  lastSpokenText: string;
  language: LanguageCode;
}

export class AudioPlaybackManager {
  private static audioCtx: AudioContext | null = null;
  private static currentUtterance: SpeechSynthesisUtterance | null = null;
  private static listeners: ((status: SynthesisStatus) => void)[] = [];
  private static status: SynthesisStatus = {
    isSpeaking: false,
    activeProfile: null,
    lastSpokenText: '',
    language: 'en'
  };

  static subscribe(listener: (status: SynthesisStatus) => void): () => void {
    this.listeners.push(listener);
    listener(this.status);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  private static notify(): void {
    for (const listener of this.listeners) {
      listener(this.status);
    }
  }

  /**
   * EMERGENCY BARGE-IN STOP:
   * Instantly stops any spoken audio or audio context output.
   */
  static emergencyStop(): void {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    if (this.audioCtx && this.audioCtx.state === 'running') {
      this.audioCtx.suspend().catch(() => {});
    }
    this.status.isSpeaking = false;
    this.notify();

    LocalStorageManager.addAuditLog({
      id: 'log_' + Date.now(),
      timestamp: Date.now(),
      eventType: 'BARGE_IN_TRIGGERED',
      speakerIdentified: 'Subham',
      confidenceScore: 1.0,
      actionTaken: 'Spoken output interrupted immediately by barge-in event',
      sensitiveAudioStored: false
    });
  }

  /**
   * Speak output using Profile B (Anin Output Voice Profile).
   * Note: SYSTEM B NEVER has authorization privileges!
   */
  static speak(text: string, language: LanguageCode = 'en'): Promise<void> {
    return new Promise((resolve) => {
      this.emergencyStop();

      const activeProfile = LocalStorageManager.getActiveProfile();
      this.status = {
        isSpeaking: true,
        activeProfile,
        lastSpokenText: text,
        language
      };
      this.notify();

      if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
        // Fallback simulated acoustic playback
        this.playSyntheticTone(activeProfile.preferredPitch || 1.15, 1200);
        setTimeout(() => {
          this.status.isSpeaking = false;
          this.notify();
          resolve();
        }, 1500);
        return;
      }

      const utterance = new SpeechSynthesisUtterance(text);
      this.currentUtterance = utterance;

      // Apply profile attributes
      utterance.pitch = Math.max(0.5, Math.min(2.0, activeProfile.preferredPitch || 1.15));
      utterance.rate = Math.max(0.6, Math.min(1.8, activeProfile.preferredRate || 1.0));
      utterance.volume = Math.max(0.1, Math.min(1.0, activeProfile.volume || 1.0));

      // Match language locale
      const langTag = language === 'bn' ? 'bn-IN' : language === 'hi' ? 'hi-IN' : 'en-US';
      utterance.lang = langTag;

      // Match female preferred voice if available
      const voices = window.speechSynthesis.getVoices();
      const match = voices.find(
        (v) =>
          v.lang.startsWith(langTag.split('-')[0]) &&
          (v.name.toLowerCase().includes('female') ||
            v.name.toLowerCase().includes('google') ||
            v.name.toLowerCase().includes('samantha') ||
            v.name.toLowerCase().includes('priya') ||
            v.name.toLowerCase().includes('neerja'))
      ) || voices.find((v) => v.lang.startsWith(langTag.split('-')[0]));

      if (match) {
        utterance.voice = match;
      }

      utterance.onend = () => {
        this.status.isSpeaking = false;
        this.notify();
        resolve();
      };

      utterance.onerror = (e) => {
        console.warn('SpeechSynthesis error:', e);
        this.status.isSpeaking = false;
        this.notify();
        resolve();
      };

      // Ensure audio context is running for visualizer
      this.getAudioContext().resume().catch(() => {});
      window.speechSynthesis.speak(utterance);
    });
  }

  private static getAudioContext(): AudioContext {
    if (!this.audioCtx) {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.audioCtx = new AudioCtx();
    }
    return this.audioCtx;
  }

  private static playSyntheticTone(pitchMultiplier: number, durationMs: number): void {
    try {
      const ctx = this.getAudioContext();
      if (ctx.state === 'suspended') {
        ctx.resume();
      }
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(260 * pitchMultiplier, ctx.currentTime);
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + durationMs / 1000);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + durationMs / 1000);
    } catch {
      // Audio context may require user interaction gesture
    }
  }
}
