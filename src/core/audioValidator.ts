import { AudioQualityCheck } from '../types';

export class AudioQualityValidator {
  static validateAudioSample(
    samples: Float32Array,
    sampleRate: number = 16000
  ): AudioQualityCheck {
    const failureReasons: string[] = [];
    const durationMs = Math.round((samples.length / sampleRate) * 1000);

    // 1. Duration check (must be between 1.0s and 12s)
    if (durationMs < 1000) {
      failureReasons.push('Sample too short (minimum 1.0 second required for biometric vector)');
    } else if (durationMs > 12000) {
      failureReasons.push('Sample too long (maximum 12.0 seconds permitted)');
    }

    // 2. RMS calculation & clipping detection
    let sumSquares = 0;
    let peak = 0;
    let clipCount = 0;
    let silentFrameCount = 0;
    const frameSize = Math.floor(sampleRate * 0.02); // 20ms frames
    const frameCount = Math.floor(samples.length / frameSize);

    for (let i = 0; i < samples.length; i++) {
      const val = Math.abs(samples[i]);
      sumSquares += val * val;
      if (val > peak) peak = val;
      if (val >= 0.985) {
        clipCount++;
      }
    }

    for (let f = 0; f < frameCount; f++) {
      let frameEnergy = 0;
      const start = f * frameSize;
      for (let j = 0; j < frameSize; j++) {
        const v = samples[start + j];
        frameEnergy += v * v;
      }
      const frameRms = Math.sqrt(frameEnergy / frameSize);
      if (frameRms < 0.015) {
        silentFrameCount++;
      }
    }

    const rms = Math.sqrt(sumSquares / Math.max(1, samples.length));
    const rmsLevelDb = rms > 0 ? Math.max(-96, Math.round(20 * Math.log10(rms))) : -96;
    const clippingDetected = clipCount > 15;
    const silenceRatio = frameCount > 0 ? silentFrameCount / frameCount : 1;

    // Check RMS bounds
    if (rmsLevelDb < -46) {
      failureReasons.push('Audio input level too low (whisper or distant mic detected)');
    } else if (clippingDetected) {
      failureReasons.push('Severe audio clipping detected. Speak slightly further from microphone.');
    }

    // Check silence ratio
    if (silenceRatio > 0.65) {
      failureReasons.push('Excessive silence detected in recording (>65% silent)');
    }

    const speechDetected = rmsLevelDb >= -45 && silenceRatio <= 0.70;
    if (!speechDetected) {
      failureReasons.push('Voice Activity Detector (VAD) could not confirm clear vocal formant speech');
    }

    // Estimate fundamental pitch multiplier based on zero-crossing rate
    let zeroCrossings = 0;
    for (let i = 1; i < samples.length; i++) {
      if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
        zeroCrossings++;
      }
    }
    const zcr = zeroCrossings / samples.length;
    // Map normal human speech ZCR (0.05 - 0.25) to pitch 0.85 - 1.25
    const pitchMultiplier = Number(Math.min(1.35, Math.max(0.75, 0.75 + zcr * 2.5)).toFixed(2));
    const rateMultiplier = durationMs < 2500 ? 1.05 : durationMs > 5000 ? 0.95 : 1.0;

    return {
      isValid: failureReasons.length === 0,
      sampleRate,
      durationMs,
      rmsLevelDb,
      clippingDetected,
      silenceRatio: Number(silenceRatio.toFixed(2)),
      speechDetected,
      pitchMultiplier,
      rateMultiplier,
      failureReasons
    };
  }

  static generateSimulatedPcm(durationSec: number = 3.2, simulateSubham: boolean = true): Float32Array {
    const sampleRate = 16000;
    const length = Math.floor(sampleRate * durationSec);
    const buffer = new Float32Array(length);

    // Subham fundamental ~ 125 Hz (male adult range) with harmonics
    const f0 = simulateSubham ? 128 : 225; // 225 for female/imposter voice
    const f1 = simulateSubham ? 500 : 750;
    const f2 = simulateSubham ? 1600 : 2100;

    for (let i = 0; i < length; i++) {
      const t = i / sampleRate;
      // Speech envelope (ramping up and down)
      const envelope = Math.sin((Math.PI * i) / length);
      // Voice formant synthesis
      const vocalFormant =
        0.55 * Math.sin(2 * Math.PI * f0 * t) +
        0.25 * Math.sin(2 * Math.PI * f1 * t) +
        0.15 * Math.sin(2 * Math.PI * f2 * t);
      // Small natural micro-jitter
      const microJitter = (Math.random() - 0.5) * 0.05;
      buffer[i] = (vocalFormant + microJitter) * envelope * 0.7;
    }

    return buffer;
  }
}
