import React, { useState } from 'react';
import {
  Shield,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Mic,
  Bell,
  Users,
  Phone,
  MessageSquare,
  Calendar,
  AlarmClock,
  MapPin,
  Eye,
  Sliders,
  RotateCcw
} from 'lucide-react';
import { PermissionManager, PermissionRecord } from '../core/permissionManager';

export const PermissionCenterScreen: React.FC = () => {
  const [permissions, setPermissions] = useState<PermissionRecord[]>(
    PermissionManager.getAllPermissions()
  );

  const handleToggle = (id: string) => {
    const updated = PermissionManager.togglePermission(id);
    setPermissions(updated);
  };

  const handleReset = () => {
    if (confirm('Reset all permission states to default safe Android configuration?')) {
      const reset = PermissionManager.resetAll();
      setPermissions(reset);
    }
  };

  const grantedCount = permissions.filter((p) => p.isGranted).length;
  const totalCount = permissions.length;

  const getIcon = (id: string) => {
    switch (id) {
      case 'mic': return <Mic className="w-5 h-5" />;
      case 'notif': return <Bell className="w-5 h-5" />;
      case 'contacts': return <Users className="w-5 h-5" />;
      case 'phone': return <Phone className="w-5 h-5" />;
      case 'sms': return <MessageSquare className="w-5 h-5" />;
      case 'calendar': return <Calendar className="w-5 h-5" />;
      case 'alarm': return <AlarmClock className="w-5 h-5" />;
      case 'location': return <MapPin className="w-5 h-5" />;
      case 'notif_listener': return <Eye className="w-5 h-5" />;
      default: return <Sliders className="w-5 h-5" />;
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-blue-950/40 via-slate-900 to-slate-900 border border-blue-500/30 rounded-2xl p-5 shadow-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-blue-500/10 text-blue-400 rounded-xl border border-blue-500/20">
            <Shield className="w-8 h-8" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-white">Central Permission Center</h2>
              <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-blue-500/20 text-blue-300">
                ANDROID 16 COMPLIANT
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Progressive permission handling: Anin operates gracefully even if individual permissions are denied.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="text-right">
            <span className="text-xs text-slate-400 block font-mono">Status:</span>
            <span className="text-sm font-bold text-emerald-400 font-mono">
              {grantedCount} / {totalCount} Granted
            </span>
          </div>

          <button
            onClick={handleReset}
            className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg transition"
            title="Reset to default permissions"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Permissions Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {permissions.map((perm) => (
          <div
            key={perm.id}
            className={`p-4 rounded-xl border transition space-y-3 ${
              perm.isGranted
                ? 'bg-slate-900/90 border-slate-800'
                : 'bg-slate-950/90 border-amber-900/30'
            }`}
          >
            {/* Top row */}
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-center gap-2.5">
                <div
                  className={`p-2 rounded-lg ${
                    perm.isGranted
                      ? 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20'
                      : 'bg-slate-800 text-slate-500'
                  }`}
                >
                  {getIcon(perm.id)}
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-white">{perm.name}</h3>
                  <span className="text-[10px] font-mono text-slate-400 block">
                    {perm.androidPermission}
                  </span>
                </div>
              </div>

              <button
                onClick={() => handleToggle(perm.id)}
                className={`px-2.5 py-1 rounded-lg text-xs font-semibold flex items-center gap-1 transition ${
                  perm.isGranted
                    ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 hover:bg-emerald-500/30'
                    : 'bg-slate-800 text-slate-400 border border-slate-700 hover:bg-slate-700'
                }`}
              >
                {perm.isGranted ? (
                  <>
                    <CheckCircle2 className="w-3.5 h-3.5" /> Granted
                  </>
                ) : (
                  <>
                    <XCircle className="w-3.5 h-3.5" /> Denied
                  </>
                )}
              </button>
            </div>

            {/* Feature & Why required */}
            <div className="space-y-1 text-xs">
              <p className="text-slate-300 text-[11px] leading-relaxed">
                <strong className="text-indigo-300">Feature:</strong> {perm.feature}
              </p>
              <p className="text-slate-400 text-[11px] leading-relaxed">
                <strong className="text-slate-300">Why required:</strong> {perm.whyRequired}
              </p>
            </div>

            {/* Denial consequence banner */}
            <div
              className={`p-2.5 rounded-lg text-[11px] flex items-start gap-2 ${
                perm.isGranted
                  ? 'bg-slate-950/60 text-slate-400 border border-slate-800/80'
                  : 'bg-amber-950/30 text-amber-300 border border-amber-900/50'
              }`}
            >
              <AlertCircle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <div>
                <span className="font-semibold block text-[10px] uppercase tracking-wide">
                  {perm.isGranted ? 'If Denied in Settings:' : 'Current Denial Consequence:'}
                </span>
                <span>{perm.denialBehavior}</span>
              </div>
            </div>

            {/* Footer action */}
            <div className="pt-1 flex items-center justify-between text-[10px] text-slate-500 font-mono">
              <span>{perm.isSpecialAccess ? 'Special System Access' : perm.isRuntime ? 'Runtime Permission' : 'Manifest Granted'}</span>
              <span className="text-indigo-400 truncate max-w-[200px]">{perm.settingsAction}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
