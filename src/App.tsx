import React, { useState, useEffect } from 'react';
import {
  MessageSquare,
  Fingerprint,
  Shield,
  Volume2,
  Brain,
  Sliders,
  CheckCircle,
  AlertTriangle,
  Square,
  KeyRound,
  Download
} from 'lucide-react';
import { AssistantInteraction, EnrollmentMetadata } from './types';
import { LocalStorageManager } from './core/storage';
import { AudioPlaybackManager } from './core/audioPlayback';
import { AssistantLiveScreen } from './screens/AssistantLiveScreen';
import { SubhamEnrollmentScreen } from './screens/SubhamEnrollmentScreen';
import { AuthenticationVoiceScreen } from './screens/AuthenticationVoiceScreen';
import { OutputVoiceStudioScreen } from './screens/OutputVoiceStudioScreen';
import { PersonalMemoryScreen } from './screens/PersonalMemoryScreen';
import { PrivacySettingsScreen } from './screens/PrivacySettingsScreen';
import { PermissionCenterScreen } from './screens/PermissionCenterScreen';
import { VoiceEnrollmentWizard } from './components/VoiceEnrollmentWizard';

type AppNavDestination =
  | 'assistant_live'
  | 'subham_enrollment'
  | 'authentication_voice'
  | 'output_voice_studio'
  | 'personal_memory'
  | 'permission_center'
  | 'privacy_settings';

export const App: React.FC = () => {
  const [currentScreen, setCurrentScreen] = useState<AppNavDestination>('assistant_live');
  const [subhamMetadata, setSubhamMetadata] = useState<EnrollmentMetadata>(
    LocalStorageManager.getSubhamMetadata()
  );
  const [interactions, setInteractions] = useState<AssistantInteraction[]>([]);
  const [isWizardOpen, setIsWizardOpen] = useState(false);
  const [isSpeaking, setIsSpeaking] = useState(false);

  useEffect(() => {
    // Seed initial truthful interaction based on real enrollment state
    const defaultProfile = LocalStorageManager.getActiveProfile();
    const meta = LocalStorageManager.getSubhamMetadata();

    if (meta.isEnrolled) {
      setInteractions([
        {
          id: 'interaction_welcome',
          timestamp: Date.now() - 30000,
          query: 'Hey Anin',
          detectedLanguage: 'en',
          verifiedSubham: true,
          confidenceScore: 0.94,
          responseText:
            'Subham, Anin is active on your iQOO Neo 10R. Speech capture, intelligence layer, and Android capability provider are synchronized.',
          voiceProfileUsed: defaultProfile.displayName,
          isSilentRejection: false,
          audioLatencyMs: 145
        }
      ]);
    } else {
      setInteractions([
        {
          id: 'interaction_welcome',
          timestamp: Date.now() - 30000,
          query: 'System Initialized',
          detectedLanguage: 'en',
          verifiedSubham: false,
          confidenceScore: 0.0,
          responseText:
            'Anin is ready on your iQOO Neo 10R. Subham Voice Enrollment is required in System A before voice commands can be authenticated and executed.',
          voiceProfileUsed: defaultProfile.displayName,
          isSilentRejection: false,
          audioLatencyMs: 50
        }
      ]);
    }

    const unsubscribe = AudioPlaybackManager.subscribe((status) => {
      setIsSpeaking(status.isSpeaking);
    });

    return unsubscribe;
  }, []);

  const refreshState = () => {
    setSubhamMetadata(LocalStorageManager.getSubhamMetadata());
  };

  const navItems = [
    { id: 'assistant_live', label: 'Live', icon: MessageSquare },
    { id: 'subham_enrollment', label: 'Enroll', icon: Fingerprint },
    { id: 'authentication_voice', label: 'Security', icon: Shield },
    { id: 'output_voice_studio', label: 'Studio', icon: Volume2 },
    { id: 'personal_memory', label: 'Memory', icon: Brain },
    { id: 'permission_center', label: 'Permissions', icon: KeyRound },
    { id: 'privacy_settings', label: 'Diagnostics', icon: Sliders }
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-indigo-500 selection:text-white">
      {/* TOP APP BAR */}
      <header className="sticky top-0 z-40 bg-slate-900/90 backdrop-blur-md border-b border-slate-800 px-4 py-3">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white shadow-md shadow-indigo-600/30">
              A
            </div>
            <div>
              <h1 className="text-base font-bold text-white tracking-tight flex items-center gap-2">
                Anin
                <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-indigo-500/20 text-indigo-300 font-normal">
                  v1.0 • Step 1+2+3 • iQOO Neo 10R
                </span>
              </h1>
              <p className="text-[11px] text-slate-400">Personal Private Voice Assistant</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Quick Barge-in Stop Button if audio is speaking */}
            {isSpeaking && (
              <button
                onClick={() => AudioPlaybackManager.emergencyStop()}
                className="px-3 py-1.5 bg-rose-600 hover:bg-rose-500 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 animate-pulse transition shadow-lg shadow-rose-900/50"
              >
                <Square className="w-3.5 h-3.5 fill-current" />
                <span>Stop Spoken Voice</span>
              </button>
            )}

            {/* Direct APK Download Link */}
            <a
              href="/download/app-debug.apk"
              download="anin-app-debug.apk"
              className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition shadow-md shadow-indigo-600/30"
              title="Download installable debug APK directly to your phone (17.8 MB)"
            >
              <Download className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Download</span> APK
            </a>

            {/* Subham Enrollment AssistChip */}
            <button
              onClick={() => setCurrentScreen('subham_enrollment')}
              className={`px-3 py-1.5 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition ${
                subhamMetadata.isEnrolled
                  ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300 hover:bg-emerald-900/40'
                  : 'bg-amber-950/40 border-amber-500/40 text-amber-300 hover:bg-amber-900/40'
              }`}
            >
              {subhamMetadata.isEnrolled ? (
                <>
                  <CheckCircle className="w-3.5 h-3.5 text-emerald-400" />
                  <span>Subham: Enrolled</span>
                </>
              ) : (
                <>
                  <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
                  <span>Subham: Unenrolled</span>
                </>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* MAIN CONTENT AREA */}
      <main className="flex-1 p-4 md:p-6 max-w-6xl mx-auto w-full">
        {currentScreen === 'assistant_live' && (
          <AssistantLiveScreen
            interactions={interactions}
            onNewInteraction={(item) => setInteractions((prev) => [...prev, item])}
            onNavigateToEnrollment={() => setCurrentScreen('subham_enrollment')}
            onNavigateToStudio={() => setCurrentScreen('output_voice_studio')}
          />
        )}

        {currentScreen === 'subham_enrollment' && (
          <SubhamEnrollmentScreen
            onEnrollmentComplete={() => {
              refreshState();
              setCurrentScreen('assistant_live');
            }}
          />
        )}

        {currentScreen === 'authentication_voice' && <AuthenticationVoiceScreen />}

        {currentScreen === 'output_voice_studio' && (
          <OutputVoiceStudioScreen onOpenWizard={() => setIsWizardOpen(true)} />
        )}

        {currentScreen === 'personal_memory' && <PersonalMemoryScreen />}

        {currentScreen === 'permission_center' && <PermissionCenterScreen />}

        {currentScreen === 'privacy_settings' && (
          <PrivacySettingsScreen onDataReset={refreshState} />
        )}
      </main>

      {/* BOTTOM NAVIGATION BAR */}
      <nav className="sticky bottom-0 z-40 bg-slate-900/95 backdrop-blur-md border-t border-slate-800 px-2 py-1.5">
        <div className="max-w-2xl mx-auto flex items-center justify-around">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = currentScreen === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setCurrentScreen(item.id as AppNavDestination)}
                className={`flex flex-col items-center py-1 px-2.5 rounded-xl transition text-[10px] font-medium ${
                  isActive
                    ? 'text-indigo-400 bg-indigo-500/10'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Icon className={`w-4 h-4 mb-0.5 ${isActive ? 'stroke-[2.5]' : 'stroke-2'}`} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Voice Enrollment Wizard Modal */}
      {isWizardOpen && (
        <VoiceEnrollmentWizard
          isOpen={isWizardOpen}
          onClose={() => setIsWizardOpen(false)}
          onProfileCreated={() => {
            setIsWizardOpen(false);
            refreshState();
          }}
        />
      )}
    </div>
  );
};
