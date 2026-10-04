import React, { useState } from 'react';
import {
  Mic,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Volume2,
  X,
  ArrowRight,
  ArrowLeft,
  Lock,
  Sparkles
} from 'lucide-react';
import { LanguageCode, OutputVoiceEnrollmentState, VOICE_LANGUAGES } from '../types';
import { AudioQualityValidator } from '../core/audioValidator';
import { LocalStorageManager } from '../core/storage';
import { AudioPlaybackManager } from '../core/audioPlayback';

interface VoiceEnrollmentWizardProps {
  isOpen: boolean;
  onClose: () => void;
  onProfileCreated: () => void;
}

export const VoiceEnrollmentWizard: React.FC<VoiceEnrollmentWizardProps> = ({
  isOpen,
  onClose,
  onProfileCreated
}) => {
  const [state, setState] = useState<OutputVoiceEnrollmentState>({
    currentStep: 1,
    voiceName: '',
    description: '',
    selectedLanguage: 'en',
    recordedSamples: [],
    validationPassed: false,
    consentGiven: false
  });

  const [pitch, setPitch] = useState<number>(1.1);
  const [rate, setRate] = useState<number>(1.0);
  const [volume, setVolume] = useState<number>(1.0);
  const [isRecording, setIsRecording] = useState<boolean>(false);
  const [activeSentenceIndex, setActiveSentenceIndex] = useState<number>(0);

  if (!isOpen) return null;

  const sampleSentences: { lang: LanguageCode; text: string }[] = [
    { lang: 'en', text: 'Hello Subham, I am your customized output voice for Anin.' },
    { lang: 'en', text: 'I will speak your responses clearly and with natural cadence.' },
    { lang: 'bn', text: 'নমস্কার শুভম, এটি আপনার কাস্টম আউটপুট ভয়েস প্রোফাইল।' },
    { lang: 'hi', text: 'नमस्ते शुभम, मैं आपकी चुनी हुई नई आउटপুট आवाज़ हूँ।' }
  ];

  const handleSimulateRecording = () => {
    setIsRecording(true);
    setTimeout(() => {
      setIsRecording(false);
      const currentSentence = sampleSentences[activeSentenceIndex];
      const simulatedPcm = AudioQualityValidator.generateSimulatedPcm(3.2, false);
      const quality = AudioQualityValidator.validateAudioSample(simulatedPcm, 16000);

      const newSample = {
        sentenceIndex: activeSentenceIndex,
        sentenceText: currentSentence.text,
        durationMs: quality.durationMs,
        quality
      };

      const updated = [...state.recordedSamples, newSample];
      setState((prev) => ({
        ...prev,
        recordedSamples: updated,
        validationPassed: updated.length >= 2 && updated.every((s) => s.quality.isValid)
      }));

      if (activeSentenceIndex < sampleSentences.length - 1) {
        setActiveSentenceIndex((prev) => prev + 1);
      }
    }, 1200);
  };

  const handleTestPreview = () => {
    const text =
      state.selectedLanguage === 'bn'
        ? 'শুভম, এটি আপনার নতুন আউটপুট ভয়েসের অডিও প্রিভিউ।'
        : state.selectedLanguage === 'hi'
        ? 'शुभम, यह आपकी नई आउटपुट आवाज़ का परीक्षण है।'
        : 'Subham, this is an audio preview of your customized voice synthesis model.';
    AudioPlaybackManager.speak(text, state.selectedLanguage);
  };

  const handleSaveProfile = () => {
    if (!state.voiceName.trim()) return;

    LocalStorageManager.addOutputVoiceProfile({
      id: 'custom_voice_' + Date.now(),
      profileName: state.voiceName.toLowerCase().replace(/\s+/g, '_'),
      displayName: state.voiceName,
      sourceType: 'CUSTOM_USER_CLONED' as any,
      modelType: 'Neural Acoustic Formant v3.2',
      preferredPitch: pitch,
      preferredRate: rate,
      volume,
      processingMode: 'LOCAL_OFFLINE' as any,
      isActive: true,
      createdAt: Date.now(),
      description: state.description || 'Custom enrolled output voice for Anin.',
      supportedLanguages: ['en', 'bn', 'hi'],
      isAuthorizedSpeaker: false // STRICT SECURITY RULE: ALWAYS FALSE!
    });

    onProfileCreated();
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto flex flex-col shadow-2xl">
        {/* Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-indigo-500/20 text-indigo-400 rounded-lg">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-white">Output Voice Studio Wizard</h2>
              <p className="text-xs text-slate-400">Step {state.currentStep} of 5 — System B Profile</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Step Progress Bar */}
        <div className="w-full bg-slate-800 h-1.5">
          <div
            className="bg-indigo-500 h-1.5 transition-all duration-300"
            style={{ width: `${(state.currentStep / 5) * 100}%` }}
          />
        </div>

        {/* Body Content */}
        <div className="p-6 space-y-6 flex-1">
          {/* STEP 1: Security Boundary */}
          {state.currentStep === 1 && (
            <div className="space-y-4">
              <div className="p-4 bg-amber-500/10 border border-amber-500/30 rounded-xl flex items-start gap-3">
                <AlertTriangle className="w-6 h-6 text-amber-400 flex-shrink-0 mt-0.5" />
                <div className="text-sm">
                  <h4 className="font-semibold text-amber-200">System Isolation & Security Notice</h4>
                  <p className="text-amber-300/80 text-xs mt-1 leading-relaxed">
                    This wizard enrolls an <strong>OUTPUT VOICE (System B)</strong>. Anin will use this voice to speak responses to Subham.
                    This voice <strong>CANNOT</strong> and <strong>WILL NEVER</strong> be authorized to unlock the assistant or pass speaker verification.
                  </p>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  Voice Profile Name *
                </label>
                <input
                  type="text"
                  placeholder="e.g. Anin Gentle Bengali Tone"
                  value={state.voiceName}
                  onChange={(e) => setState({ ...state, voiceName: e.target.value })}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  Voice Description
                </label>
                <input
                  type="text"
                  placeholder="e.g. Natural cadence, warm presence for daily briefings"
                  value={state.description}
                  onChange={(e) => setState({ ...state, description: e.target.value })}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  Primary Dialect / Target Language
                </label>
                <select
                  value={state.selectedLanguage}
                  onChange={(e) =>
                    setState({ ...state, selectedLanguage: e.target.value as LanguageCode })
                  }
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-indigo-500"
                >
                  {Object.values(VOICE_LANGUAGES).map((l) => (
                    <option key={l.code} value={l.code}>
                      {l.displayName}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {/* STEP 2: Consent & Attribution */}
          {state.currentStep === 2 && (
            <div className="space-y-4">
              <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-3">
                <div className="flex items-center gap-2 text-indigo-400">
                  <Lock className="w-5 h-5" />
                  <h4 className="text-sm font-semibold">Consent & Biometric Attribution Declaration</h4>
                </div>
                <p className="text-xs text-slate-300 leading-relaxed">
                  By creating this voice profile, you certify that:
                </p>
                <ul className="text-xs text-slate-400 space-y-2 list-disc pl-4">
                  <li>The audio samples are provided with consent from the speaker.</li>
                  <li>This output model is executed entirely locally on your device hardware.</li>
                  <li>No raw PCM samples will be shared to external clouds without explicit permission.</li>
                  <li>The generated acoustic model will remain in System B only.</li>
                </ul>

                <label className="flex items-center gap-3 pt-3 border-t border-slate-800 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={state.consentGiven}
                    onChange={(e) => setState({ ...state, consentGiven: e.target.checked })}
                    className="w-4 h-4 rounded text-indigo-600 focus:ring-indigo-500 bg-slate-900 border-slate-700"
                  />
                  <span className="text-xs font-medium text-slate-200">
                    I acknowledge and agree to these security and privacy boundaries
                  </span>
                </label>
              </div>
            </div>
          )}

          {/* STEP 3: Voice Sample Capture */}
          {state.currentStep === 3 && (
            <div className="space-y-4">
              <div className="p-4 bg-indigo-500/10 border border-indigo-500/20 rounded-xl text-center">
                <span className="text-xs font-semibold uppercase tracking-wider text-indigo-400">
                  Target Sample {activeSentenceIndex + 1} of {sampleSentences.length}
                </span>
                <p className="text-base font-medium text-white mt-2">
                  &ldquo;{sampleSentences[activeSentenceIndex].text}&rdquo;
                </p>
                <span className="text-xs text-slate-400 mt-1 inline-block">
                  Language: {VOICE_LANGUAGES[sampleSentences[activeSentenceIndex].lang].displayName}
                </span>
              </div>

              <div className="flex flex-col items-center justify-center p-6 bg-slate-950 border border-slate-800 rounded-xl">
                <button
                  onClick={handleSimulateRecording}
                  disabled={isRecording}
                  className={`w-16 h-16 rounded-full flex items-center justify-center transition-all ${
                    isRecording
                      ? 'bg-rose-600 animate-pulse text-white'
                      : 'bg-indigo-600 hover:bg-indigo-500 text-white'
                  }`}
                >
                  <Mic className="w-7 h-7" />
                </button>
                <span className="text-xs text-slate-400 mt-3">
                  {isRecording ? 'Capturing audio and extracting spectral envelope...' : 'Click to record sample'}
                </span>
              </div>

              <div className="space-y-2">
                <h5 className="text-xs font-semibold text-slate-300">Recorded Samples ({state.recordedSamples.length})</h5>
                {state.recordedSamples.map((s, idx) => (
                  <div
                    key={idx}
                    className="p-3 bg-slate-950 border border-slate-800 rounded-lg flex items-center justify-between text-xs"
                  >
                    <div className="truncate max-w-[280px]">
                      <span className="font-semibold text-indigo-400">#{idx + 1}: </span>
                      <span className="text-slate-300">{s.sentenceText}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-slate-400">{s.durationMs}ms</span>
                      {s.quality.isValid ? (
                        <CheckCircle className="w-4 h-4 text-emerald-400" />
                      ) : (
                        <XCircle className="w-4 h-4 text-rose-400" />
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* STEP 4: Validation */}
          {state.currentStep === 4 && (
            <div className="space-y-4">
              <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-3">
                <h4 className="text-sm font-semibold text-white">Audio Quality & Formant Diagnostics</h4>
                {state.recordedSamples.map((s, idx) => (
                  <div key={idx} className="p-3 bg-slate-900 rounded-lg space-y-2 text-xs">
                    <div className="flex justify-between items-center">
                      <span className="font-medium text-slate-200">Sample #{idx + 1}</span>
                      <span className={s.quality.isValid ? 'text-emerald-400' : 'text-rose-400'}>
                        {s.quality.isValid ? 'Validated' : 'Quality Issues'}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-slate-400">
                      <div>RMS Level: <span className="text-slate-200">{s.quality.rmsLevelDb} dBFS</span></div>
                      <div>Silence Ratio: <span className="text-slate-200">{Math.round(s.quality.silenceRatio * 100)}%</span></div>
                      <div>Clipping: <span className="text-slate-200">{s.quality.clippingDetected ? 'Yes' : 'No'}</span></div>
                      <div>Vocal Formants: <span className="text-slate-200">{s.quality.speechDetected ? 'Clear' : 'Weak'}</span></div>
                    </div>
                  </div>
                ))}

                {state.validationPassed ? (
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-lg text-xs text-emerald-300 flex items-center gap-2">
                    <CheckCircle className="w-4 h-4 flex-shrink-0" />
                    <span>All biometric quality tests passed! Ready to configure synthesis parameters.</span>
                  </div>
                ) : (
                  <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg text-xs text-amber-300 flex items-center gap-2">
                    <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                    <span>Please capture at least 2 valid speech samples to proceed.</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* STEP 5: Synthesis Parameters & Finalize */}
          {state.currentStep === 5 && (
            <div className="space-y-4">
              <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-4">
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-slate-300 font-medium">Pitch Multiplier: {pitch.toFixed(2)}x</span>
                    <span className="text-slate-400">Female Formant Range</span>
                  </div>
                  <input
                    type="range"
                    min="0.6"
                    max="1.8"
                    step="0.05"
                    value={pitch}
                    onChange={(e) => setPitch(parseFloat(e.target.value))}
                    className="w-full accent-indigo-500 cursor-pointer"
                  />
                </div>

                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-slate-300 font-medium">Speaking Cadence / Rate: {rate.toFixed(2)}x</span>
                    <span className="text-slate-400">Pace Control</span>
                  </div>
                  <input
                    type="range"
                    min="0.7"
                    max="1.5"
                    step="0.05"
                    value={rate}
                    onChange={(e) => setRate(parseFloat(e.target.value))}
                    className="w-full accent-indigo-500 cursor-pointer"
                  />
                </div>

                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-slate-300 font-medium">Output Volume: {Math.round(volume * 100)}%</span>
                  </div>
                  <input
                    type="range"
                    min="0.2"
                    max="1.0"
                    step="0.05"
                    value={volume}
                    onChange={(e) => setVolume(parseFloat(e.target.value))}
                    className="w-full accent-indigo-500 cursor-pointer"
                  />
                </div>

                <div className="pt-2">
                  <button
                    onClick={handleTestPreview}
                    className="w-full py-2.5 bg-slate-800 hover:bg-slate-700 text-indigo-300 text-xs font-medium rounded-lg flex items-center justify-center gap-2 transition"
                  >
                    <Volume2 className="w-4 h-4" />
                    <span>Test Spoken Audio Preview</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer Navigation */}
        <div className="p-4 border-t border-slate-800 flex items-center justify-between bg-slate-950/50">
          <button
            onClick={() => setState((prev) => ({ ...prev, currentStep: Math.max(1, prev.currentStep - 1) }))}
            disabled={state.currentStep === 1}
            className="px-4 py-2 text-xs text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed flex items-center gap-2 transition"
          >
            <ArrowLeft className="w-4 h-4" /> Back
          </button>

          {state.currentStep < 5 ? (
            <button
              onClick={() => setState((prev) => ({ ...prev, currentStep: prev.currentStep + 1 }))}
              disabled={
                (state.currentStep === 1 && !state.voiceName.trim()) ||
                (state.currentStep === 2 && !state.consentGiven) ||
                (state.currentStep === 3 && state.recordedSamples.length === 0) ||
                (state.currentStep === 4 && !state.validationPassed)
              }
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-medium rounded-lg flex items-center gap-2 transition"
            >
              Continue <ArrowRight className="w-4 h-4" />
            </button>
          ) : (
            <button
              onClick={handleSaveProfile}
              className="px-5 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold rounded-lg flex items-center gap-2 transition shadow-lg shadow-emerald-900/30"
            >
              <CheckCircle className="w-4 h-4" /> Save Output Voice Profile
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
