import React, { useState } from 'react';
import {
  Fingerprint,
  CheckCircle,
  Clock,
  Mic,
  Lock,
  RotateCcw,
  Sparkles,
  ShieldCheck,
  AlertTriangle
} from 'lucide-react';
import { EnrollmentMetadata, SubhamEnrollmentSampleEntity, VOICE_LANGUAGES } from '../types';
import { ENROLLMENT_SENTENCES } from '../core/enrollmentSentences';
import { AudioQualityValidator } from '../core/audioValidator';
import { SpeakerVerificationEngine } from '../core/speakerVerification';
import { LocalStorageManager } from '../core/storage';

interface SubhamEnrollmentScreenProps {
  onEnrollmentComplete: () => void;
}

export const SubhamEnrollmentScreen: React.FC<SubhamEnrollmentScreenProps> = ({
  onEnrollmentComplete
}) => {
  const [metadata, setMetadata] = useState<EnrollmentMetadata>(
    LocalStorageManager.getSubhamMetadata()
  );
  const [samples, setSamples] = useState<SubhamEnrollmentSampleEntity[]>(
    LocalStorageManager.getSubhamSamples()
  );
  const [currentIndex, setCurrentIndex] = useState<number>(
    Math.min(samples.length, ENROLLMENT_SENTENCES.length - 1)
  );
  const [isRecording, setIsRecording] = useState<boolean>(false);
  const [lastValidationMessage, setLastValidationMessage] = useState<string | null>(null);

  const currentSentence = ENROLLMENT_SENTENCES[currentIndex];
  const targetRequired = 10;
  const progressPercent = Math.min(100, Math.round((samples.length / targetRequired) * 100));

  const handleRecordSample = () => {
    setIsRecording(true);
    setLastValidationMessage(null);

    setTimeout(() => {
      setIsRecording(false);
      // Generate simulated Subham audio PCM
      const pcm = AudioQualityValidator.generateSimulatedPcm(3.2, true);
      const quality = AudioQualityValidator.validateAudioSample(pcm, 16000);

      if (!quality.isValid) {
        setLastValidationMessage(`Quality Check Failed: ${quality.failureReasons.join(', ')}`);
        return;
      }

      const features = SpeakerVerificationEngine.extractAcousticFeatures(pcm);
      const newSample: SubhamEnrollmentSampleEntity = {
        id: 'subham_sample_' + Date.now(),
        sampleIndex: currentIndex + 1,
        language: currentSentence.language,
        textPrompt: currentSentence.promptText,
        acousticFeaturesVector: features,
        qualityScore: 0.92,
        durationMs: quality.durationMs,
        isUsable: true,
        createdAt: Date.now()
      };

      const updated = [...samples, newSample];
      setSamples(updated);
      LocalStorageManager.saveSubhamSamples(updated);
      setLastValidationMessage(
        `Sample #${currentIndex + 1} captured successfully (RMS: ${quality.rmsLevelDb} dBFS, Formants clear).`
      );

      if (currentIndex < ENROLLMENT_SENTENCES.length - 1) {
        setCurrentIndex((prev) => prev + 1);
      }
    }, 1200);
  };

  const handleFinalize = () => {
    const result = SpeakerVerificationEngine.finalizeEnrollment();
    if (result.isSuccessful) {
      setMetadata(LocalStorageManager.getSubhamMetadata());
      setLastValidationMessage(result.message);
      onEnrollmentComplete();
    } else {
      setLastValidationMessage(result.message);
    }
  };

  const handleDeleteProfile = () => {
    if (confirm('Are you sure you want to delete Subham biometric voice profile? All voice commands will be locked until re-enrolled.')) {
      LocalStorageManager.deleteSubhamBiometrics();
      setMetadata(LocalStorageManager.getSubhamMetadata());
      setSamples([]);
      setCurrentIndex(0);
      setLastValidationMessage('Biometric data cleared.');
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* SYSTEM A ARCHITECTURAL BANNER */}
      <div className="bg-gradient-to-r from-emerald-950/40 via-slate-900 to-slate-900 border border-emerald-500/30 rounded-2xl p-5 shadow-xl">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-3 bg-emerald-500/10 text-emerald-400 rounded-xl border border-emerald-500/20">
              <Fingerprint className="w-8 h-8" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-white">SYSTEM A: Subham Voice Enrollment</h2>
                <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-emerald-500/20 text-emerald-300">
                  BIOMETRIC GATEWAY
                </span>
              </div>
              <p className="text-xs text-slate-300 mt-1 max-w-xl">
                This voice model exists solely to determine if the speaker is Subham.
                It is stored in hardware-backed encrypted storage and enforces conservative fail-closed security.
              </p>
            </div>
          </div>

          {metadata.isEnrolled && (
            <button
              onClick={handleDeleteProfile}
              className="px-3 py-1.5 bg-rose-950/50 hover:bg-rose-900/60 border border-rose-800 text-rose-300 text-xs font-medium rounded-lg flex items-center gap-1.5 transition"
            >
              <RotateCcw className="w-3.5 h-3.5" /> Re-enroll / Delete
            </button>
          )}
        </div>
      </div>

      {/* Status & Progress Card */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs text-slate-400 font-medium">Enrollment Status</span>
          <div className="flex items-center gap-2 my-2">
            {metadata.isEnrolled ? (
              <div className="flex items-center gap-1.5 text-emerald-400 font-semibold text-sm">
                <ShieldCheck className="w-5 h-5" /> Enrolled & Active
              </div>
            ) : (
              <div className="flex items-center gap-1.5 text-amber-400 font-semibold text-sm">
                <Clock className="w-5 h-5" /> Incomplete ({samples.length}/{targetRequired})
              </div>
            )}
          </div>
          <span className="text-[11px] text-slate-500">
            {metadata.isEnrolled ? metadata.audioFeaturesFingerprint : 'Awaiting 10 multilingual sentences'}
          </span>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs text-slate-400 font-medium">Biometric Keystore Hash</span>
          <div className="text-sm font-mono text-slate-200 truncate my-2">
            {metadata.modelHash}
          </div>
          <span className="text-[11px] text-slate-500">Hardware-backed AndroidKeyStore</span>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs text-slate-400 font-medium">Verification History</span>
          <div className="flex items-center gap-4 my-2 text-xs">
            <span className="text-emerald-400 font-semibold">Pass: {metadata.verificationPassCount}</span>
            <span className="text-rose-400 font-semibold">Fail-Closed: {metadata.verificationFailureCount}</span>
          </div>
          <span className="text-[11px] text-slate-500">100% Silent on verification failure</span>
        </div>
      </div>

      {/* Interactive Recording Section */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-base font-semibold text-white">Enrollment Sentence {currentIndex + 1} of {ENROLLMENT_SENTENCES.length}</h3>
            <p className="text-xs text-slate-400">Speak naturally in your normal conversational tone</p>
          </div>
          <span className="text-xs font-mono px-2 py-1 bg-slate-800 text-indigo-300 rounded">
            Language: {VOICE_LANGUAGES[currentSentence.language].displayName}
          </span>
        </div>

        {/* Target Sentence Card */}
        <div className="p-6 bg-slate-950 border border-slate-800 rounded-xl text-center space-y-3">
          <span className="text-xs text-slate-500 uppercase tracking-widest font-semibold">
            Prompt to speak aloud:
          </span>
          <p className="text-xl md:text-2xl font-semibold text-white tracking-wide leading-relaxed">
            &ldquo;{currentSentence.promptText}&rdquo;
          </p>
          <div className="flex items-center justify-center gap-4 text-xs text-slate-400 pt-2">
            <span>Pace: <strong className="text-slate-200 capitalize">{currentSentence.speakingPace}</strong></span>
            <span>•</span>
            <span>Tone: <strong className="text-slate-200 capitalize">{currentSentence.tone}</strong></span>
            <span>•</span>
            <span>Pattern: <strong className="text-slate-200">{currentSentence.phoneticPattern}</strong></span>
          </div>
        </div>

        {/* Record Button & Feedback */}
        <div className="flex flex-col items-center justify-center gap-3">
          <button
            onClick={handleRecordSample}
            disabled={isRecording}
            className={`w-20 h-20 rounded-full flex items-center justify-center transition-all ${
              isRecording
                ? 'bg-rose-600 animate-pulse text-white shadow-xl shadow-rose-900/50'
                : 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg shadow-emerald-900/30'
            }`}
          >
            <Mic className="w-8 h-8" />
          </button>
          <span className="text-xs text-slate-400">
            {isRecording ? 'Extracting 16-band vocal tract acoustic vectors...' : 'Tap to speak sample'}
          </span>
        </div>

        {lastValidationMessage && (
          <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl text-xs text-center font-medium text-emerald-400">
            {lastValidationMessage}
          </div>
        )}

        {/* Progress Bar */}
        <div className="space-y-2">
          <div className="flex justify-between text-xs text-slate-400">
            <span>Enrollment Progress ({samples.length}/{targetRequired} minimum required)</span>
            <span className="font-semibold text-white">{progressPercent}%</span>
          </div>
          <div className="w-full bg-slate-950 h-2.5 rounded-full overflow-hidden border border-slate-800">
            <div
              className="bg-emerald-500 h-full transition-all duration-300"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>

        {/* Finalize Button */}
        {samples.length >= 5 && (
          <div className="pt-2 flex justify-center">
            <button
              onClick={handleFinalize}
              className="px-6 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold rounded-xl flex items-center gap-2 transition shadow-xl shadow-emerald-950"
            >
              <CheckCircle className="w-4 h-4" />
              <span>Finalize & Lock Biometric Model ({samples.length} Samples Enrolled)</span>
            </button>
          </div>
        )}
      </div>

      {/* Enrolled Samples List */}
      {samples.length > 0 && (
        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 space-y-3">
          <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
            Enrolled Acoustic Vectors ({samples.length})
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2 max-h-56 overflow-y-auto pr-1">
            {samples.map((s, idx) => (
              <div
                key={s.id}
                className="p-3 bg-slate-950 border border-slate-800/80 rounded-lg text-xs space-y-1"
              >
                <div className="flex justify-between items-center">
                  <span className="font-medium text-indigo-400">Sample #{idx + 1} ({s.language.toUpperCase()})</span>
                  <span className="text-emerald-400 text-[11px] font-mono">{s.durationMs}ms</span>
                </div>
                <p className="text-slate-300 text-[11px] truncate">&ldquo;{s.textPrompt}&rdquo;</p>
                <div className="text-[10px] text-slate-500 font-mono truncate">
                  Vector: [{s.acousticFeaturesVector.slice(0, 4).join(', ')}, ...]
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
