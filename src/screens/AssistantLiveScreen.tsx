import React, { useState, useEffect, useRef } from 'react';
import {
  Mic,
  MicOff,
  Square,
  Send,
  Volume2,
  VolumeX,
  ShieldCheck,
  ShieldAlert,
  Sparkles,
  Ear,
  Globe,
  ExternalLink,
  Lock,
  AlertTriangle,
  Brain,
  PhoneCall,
  MessageSquare
} from 'lucide-react';
import { AssistantInteraction, AudioCaptureMetrics, ActionSafetyLevel, detectLanguage } from '../types';
import { AiOrchestrator } from '../core/aiOrchestrator';
import { AudioPlaybackManager } from '../core/audioPlayback';
import { LocalStorageManager } from '../core/storage';
import { AudioQualityValidator } from '../core/audioValidator';
import { VoiceWaveformVisualizer } from '../components/VoiceWaveformVisualizer';

interface AssistantLiveScreenProps {
  interactions: AssistantInteraction[];
  onNewInteraction: (interaction: AssistantInteraction) => void;
  onNavigateToEnrollment: () => void;
  onNavigateToStudio: () => void;
}

export const AssistantLiveScreen: React.FC<AssistantLiveScreenProps> = ({
  interactions,
  onNewInteraction,
  onNavigateToEnrollment,
  onNavigateToStudio
}) => {
  const [inputText, setInputText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [simulateImposter, setSimulateImposter] = useState(false);
  const [activeProfile, setActiveProfile] = useState(LocalStorageManager.getActiveProfile());
  const [subhamMeta, setSubhamMeta] = useState(LocalStorageManager.getSubhamMetadata());

  // Audio capture telemetry
  const [captureMetrics, setCaptureMetrics] = useState<AudioCaptureMetrics>({
    isCapturing: true,
    currentDbfs: -42,
    speechActivityDetected: false,
    wakeWordDetected: false,
    echoCancellationActive: true,
    noiseSuppressionActive: true,
    automaticGainControlActive: true
  });

  const chatBottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const unsubscribe = AudioPlaybackManager.subscribe((status) => {
      setIsSpeaking(status.isSpeaking);
      if (status.activeProfile) {
        setActiveProfile(status.activeProfile);
      }
    });
    return unsubscribe;
  }, []);

  useEffect(() => {
    if (!captureMetrics.isCapturing) return;
    const interval = setInterval(() => {
      const randomDb = -48 + Math.floor(Math.random() * 18);
      setCaptureMetrics((prev) => ({
        ...prev,
        currentDbfs: randomDb,
        speechActivityDetected: randomDb > -36
      }));
    }, 400);
    return () => clearInterval(interval);
  }, [captureMetrics.isCapturing]);

  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [interactions]);

  const handleSendCommand = async (text: string, forceImposter: boolean = simulateImposter) => {
    if (!text.trim() || isProcessing) return;
    setIsProcessing(true);

    const simulatedPcm = AudioQualityValidator.generateSimulatedPcm(3.0, !forceImposter);

    try {
      const interaction = await AiOrchestrator.processCommand(
        text,
        simulatedPcm,
        forceImposter ? false : undefined
      );
      onNewInteraction(interaction);
      setInputText('');
    } finally {
      setIsProcessing(false);
      setSubhamMeta(LocalStorageManager.getSubhamMetadata());
    }
  };

  const handleEmergencyStop = () => {
    AudioPlaybackManager.emergencyStop();
  };

  const quickCommands = [
    { label: 'Weather (BN)', text: 'আজ কলকাতায় আবহাওয়া কেমন?' },
    { label: 'Latest Sports (EN)', text: 'Who won the latest cricket match?' },
    { label: 'Remember (BN)', text: 'মনে রাখো যে শুভ্রতা আমার ঘনিষ্ঠ বন্ধু।' },
    { label: 'Recall Memory (EN)', text: 'What do you remember about me?' },
    { label: 'Call Contact (EN)', text: 'Call Shubhrata' },
    { label: 'Financial Safety Test', text: 'Transfer 5000 rupees via GPay to Rahul' },
    { label: 'Device Telemetry (HI)', text: 'फोन की बैटरी और तापमान चेक करो।' },
    { label: 'Emergency Halt', text: 'stop' }
  ];

  return (
    <div className="flex flex-col h-[calc(100vh-8rem)] max-w-4xl mx-auto w-full gap-4">
      {/* Top Banner: Active Output Voice & Subham Enrollment status */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {/* Subham Authentication Status Card */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className={`p-2 rounded-lg ${
                subhamMeta.isEnrolled
                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                  : 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
              }`}
            >
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold text-slate-200">System A: Speaker Auth</span>
                <span
                  className={`text-[10px] px-1.5 py-0.5 rounded font-mono font-bold ${
                    subhamMeta.isEnrolled
                      ? 'bg-emerald-500/20 text-emerald-300'
                      : 'bg-amber-500/20 text-amber-300'
                  }`}
                >
                  {subhamMeta.isEnrolled ? 'SUBHAM ENROLLED' : 'NOT ENROLLED'}
                </span>
              </div>
              <p className="text-[11px] text-slate-400">
                {subhamMeta.isEnrolled
                  ? `${subhamMeta.totalSamplesEnrolled} vectors • Fail-closed silent reject`
                  : 'Enrollment needed for voice commands'}
              </p>
            </div>
          </div>
          <button
            onClick={onNavigateToEnrollment}
            className="text-xs text-indigo-400 hover:text-indigo-300 font-medium px-2 py-1 rounded hover:bg-slate-800 transition"
          >
            Manage
          </button>
        </div>

        {/* Output Voice Status Card */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              <Volume2 className="w-5 h-5" />
            </div>
            <div className="truncate">
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold text-slate-200">System B: Output Voice</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded font-mono font-bold bg-indigo-500/20 text-indigo-300">
                  {activeProfile.sourceType === 'BUILT_IN_ANIN' ? 'BUILT-IN' : 'CUSTOM'}
                </span>
              </div>
              <p className="text-[11px] text-slate-400 truncate">
                {activeProfile.displayName} (Pitch {activeProfile.preferredPitch}x)
              </p>
            </div>
          </div>
          <button
            onClick={onNavigateToStudio}
            className="text-xs text-indigo-400 hover:text-indigo-300 font-medium px-2 py-1 rounded hover:bg-slate-800 transition"
          >
            Studio
          </button>
        </div>
      </div>

      {/* Main Live Interaction Stage */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 flex flex-col items-center justify-center relative overflow-hidden shadow-xl">
        <div
          className={`absolute -top-12 -left-12 w-48 h-48 rounded-full blur-3xl pointer-events-none transition-opacity duration-700 ${
            isSpeaking ? 'bg-indigo-500/20 opacity-100' : 'bg-transparent opacity-0'
          }`}
        />

        {/* Status indicator pill */}
        <div className="flex items-center gap-4 mb-2">
          <div className="flex items-center gap-1.5 px-3 py-1 bg-slate-950/80 border border-slate-800 rounded-full text-xs">
            <span
              className={`w-2 h-2 rounded-full ${
                captureMetrics.isCapturing ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'
              }`}
            />
            <span className="text-slate-300 text-[11px] font-medium">
              Wake Word: <strong className="text-indigo-300">&ldquo;Hey Anin&rdquo;</strong> (হে অনিন / हे अनिन)
            </span>
          </div>

          <div className="hidden sm:flex items-center gap-1 text-[11px] text-slate-400 font-mono">
            <span>AEC:</span>
            <span className="text-emerald-400">ON</span>
            <span className="ml-1">NS:</span>
            <span className="text-emerald-400">ON</span>
            <span className="ml-1">dBFS:</span>
            <span className="text-slate-200">{captureMetrics.currentDbfs}</span>
          </div>
        </div>

        {/* Dynamic Waveform Visualizer */}
        <div className="w-full flex items-center justify-center py-2">
          <VoiceWaveformVisualizer
            isActive={isSpeaking || captureMetrics.speechActivityDetected || isProcessing}
            waveColor={isSpeaking ? '#818cf8' : isProcessing ? '#f59e0b' : '#34d399'}
          />
        </div>

        {/* Central Controls: Speaker Toggle & Emergency Barge-in Stop */}
        <div className="flex flex-wrap items-center justify-center gap-3 mt-2">
          <button
            onClick={handleEmergencyStop}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition ${
              isSpeaking
                ? 'bg-rose-600 hover:bg-rose-500 text-white animate-bounce shadow-lg shadow-rose-900/50'
                : 'bg-slate-800 hover:bg-slate-700 text-slate-300'
            }`}
          >
            <Square className="w-3.5 h-3.5 fill-current" />
            <span>Emergency Barge-in Stop</span>
          </button>

          <button
            onClick={() => setSimulateImposter((prev) => !prev)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition border ${
              simulateImposter
                ? 'bg-rose-950/40 border-rose-600/50 text-rose-300'
                : 'bg-emerald-950/40 border-emerald-600/50 text-emerald-300'
            }`}
          >
            {simulateImposter ? <ShieldAlert className="w-3.5 h-3.5" /> : <ShieldCheck className="w-3.5 h-3.5" />}
            <span>Speaker Mode: {simulateImposter ? 'Imposter Voice (Fail Closed)' : 'Subham (Verified)'}</span>
          </button>
        </div>
      </div>

      {/* Quick Command Chips */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
        <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider flex-shrink-0 flex items-center gap-1">
          <Sparkles className="w-3 h-3 text-indigo-400" /> Commands:
        </span>
        {quickCommands.map((cmd, i) => (
          <button
            key={i}
            onClick={() => handleSendCommand(cmd.text)}
            disabled={isProcessing}
            className="flex-shrink-0 px-2.5 py-1 bg-slate-900 hover:bg-slate-800 border border-slate-800 rounded-lg text-xs text-slate-300 hover:text-white transition disabled:opacity-50"
          >
            {cmd.label}
          </button>
        ))}
      </div>

      {/* Conversation Feed */}
      <div className="flex-1 bg-slate-900/50 border border-slate-800 rounded-2xl p-4 overflow-y-auto space-y-3 shadow-inner">
        {interactions.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
            <Ear className="w-10 h-10 text-slate-600 mb-2 stroke-[1.5]" />
            <h4 className="text-sm font-semibold text-slate-400">Anin Step 2 Intelligence Active</h4>
            <p className="text-xs text-slate-500 max-w-sm mt-1">
              Natural conversation in Bengali, Hindi, or English. Online search grounding, encrypted memory vault, and strict financial safety enforcement.
            </p>
          </div>
        ) : (
          interactions.map((msg) => (
            <div
              key={msg.id}
              className={`p-3.5 rounded-xl border text-xs space-y-2 transition ${
                msg.isSilentRejection
                  ? 'bg-rose-950/20 border-rose-900/40 text-slate-400'
                  : msg.safetyLevel === ActionSafetyLevel.LEVEL_3_HIGH_RISK_PROHIBITED
                  ? 'bg-amber-950/20 border-amber-800/40 text-slate-300'
                  : 'bg-slate-900 border-slate-800 text-slate-200'
              }`}
            >
              {/* Query Header */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-indigo-400">&ldquo;{msg.query}&rdquo;</span>
                  <span className="text-[10px] px-1.5 py-0.2 bg-slate-800 text-slate-400 rounded uppercase">
                    {msg.detectedLanguage}
                  </span>
                  {msg.actionClassification && (
                    <span className="text-[10px] px-1.5 py-0.2 bg-indigo-950/60 border border-indigo-800/50 text-indigo-300 rounded font-mono">
                      {msg.actionClassification}
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  {msg.verifiedSubham ? (
                    <span className="flex items-center gap-1 text-[11px] text-emerald-400">
                      <ShieldCheck className="w-3 h-3" /> Subham ({Math.round(msg.confidenceScore * 100)}%)
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-[11px] text-rose-400 font-mono">
                      <ShieldAlert className="w-3 h-3" /> SILENT REJECT
                    </span>
                  )}
                  <span className="text-[10px] text-slate-500">{msg.audioLatencyMs}ms</span>
                </div>
              </div>

              {/* Silent Rejection vs Real Response */}
              {msg.isSilentRejection ? (
                <div className="p-2 bg-slate-950/70 rounded border border-rose-950 text-rose-400 font-mono text-[11px] flex items-center justify-between">
                  <span>{msg.responseText}</span>
                  <span className="text-[10px] text-slate-500">Security Rule: 100% Silence</span>
                </div>
              ) : (
                <div className="p-3 bg-slate-950/90 rounded-lg border border-slate-800 text-slate-200 text-sm leading-relaxed space-y-2">
                  <p>{msg.responseText}</p>

                  {/* Web Search Sources Badges */}
                  {msg.searchSources && msg.searchSources.length > 0 && (
                    <div className="pt-2 border-t border-slate-800 space-y-1.5">
                      <div className="flex items-center gap-1.5 text-xs text-indigo-300 font-semibold">
                        <Globe className="w-3.5 h-3.5 text-indigo-400" />
                        <span>Google Search Grounded Sources:</span>
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {msg.searchSources.map((src, idx) => (
                          <a
                            key={idx}
                            href={src.uri}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 px-2 py-0.5 bg-slate-900 hover:bg-slate-800 border border-slate-700 rounded text-[11px] text-indigo-300 hover:text-white transition"
                          >
                            <span className="truncate max-w-[200px]">{src.title}</span>
                            <ExternalLink className="w-3 h-3 flex-shrink-0" />
                          </a>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Tool Execution Details */}
                  {msg.toolCalls && msg.toolCalls.length > 0 && (
                    <div className="pt-2 border-t border-slate-800 space-y-1 text-xs font-mono">
                      {msg.toolCalls.map((t, idx) => (
                        <div key={idx} className="p-2 bg-slate-900 rounded border border-slate-800 text-slate-300 flex items-center justify-between">
                          <span>Tool: <strong className="text-indigo-400">{t.toolName}</strong></span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-emerald-300">
                            {t.capabilityStatus || 'AVAILABLE'}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Action / Safety Footer */}
                  {msg.actionExecuted && (
                    <div className="pt-1.5 border-t border-slate-800/80 flex items-center justify-between text-[10px] text-slate-400 font-mono">
                      <span>Action: {msg.actionExecuted}</span>
                      <span>Output: {msg.voiceProfileUsed}</span>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))
        )}
        <div ref={chatBottomRef} />
      </div>

      {/* Input Bar */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-2 flex items-center gap-2">
        <button
          onClick={() => handleSendCommand('আজ কলকাতায় আবহাওয়া কেমন?')}
          disabled={isProcessing}
          className="p-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg transition disabled:opacity-50"
          title="Simulate Voice Input with Acoustic Feature Vector"
        >
          <Mic className="w-4 h-4" />
        </button>

        <input
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSendCommand(inputText)}
          placeholder="Speak or type (e.g. 'আজকের খবর কি?', 'Remember Shubhrata is my friend', 'Call Dad')"
          disabled={isProcessing}
          className="flex-1 bg-transparent px-2 text-xs text-white placeholder-slate-500 focus:outline-none"
        />

        <button
          onClick={() => handleSendCommand(inputText)}
          disabled={!inputText.trim() || isProcessing}
          className="p-2.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-white rounded-lg transition"
        >
          <Send className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
