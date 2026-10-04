import React, { useState } from 'react';
import {
  Volume2,
  Sliders,
  Plus,
  Play,
  Square,
  AlertTriangle,
  Clock,
  Trash2,
  Check,
  Sparkles
} from 'lucide-react';
import { LanguageCode, OutputVoiceProfileEntity, VOICE_LANGUAGES } from '../types';
import { LocalStorageManager } from '../core/storage';
import { AudioPlaybackManager } from '../core/audioPlayback';

interface OutputVoiceStudioScreenProps {
  onOpenWizard: () => void;
}

export const OutputVoiceStudioScreen: React.FC<OutputVoiceStudioScreenProps> = ({
  onOpenWizard
}) => {
  const [profiles, setProfiles] = useState<OutputVoiceProfileEntity[]>(
    LocalStorageManager.getOutputVoiceProfiles()
  );
  const [activeProfile, setActiveProfile] = useState<OutputVoiceProfileEntity>(
    LocalStorageManager.getActiveProfile()
  );
  const [testLanguage, setTestLanguage] = useState<LanguageCode>('en');
  const [isPlaying, setIsPlaying] = useState<boolean>(false);

  const handleSelectProfile = (profileId: string) => {
    LocalStorageManager.setActiveProfile(profileId);
    const updated = LocalStorageManager.getOutputVoiceProfiles();
    setProfiles(updated);
    setActiveProfile(LocalStorageManager.getActiveProfile());
  };

  const handleDeleteProfile = (profileId: string) => {
    if (confirm('Delete this custom output voice profile?')) {
      LocalStorageManager.deleteOutputVoiceProfile(profileId);
      setProfiles(LocalStorageManager.getOutputVoiceProfiles());
      setActiveProfile(LocalStorageManager.getActiveProfile());
    }
  };

  const handleTestProfileSpeech = async (profile: OutputVoiceProfileEntity) => {
    setIsPlaying(true);
    let sample = '';
    if (testLanguage === 'bn') {
      sample = 'শুভম, আমি অনিন। এটি আমার সংশোধিত অডিও আউটপুট প্রোফাইল।';
    } else if (testLanguage === 'hi') {
      sample = 'शुभम, मैं अनिन हूँ। यह मेरा संशोधित ऑडियो आउटपुट प्रोफ़ाइल है।';
    } else {
      sample = 'Subham, I am Anin. This is my active spoken acoustic profile.';
    }

    try {
      await AudioPlaybackManager.speak(sample, testLanguage);
    } finally {
      setIsPlaying(false);
    }
  };

  const handlePitchChange = (newPitch: number) => {
    const updated = profiles.map((p) =>
      p.id === activeProfile.id ? { ...p, preferredPitch: newPitch } : p
    );
    LocalStorageManager.saveOutputVoiceProfiles(updated);
    setProfiles(updated);
    setActiveProfile({ ...activeProfile, preferredPitch: newPitch });
  };

  const handleRateChange = (newRate: number) => {
    const updated = profiles.map((p) =>
      p.id === activeProfile.id ? { ...p, preferredRate: newRate } : p
    );
    LocalStorageManager.saveOutputVoiceProfiles(updated);
    setProfiles(updated);
    setActiveProfile({ ...activeProfile, preferredRate: newRate });
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* HEADER BANNER */}
      <div className="bg-gradient-to-r from-indigo-950/40 via-slate-900 to-slate-900 border border-indigo-500/30 rounded-2xl p-5 shadow-xl">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-3 bg-indigo-500/10 text-indigo-400 rounded-xl border border-indigo-500/20">
              <Volume2 className="w-8 h-8" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-white">System B: Output Voice Studio</h2>
                <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-indigo-500/20 text-indigo-300">
                  SYNTHESIS ENGINE
                </span>
              </div>
              <p className="text-xs text-slate-300 mt-1 max-w-xl">
                Configure the spoken voice Anin uses to answer Subham.
                Strictly separated from biometric authorization.
              </p>
            </div>
          </div>

          <button
            onClick={onOpenWizard}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold rounded-xl flex items-center gap-2 transition shadow-lg shadow-indigo-900/40"
          >
            <Plus className="w-4 h-4" /> Create Custom Voice
          </button>
        </div>
      </div>

      {/* ACTIVE PROFILE HERO CARD */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-800">
          <div>
            <span className="text-xs font-semibold text-indigo-400 uppercase tracking-wider">
              Currently Selected Voice
            </span>
            <h3 className="text-xl font-bold text-white mt-1">{activeProfile.displayName}</h3>
            <p className="text-xs text-slate-400 mt-0.5">{activeProfile.description}</p>
          </div>

          <div className="flex items-center gap-2">
            <select
              value={testLanguage}
              onChange={(e) => setTestLanguage(e.target.value as LanguageCode)}
              className="px-3 py-1.5 bg-slate-950 border border-slate-700 rounded-lg text-xs text-slate-200 focus:outline-none"
            >
              <option value="en">English Test</option>
              <option value="bn">বাংলা (Bengali)</option>
              <option value="hi">हिन्दी (Hindi)</option>
            </select>

            <button
              onClick={() => handleTestProfileSpeech(activeProfile)}
              disabled={isPlaying}
              className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-xs font-medium rounded-lg flex items-center gap-1.5 transition"
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              <span>{isPlaying ? 'Playing...' : 'Play Preview'}</span>
            </button>
          </div>
        </div>

        {/* Live Pitch & Cadence Sliders */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300 font-medium">Acoustic Pitch Multiplier</span>
              <span className="font-mono text-indigo-400">{activeProfile.preferredPitch.toFixed(2)}x</span>
            </div>
            <input
              type="range"
              min="0.6"
              max="1.8"
              step="0.05"
              value={activeProfile.preferredPitch}
              onChange={(e) => handlePitchChange(parseFloat(e.target.value))}
              className="w-full accent-indigo-500 cursor-pointer"
            />
            <div className="flex justify-between text-[10px] text-slate-500 font-mono">
              <span>0.6x (Deep Resonant)</span>
              <span>1.0x (Neutral)</span>
              <span>1.8x (Bright Female)</span>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300 font-medium">Cadence / Speaking Pace</span>
              <span className="font-mono text-indigo-400">{activeProfile.preferredRate.toFixed(2)}x</span>
            </div>
            <input
              type="range"
              min="0.7"
              max="1.5"
              step="0.05"
              value={activeProfile.preferredRate}
              onChange={(e) => handleRateChange(parseFloat(e.target.value))}
              className="w-full accent-indigo-500 cursor-pointer"
            />
            <div className="flex justify-between text-[10px] text-slate-500 font-mono">
              <span>0.7x (Deliberate)</span>
              <span>1.0x (Conversational)</span>
              <span>1.5x (Fast)</span>
            </div>
          </div>
        </div>

        {/* Strict Security Badge */}
        <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-xl flex items-center justify-between text-xs">
          <div className="flex items-center gap-2 text-amber-300">
            <AlertTriangle className="w-4 h-4 flex-shrink-0" />
            <span>isAuthorizedSpeaker flag:</span>
          </div>
          <span className="font-mono font-bold px-2 py-0.5 bg-amber-950 text-amber-300 rounded">
            FALSE (Strict Isolation)
          </span>
        </div>
      </div>

      {/* INSTALLED OUTPUT PROFILES */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
          Available Output Voices ({profiles.length})
        </h4>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {profiles.map((profile) => (
            <div
              key={profile.id}
              className={`p-4 rounded-xl border transition ${
                profile.isActive
                  ? 'bg-indigo-950/20 border-indigo-500/50 shadow-lg'
                  : 'bg-slate-900 border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <h5 className="font-semibold text-sm text-white">{profile.displayName}</h5>
                    <span
                      className={`text-[9px] px-1.5 py-0.2 rounded font-mono font-bold ${
                        profile.sourceType === 'BUILT_IN_ANIN'
                          ? 'bg-slate-800 text-slate-300'
                          : 'bg-indigo-500/20 text-indigo-300'
                      }`}
                    >
                      {profile.sourceType === 'BUILT_IN_ANIN' ? 'BUILT-IN' : 'CUSTOM CLONED'}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1 line-clamp-2">{profile.description}</p>
                </div>

                {profile.sourceType !== 'BUILT_IN_ANIN' && (
                  <button
                    onClick={() => handleDeleteProfile(profile.id)}
                    className="p-1.5 text-slate-500 hover:text-rose-400 rounded-lg hover:bg-slate-800 transition"
                    title="Delete Custom Voice"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
              </div>

              <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs">
                <span className="text-slate-400 font-mono text-[11px]">
                  Pitch: {profile.preferredPitch}x • Rate: {profile.preferredRate}x
                </span>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handleTestProfileSpeech(profile)}
                    className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded text-xs transition"
                  >
                    Test
                  </button>

                  {!profile.isActive && (
                    <button
                      onClick={() => handleSelectProfile(profile.id)}
                      className="px-3 py-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded text-xs font-medium transition"
                    >
                      Set Active
                    </button>
                  )}

                  {profile.isActive && (
                    <span className="flex items-center gap-1 text-emerald-400 text-xs font-semibold">
                      <Check className="w-4 h-4" /> Active
                    </span>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
