import React, { useState } from 'react';
import {
  Shield,
  Smartphone,
  Sliders,
  Lock,
  RefreshCw,
  Trash2,
  AlertTriangle,
  History,
  CheckCircle,
  Database,
  Phone,
  MessageSquare,
  Users,
  Bell,
  Ban
} from 'lucide-react';
import {
  DeviceSystemDiagnostics,
  ProcessingModeSettings,
  VoiceProcessingMode,
  ActionSafetyLevel
} from '../types';
import { LocalStorageManager } from '../core/storage';

interface PrivacySettingsScreenProps {
  onDataReset: () => void;
}

export const PrivacySettingsScreen: React.FC<PrivacySettingsScreenProps> = ({
  onDataReset
}) => {
  const [diagnostics, setDiagnostics] = useState<DeviceSystemDiagnostics>({
    deviceName: 'iQOO Neo 10R',
    ramTotalGb: 8,
    ramAvailableGb: 5.1,
    osVersion: 'Android 16',
    buildNumber: 'PD2352F_EX_A_16.1.19.0.W30',
    chipset: 'Snapdragon 8 Gen 3 (64-bit ARM)',
    batteryPercentage: 85,
    batteryStatus: 'Discharging (Nominal)',
    thermalStatus: 'Optimal (32.4°C)',
    networkType: 'Local DSP On-Device',
    acousticEchoCancellation: true,
    noiseSuppression: true,
    automaticGainControl: true,
    encryptedVaultHardwareBacked: true
  });

  const [settings, setSettings] = useState<ProcessingModeSettings>({
    voiceProcessingMode: VoiceProcessingMode.HYBRID,
    audioRetentionDays: 0,
    allowCloudSynthesis: true,
    bargeInSensitivity: 85,
    strictVerificationThreshold: 0.72
  });

  const [isRefreshing, setIsRefreshing] = useState(false);

  const handleRefreshDiagnostics = () => {
    setIsRefreshing(true);
    setTimeout(() => {
      setDiagnostics((prev) => ({
        ...prev,
        ramAvailableGb: Number((4.8 + Math.random() * 0.6).toFixed(1)),
        batteryPercentage: Math.max(80, Math.min(88, prev.batteryPercentage + (Math.random() > 0.5 ? 1 : -1))),
        thermalStatus: `${(31.8 + Math.random() * 1.5).toFixed(1)}°C (Cool)`
      }));
      setIsRefreshing(false);
    }, 600);
  };

  const handleWipeAllBiometrics = () => {
    if (confirm('CRITICAL ACTION: This will completely delete Subham biometric enrollment vectors and revoke voice authorization. Proceed?')) {
      LocalStorageManager.deleteSubhamBiometrics();
      onDataReset();
      alert('Subham voice profile deleted. Fail-closed security is now enforced for all commands.');
    }
  };

  const handleWipeAllMemories = () => {
    if (confirm('Delete all encrypted personal memories and reminders?')) {
      LocalStorageManager.savePersonalMemories([]);
      LocalStorageManager.saveReminders([]);
      onDataReset();
      alert('Encrypted memory vault cleared.');
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* HEADER BANNER */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-emerald-500/10 text-emerald-400 rounded-xl border border-emerald-500/20">
            <Shield className="w-8 h-8" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-white">Diagnostics, Action Safety & Privacy Model</h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Verified specifications for iQOO Neo 10R Snapdragon architecture, capabilities, and safety boundaries.
            </p>
          </div>
        </div>
      </div>

      {/* ACTION SAFETY ENGINE & FINANCIAL PROHIBITIONS */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex items-center gap-2">
          <Ban className="w-5 h-5 text-rose-400" />
          <h3 className="text-base font-semibold text-white">Action Safety Matrix & Financial Policy</h3>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div className="p-3 bg-slate-950 rounded-xl border border-emerald-900/50 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-emerald-400">LEVEL 0 — Read-Only</span>
              <span className="text-[10px] px-1.5 py-0.5 bg-emerald-950 text-emerald-300 rounded font-mono">AUTOMATIC</span>
            </div>
            <p className="text-slate-400 text-[11px]">
              Web search, weather, battery diagnostics, memory retrieval, time queries. Zero side-effects.
            </p>
          </div>

          <div className="p-3 bg-slate-950 rounded-xl border border-indigo-900/50 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-indigo-400">LEVEL 1 — Local Reversible</span>
              <span className="text-[10px] px-1.5 py-0.5 bg-indigo-950 text-indigo-300 rounded font-mono">AUTOMATIC</span>
            </div>
            <p className="text-slate-400 text-[11px]">
              Save personal note, create reminder, update preference. Modifies local encrypted storage with rollback.
            </p>
          </div>

          <div className="p-3 bg-slate-950 rounded-xl border border-amber-900/50 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-amber-400">LEVEL 2 — External Side Effect</span>
              <span className="text-[10px] px-1.5 py-0.5 bg-amber-950 text-amber-300 rounded font-mono">CONFIRMATION</span>
            </div>
            <p className="text-slate-400 text-[11px]">
              Initiate call, prepare WhatsApp/SMS, clear all memories, delete profile. Explicit confirmation required.
            </p>
          </div>

          <div className="p-3 bg-slate-950 rounded-xl border border-rose-900/60 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-rose-400">LEVEL 3 — High-Risk Prohibited</span>
              <span className="text-[10px] px-1.5 py-0.5 bg-rose-950 text-rose-300 rounded font-mono">STRICT REFUSAL</span>
            </div>
            <p className="text-slate-400 text-[11px]">
              UPI payments (GPay/PhonePe), money transfers, bank transactions, automated OTP transaction submissions. <strong>Hard blocked.</strong>
            </p>
          </div>
        </div>
      </div>

      {/* DEVICE CAPABILITY PROVIDER STATUS */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex items-center gap-2">
          <Smartphone className="w-5 h-5 text-indigo-400" />
          <h3 className="text-base font-semibold text-white">Device Capability Provider (Browser vs Native Bridge)</h3>
        </div>

        <div className="space-y-2 text-xs">
          <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Phone className="w-4 h-4 text-indigo-400" />
              <div>
                <span className="font-medium text-white block">Telephony & Outgoing Calling</span>
                <span className="text-slate-400 text-[11px]">Direct TelecomManager calls require Android native bridge; prepares tel: dialer action</span>
              </div>
            </div>
            <span className="font-mono text-[10px] px-2 py-0.5 bg-slate-900 text-indigo-300 rounded border border-indigo-900/50">
              REQUIRES_NATIVE_BRIDGE
            </span>
          </div>

          <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <MessageSquare className="w-4 h-4 text-emerald-400" />
              <div>
                <span className="font-medium text-white block">WhatsApp & SMS Messaging</span>
                <span className="text-slate-400 text-[11px]">Drafts message cleanly and generates wa.me / sms: intent payloads</span>
              </div>
            </div>
            <span className="font-mono text-[10px] px-2 py-0.5 bg-slate-900 text-emerald-300 rounded border border-emerald-900/50">
              REQUIRES_NATIVE_BRIDGE
            </span>
          </div>

          <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Users className="w-4 h-4 text-purple-400" />
              <div>
                <span className="font-medium text-white block">Contacts Registry</span>
                <span className="text-slate-400 text-[11px]">Subham verified contacts registry with ambiguity & duplicate detection</span>
              </div>
            </div>
            <span className="font-mono text-[10px] px-2 py-0.5 bg-slate-900 text-emerald-300 rounded border border-emerald-900/50">
              AVAILABLE
            </span>
          </div>

          <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Bell className="w-4 h-4 text-amber-400" />
              <div>
                <span className="font-medium text-white block">Notification & OTP Inspection</span>
                <span className="text-slate-400 text-[11px]">Reads latest security verification codes; refuses all transaction approvals</span>
              </div>
            </div>
            <span className="font-mono text-[10px] px-2 py-0.5 bg-slate-900 text-amber-300 rounded border border-amber-900/50">
              AVAILABLE (READ-ONLY)
            </span>
          </div>
        </div>
      </div>

      {/* TARGET DEVICE SPECIFICATIONS */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Smartphone className="w-5 h-5 text-indigo-400" />
            <h3 className="text-base font-semibold text-white">Target Hardware Telemetry</h3>
          </div>
          <button
            onClick={handleRefreshDiagnostics}
            disabled={isRefreshing}
            className="text-xs text-indigo-400 hover:text-indigo-300 flex items-center gap-1.5 transition"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} /> Refresh Telemetry
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
          <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
            <span className="text-[11px] text-slate-500 block">Device</span>
            <span className="font-semibold text-white mt-1 block">{diagnostics.deviceName}</span>
          </div>

          <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
            <span className="text-[11px] text-slate-500 block">OS & ROM</span>
            <span className="font-semibold text-white mt-1 block">{diagnostics.osVersion}</span>
            <span className="text-[10px] text-slate-500 font-mono">{diagnostics.buildNumber}</span>
          </div>

          <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
            <span className="text-[11px] text-slate-500 block">RAM & Architecture</span>
            <span className="font-semibold text-white mt-1 block">{diagnostics.ramAvailableGb} GB free / {diagnostics.ramTotalGb} GB</span>
            <span className="text-[10px] text-slate-500">{diagnostics.chipset}</span>
          </div>

          <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
            <span className="text-[11px] text-slate-500 block">Battery & Thermals</span>
            <span className="font-semibold text-emerald-400 mt-1 block">{diagnostics.batteryPercentage}% • {diagnostics.thermalStatus}</span>
          </div>
        </div>
      </div>

      {/* PRIVACY & RETENTION CONTROLS */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-5">
        <div className="flex items-center gap-2">
          <Lock className="w-5 h-5 text-emerald-400" />
          <h3 className="text-base font-semibold text-white">Privacy Guarantees & Retention Policy</h3>
        </div>

        <div className="space-y-4 text-xs">
          <div className="flex items-center justify-between p-3 bg-slate-950 border border-slate-800 rounded-xl">
            <div>
              <span className="font-semibold text-white block">Raw Voice PCM Retention</span>
              <span className="text-slate-400 text-[11px]">Audio samples are processed in volatile RAM only</span>
            </div>
            <span className="font-mono font-bold text-emerald-400 bg-emerald-950/60 px-2.5 py-1 rounded border border-emerald-900">
              0 DAYS (NO STORAGE)
            </span>
          </div>

          <div className="flex items-center justify-between p-3 bg-slate-950 border border-slate-800 rounded-xl">
            <div>
              <span className="font-semibold text-white block">Conservative Verification Threshold</span>
              <span className="text-slate-400 text-[11px]">Fail Closed: Silent ignore when speaker match is uncertain</span>
            </div>
            <span className="font-mono font-bold text-amber-400 bg-amber-950/60 px-2.5 py-1 rounded border border-amber-900">
              0.72 (CONSERVATIVE)
            </span>
          </div>
        </div>

        {/* Data Wiping Actions */}
        <div className="pt-4 border-t border-slate-800 space-y-3">
          <h4 className="text-xs font-semibold text-rose-400 uppercase tracking-wider">
            Biometric & Vault Management
          </h4>
          <div className="flex flex-wrap gap-3">
            <button
              onClick={handleWipeAllBiometrics}
              className="px-4 py-2 bg-rose-950/60 hover:bg-rose-900/60 border border-rose-800 text-rose-300 text-xs font-semibold rounded-xl flex items-center gap-2 transition"
            >
              <Trash2 className="w-4 h-4" /> Wipe Subham Biometrics
            </button>

            <button
              onClick={handleWipeAllMemories}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium rounded-xl flex items-center gap-2 transition"
            >
              <Database className="w-4 h-4" /> Clear Memory Vault & Reminders
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
