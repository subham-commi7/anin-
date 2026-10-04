import React, { useState } from 'react';
import {
  Shield,
  ShieldCheck,
  ShieldAlert,
  Fingerprint,
  Trash2,
  CheckCircle,
  HelpCircle,
  Info
} from 'lucide-react';
import { EnrollmentMetadata, VerificationResult } from '../types';
import { LocalStorageManager } from '../core/storage';
import { SpeakerVerificationEngine } from '../core/speakerVerification';
import { AudioQualityValidator } from '../core/audioValidator';

export const AuthenticationVoiceScreen: React.FC = () => {
  const [metadata, setMetadata] = useState<EnrollmentMetadata>(
    LocalStorageManager.getSubhamMetadata()
  );
  const [testResult, setTestResult] = useState<VerificationResult | null>(null);
  const [isVerifying, setIsVerifying] = useState<boolean>(false);
  const [auditLogs, setAuditLogs] = useState(LocalStorageManager.getAuditLogs());

  const handleTestVerification = (simulateSubham: boolean) => {
    setIsVerifying(true);
    setTestResult(null);

    setTimeout(() => {
      setIsVerifying(false);
      const simulatedPcm = AudioQualityValidator.generateSimulatedPcm(3.0, simulateSubham);
      const result = SpeakerVerificationEngine.verifySpeaker(simulatedPcm);
      setTestResult(result);
      setMetadata(LocalStorageManager.getSubhamMetadata());
      setAuditLogs(LocalStorageManager.getAuditLogs());
    }, 900);
  };

  const handleClearLogs = () => {
    LocalStorageManager.clearAuditLogs();
    setAuditLogs([]);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* HEADER BANNER */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-indigo-500/10 text-indigo-400 rounded-xl border border-indigo-500/20">
            <Shield className="w-8 h-8" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-white">System A: Authentication Voice Security Model</h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Speaker verification engine strictly isolated from Anin&rsquo;s response output voices.
            </p>
          </div>
        </div>
      </div>

      {/* TWO SEPARATE VOICE SYSTEMS ARCHITECTURE CARD */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* System A Card */}
        <div className="bg-slate-900/90 border border-emerald-500/30 rounded-xl p-5 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold font-mono text-emerald-400 uppercase tracking-wider">
              System A (Biometric Gate)
            </span>
            <span className="text-[10px] px-2 py-0.5 rounded font-bold bg-emerald-500/20 text-emerald-300">
              AUTHORIZED
            </span>
          </div>
          <h3 className="text-base font-semibold text-white">Subham Authentication Voice</h3>
          <p className="text-xs text-slate-300 leading-relaxed">
            Authenticates speaker identity using high-security 16-band vocal tract analysis.
            If speaker confidence is uncertain or below threshold, the system <strong>fails closed</strong> and remains completely silent.
          </p>
          <div className="pt-2 text-[11px] text-emerald-400/90 font-mono space-y-1">
            <div>✓ Hardware Keystore encryption</div>
            <div>✓ Fail Closed on uncertainty</div>
            <div>✓ Silent ignore on imposter voice</div>
          </div>
        </div>

        {/* System B Card */}
        <div className="bg-slate-900/90 border border-indigo-500/30 rounded-xl p-5 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold font-mono text-indigo-400 uppercase tracking-wider">
              System B (Response Audio)
            </span>
            <span className="text-[10px] px-2 py-0.5 rounded font-bold bg-indigo-500/20 text-indigo-300">
              OUTPUT ONLY
            </span>
          </div>
          <h3 className="text-base font-semibold text-white">Anin Output Voice</h3>
          <p className="text-xs text-slate-300 leading-relaxed">
            Generates natural speech synthesis in Bengali, Hindi, or English.
            <strong>Security Boundary:</strong> Anin&rsquo;s output voice is NEVER granted speaker authorization. Anin cannot authenticate herself.
          </p>
          <div className="pt-2 text-[11px] text-indigo-400/90 font-mono space-y-1">
            <div>✓ Multilingual female tone</div>
            <div>✓ Instant barge-in stop</div>
            <div>✗ Never an authorized speaker</div>
          </div>
        </div>
      </div>

      {/* BIOMETRIC TEST BENCH */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-5">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-base font-semibold text-white">Live Speaker Verification Simulator</h3>
            <p className="text-xs text-slate-400">
              Test speaker verification with Subham vocal patterns vs an imposter voice
            </p>
          </div>
          <span className="text-xs font-mono px-2 py-1 bg-slate-800 text-slate-300 rounded">
            Threshold: 72% (Conservative)
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <button
            onClick={() => handleTestVerification(true)}
            disabled={isVerifying || !metadata.isEnrolled}
            className="p-4 bg-emerald-950/30 hover:bg-emerald-900/40 border border-emerald-500/40 rounded-xl text-left transition disabled:opacity-40"
          >
            <div className="flex items-center gap-2 text-emerald-400 font-semibold text-sm mb-1">
              <ShieldCheck className="w-5 h-5" />
              <span>Simulate Subham Speaking</span>
            </div>
            <p className="text-xs text-slate-400">
              Simulates speech matching Subham&rsquo;s enrolled vocal tract vectors. Should pass verification.
            </p>
          </button>

          <button
            onClick={() => handleTestVerification(false)}
            disabled={isVerifying || !metadata.isEnrolled}
            className="p-4 bg-rose-950/30 hover:bg-rose-900/40 border border-rose-500/40 rounded-xl text-left transition disabled:opacity-40"
          >
            <div className="flex items-center gap-2 text-rose-400 font-semibold text-sm mb-1">
              <ShieldAlert className="w-5 h-5" />
              <span>Simulate Imposter / Stranger</span>
            </div>
            <p className="text-xs text-slate-400">
              Simulates foreign formant pattern. Must fail closed and trigger 100% silent rejection.
            </p>
          </button>
        </div>

        {/* Verification Result Output */}
        {testResult && (
          <div
            className={`p-4 rounded-xl border text-xs space-y-2 ${
              testResult.isVerified
                ? 'bg-emerald-950/40 border-emerald-500/50 text-emerald-200'
                : 'bg-rose-950/40 border-rose-500/50 text-rose-200'
            }`}
          >
            <div className="flex items-center justify-between font-semibold text-sm">
              <span className="flex items-center gap-2">
                {testResult.isVerified ? (
                  <CheckCircle className="w-5 h-5 text-emerald-400" />
                ) : (
                  <ShieldAlert className="w-5 h-5 text-rose-400" />
                )}
                Verification: {testResult.isVerified ? 'VERIFIED (Subham)' : 'FAIL CLOSED (Silent Reject)'}
              </span>
              <span className="font-mono">
                Match: {Math.round(testResult.confidenceScore * 100)}% (Threshold: {Math.round(testResult.matchThreshold * 100)}%)
              </span>
            </div>
            <p className="text-slate-300">
              {testResult.isVerified
                ? 'Acoustic similarity vector matched enrolled profile within conservative tolerance. Authorized execution granted.'
                : 'Acoustic mismatch. As specified, Anin remains completely silent to the external speaker.'}
            </p>
          </div>
        )}
      </div>

      {/* SECURITY AUDIT LOGS */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-white">Security Audit Trail (Non-Sensitive)</h3>
            <p className="text-xs text-slate-400">
              Diagnostic verification events. In compliance with security rules, raw audio PCM is never recorded.
            </p>
          </div>
          {auditLogs.length > 0 && (
            <button
              onClick={handleClearLogs}
              className="text-xs text-rose-400 hover:text-rose-300 flex items-center gap-1 hover:underline"
            >
              <Trash2 className="w-3.5 h-3.5" /> Clear Audit Logs
            </button>
          )}
        </div>

        <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
          {auditLogs.length === 0 ? (
            <p className="text-xs text-slate-500 text-center py-4">No audit logs recorded yet.</p>
          ) : (
            auditLogs.map((log) => (
              <div
                key={log.id}
                className="p-3 bg-slate-950 border border-slate-800/80 rounded-xl flex items-center justify-between text-xs font-mono"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`font-semibold ${
                        log.eventType === 'AUTHENTICATION_SUCCESS'
                          ? 'text-emerald-400'
                          : log.eventType === 'AUTHENTICATION_FAILED'
                          ? 'text-rose-400'
                          : 'text-indigo-400'
                      }`}
                    >
                      {log.eventType}
                    </span>
                    <span className="text-slate-400">[{log.speakerIdentified}]</span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5">{log.actionTaken}</p>
                </div>
                <div className="text-right text-[11px] text-slate-500">
                  <div>{new Date(log.timestamp).toLocaleTimeString()}</div>
                  <div>PCM Stored: <span className="text-emerald-400">FALSE</span></div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
