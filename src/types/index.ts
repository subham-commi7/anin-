export type LanguageCode = 'en' | 'bn' | 'hi';

export interface VoiceLanguageConfig {
  code: LanguageCode;
  displayName: string;
  samplePrompt: string;
  testSentence: string;
}

export const VOICE_LANGUAGES: Record<LanguageCode, VoiceLanguageConfig> = {
  en: {
    code: 'en',
    displayName: 'English',
    samplePrompt: 'Hey Anin, what is my schedule today?',
    testSentence: 'Good morning Subham. All systems are nominal and secure.'
  },
  bn: {
    code: 'bn',
    displayName: 'বাংলা (Bengali)',
    samplePrompt: 'হে অনিন, আজকের আবহাওয়া কেমন?',
    testSentence: 'নমস্কার শুভম। আমি অনিন, আপনার ব্যক্তিগত ভয়েস অ্যাসিস্ট্যান্ট।'
  },
  hi: {
    code: 'hi',
    displayName: 'हिन्दी (Hindi)',
    samplePrompt: 'हे अनিন, मेरी आगामी मीटिंग कब है?',
    testSentence: 'नमस्ते शुभम। मैं अनिन हूँ, आपकी सुरक्षित वॉइस असिस्टेंट।'
  }
};

export enum VoiceProcessingMode {
  LOCAL_OFFLINE = 'LOCAL_OFFLINE',
  ONLINE_CLOUD = 'ONLINE_CLOUD',
  HYBRID = 'HYBRID'
}

export enum VoiceSourceType {
  BUILT_IN_ANIN = 'BUILT_IN_ANIN',
  CUSTOM_USER_CLONED = 'CUSTOM_USER_CLONED',
  SYSTEM_FALLBACK = 'SYSTEM_FALLBACK'
}

export interface AudioQualityCheck {
  isValid: boolean;
  sampleRate: number;
  durationMs: number;
  rmsLevelDb: number;
  clippingDetected: boolean;
  silenceRatio: number;
  speechDetected: boolean;
  pitchMultiplier: number;
  rateMultiplier: number;
  failureReasons: string[];
}

export interface OutputVoiceProfileEntity {
  id: string;
  profileName: string;
  displayName: string;
  sourceType: VoiceSourceType;
  modelType: string;
  preferredPitch: number; // 0.5 to 2.0 (default 1.0)
  preferredRate: number;  // 0.5 to 2.0 (default 1.0)
  volume: number;         // 0.0 to 1.0
  processingMode: VoiceProcessingMode;
  isActive: boolean;
  sampleAudioPath?: string;
  createdAt: number;
  description: string;
  supportedLanguages: LanguageCode[];
  isAuthorizedSpeaker: boolean; // MUST ALWAYS BE FALSE for Output Voices!
}

export interface VerificationResult {
  isVerified: boolean;
  confidenceScore: number;
  speakerId: string;
  failureReason?: string;
  matchThreshold: number;
  executionAllowed: boolean;
  rawPcmRetained: boolean; // Always false for privacy
}

export interface SpeakerEnrollmentResult {
  isSuccessful: boolean;
  samplesCompleted: number;
  totalRequired: number;
  averageQualityScore: number;
  enrolledAt?: number;
  message: string;
}

export interface EnrollmentMetadata {
  isEnrolled: boolean;
  totalSamplesEnrolled: number;
  lastEnrolledTimestamp: number;
  verificationPassCount: number;
  verificationFailureCount: number;
  audioFeaturesFingerprint: string;
  modelHash: string;
}

export interface EnrollmentSentence {
  index: number;
  language: LanguageCode;
  promptText: string;
  speakingPace: 'normal' | 'fast' | 'slow';
  tone: 'conversational' | 'formal' | 'inquiry';
  phoneticPattern: string;
}

export interface SubhamEnrollmentSampleEntity {
  id: string;
  sampleIndex: number;
  language: LanguageCode;
  textPrompt: string;
  acousticFeaturesVector: number[];
  qualityScore: number;
  durationMs: number;
  isUsable: boolean;
  createdAt: number;
}

// STEP 2: 11 Structured Memory Categories
export type MemoryCategory =
  | 'USER_PROFILE'
  | 'PREFERENCES'
  | 'ROUTINES'
  | 'PEOPLE'
  | 'IMPORTANT_FACTS'
  | 'REMINDERS'
  | 'COMMAND_HISTORY'
  | 'CONVERSATION_SUMMARIES'
  | 'DEVICE_PREFERENCES'
  | 'ASSISTANT_PREFERENCES'
  | 'TEMPORARY_CONTEXT';

export const MEMORY_CATEGORIES: { id: MemoryCategory; label: string; icon: string; description: string }[] = [
  { id: 'USER_PROFILE', label: 'User Profile', icon: 'User', description: 'Name, identity, personal background' },
  { id: 'PREFERENCES', label: 'Language & Habits', icon: 'Sliders', description: 'Preferred language blends, communication styles' },
  { id: 'ROUTINES', label: 'Daily Routines', icon: 'Clock', description: 'Sleep schedule, workout times, recurring work blocks' },
  { id: 'PEOPLE', label: 'People & Relationships', icon: 'Users', description: 'Friends, family, colleagues (e.g. Shubhrata, Dad)' },
  { id: 'IMPORTANT_FACTS', label: 'Important Facts', icon: 'FileText', description: 'Critical info, addresses, hardware details' },
  { id: 'REMINDERS', label: 'Active Reminders', icon: 'AlarmClock', description: 'Time-sensitive tasks & alerts' },
  { id: 'COMMAND_HISTORY', label: 'Command History', icon: 'History', description: 'Audited log of authorized executions' },
  { id: 'CONVERSATION_SUMMARIES', label: 'Conversation Summaries', icon: 'MessageSquare', description: 'Digest of past discussions' },
  { id: 'DEVICE_PREFERENCES', label: 'Device Preferences', icon: 'Smartphone', description: 'iQOO Neo 10R DSP & performance modes' },
  { id: 'ASSISTANT_PREFERENCES', label: 'Assistant Persona', icon: 'Sparkles', description: 'Anin tone, verbosity, and pacing' },
  { id: 'TEMPORARY_CONTEXT', label: 'Temporary Context', icon: 'Layers', description: 'Current session topic & active entity thread' }
];

export interface PersonalMemoryEntity {
  id: string;
  category: MemoryCategory;
  key: string;
  encryptedValue: string;
  rawPreview: string;
  createdAt: number;
  lastAccessedAt: number;
  requiresBiometricAuth: boolean;
  metadata?: Record<string, any>;
}

export interface ReminderEntity {
  id: string;
  title: string;
  dueTimeFormatted: string;
  targetTimestamp?: number;
  recurrence?: 'none' | 'daily' | 'weekly' | 'monthly';
  language: LanguageCode;
  isCompleted: boolean;
  createdAt: number;
}

export interface SecurityAuditLogEntity {
  id: string;
  timestamp: number;
  eventType:
    | 'AUTHENTICATION_SUCCESS'
    | 'AUTHENTICATION_FAILED'
    | 'VOICE_PROFILE_ENROLLED'
    | 'PROFILE_SWITCHED'
    | 'DATA_CLEARED'
    | 'BARGE_IN_TRIGGERED'
    | 'TOOL_EXECUTED'
    | 'UNSAFE_ACTION_REFUSED'
    | 'WEB_SEARCH_PERFORMED';
  speakerIdentified: string;
  confidenceScore: number;
  actionTaken: string;
  sensitiveAudioStored: boolean; // Always false
}

export interface DeviceSystemDiagnostics {
  deviceName: string;
  ramTotalGb: number;
  ramAvailableGb: number;
  osVersion: string;
  buildNumber: string;
  chipset: string;
  batteryPercentage: number;
  batteryStatus: string;
  thermalStatus: string;
  networkType: string;
  acousticEchoCancellation: boolean;
  noiseSuppression: boolean;
  automaticGainControl: boolean;
  encryptedVaultHardwareBacked: boolean;
}

export interface ProcessingModeSettings {
  voiceProcessingMode: VoiceProcessingMode;
  audioRetentionDays: number;
  allowCloudSynthesis: boolean;
  bargeInSensitivity: number; // 0 to 100
  strictVerificationThreshold: number; // 0.6 to 0.95
}

// STEP 2: Safety Levels
export enum ActionSafetyLevel {
  LEVEL_0_READ_ONLY = 0,        // Search, battery, weather, memory lookup (no confirmation needed)
  LEVEL_1_LOCAL_REVERSIBLE = 1, // Save note, create reminder
  LEVEL_2_EXTERNAL_SIDE_EFFECT = 2, // Calling, messaging, appointments, deleting data
  LEVEL_3_HIGH_RISK_PROHIBITED = 3 // Banking, UPI, payments, transfers, credentials (STRICT REFUSAL)
}

// STEP 2: Action Classifications
export type ActionClassification =
  | 'CONVERSATION'
  | 'INFORMATION'
  | 'WEB_SEARCH'
  | 'MEMORY_OPERATION'
  | 'REMINDER'
  | 'DEVICE_ACTION'
  | 'UNSUPPORTED'
  | 'REFUSED_UNSAFE';

// STEP 2 & 3: Device Capability Status
export type CapabilityStatus =
  | 'SUCCESS'
  | 'FAILED'
  | 'DENIED'
  | 'NOT_GRANTED'
  | 'NOT_AVAILABLE'
  | 'NOT_SUPPORTED'
  | 'REQUIRES_PERMISSION'
  | 'REQUIRES_USER_ACTION'
  | 'REQUIRES_NATIVE_SYSTEM_UI'
  | 'REQUIRES_CONFIRMATION'
  | 'AVAILABLE'
  | 'UNAVAILABLE'
  | 'REQUIRES_NATIVE_BRIDGE';

export interface WebSearchSource {
  title: string;
  uri: string;
  snippet?: string;
}

export interface StructuredToolCall {
  toolName: string;
  arguments: Record<string, any>;
  result?: any;
  status: 'PENDING_CONFIRMATION' | 'EXECUTED' | 'FAILED' | 'REFUSED';
  capabilityStatus?: CapabilityStatus;
  safetyLevel: ActionSafetyLevel;
}

export interface AssistantInteraction {
  id: string;
  timestamp: number;
  query: string;
  detectedLanguage: LanguageCode;
  verifiedSubham: boolean;
  confidenceScore: number;
  responseText: string;
  actionExecuted?: string;
  actionClassification?: ActionClassification;
  safetyLevel?: ActionSafetyLevel;
  voiceProfileUsed: string;
  isSilentRejection: boolean;
  audioLatencyMs: number;
  searchSources?: WebSearchSource[];
  toolCalls?: StructuredToolCall[];
  memoryImpact?: {
    action: 'SAVE' | 'READ' | 'UPDATE' | 'DELETE' | 'CLEAR';
    key?: string;
  };
}

export interface AudioCaptureMetrics {
  isCapturing: boolean;
  currentDbfs: number;
  speechActivityDetected: boolean;
  wakeWordDetected: boolean;
  echoCancellationActive: boolean;
  noiseSuppressionActive: boolean;
  automaticGainControlActive: boolean;
}

export interface OutputVoiceEnrollmentState {
  currentStep: number; // 1 to 5
  voiceName: string;
  description: string;
  selectedLanguage: LanguageCode;
  recordedSamples: {
    sentenceIndex: number;
    sentenceText: string;
    blobUrl?: string;
    durationMs: number;
    quality: AudioQualityCheck;
  }[];
  validationPassed: boolean;
  consentGiven: boolean;
}

export function detectLanguage(text: string): LanguageCode {
  // Bengali Unicode range: 0x0980 - 0x09FF
  const hasBengali = /[\u0980-\u09FF]/.test(text);
  if (hasBengali) return 'bn';
  // Devanagari / Hindi Unicode range: 0x0900 - 0x097F
  const hasHindi = /[\u0900-\u097F]/.test(text);
  if (hasHindi) return 'hi';
  return 'en';
}
