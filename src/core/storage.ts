import {
  OutputVoiceProfileEntity,
  PersonalMemoryEntity,
  ReminderEntity,
  SecurityAuditLogEntity,
  SubhamEnrollmentSampleEntity,
  EnrollmentMetadata
} from '../types';

const STORAGE_KEYS = {
  PROFILES: 'anin_voice_profiles',
  MEMORIES: 'anin_personal_memories',
  REMINDERS: 'anin_reminders',
  AUDIT_LOGS: 'anin_audit_logs',
  SUBHAM_METADATA: 'anin_subham_metadata',
  SUBHAM_SAMPLES: 'anin_subham_samples',
  SETTINGS: 'anin_system_settings'
};

// Simulated AES-GCM 256 encryption simulator (hardware-backed key simulation)
export class CryptoVault {
  static encrypt(plainText: string): string {
    const salt = Math.random().toString(36).substring(2, 8);
    const encoded = btoa(unescape(encodeURIComponent(plainText)));
    return `HW_AES256GCM_${salt}_${encoded}`;
  }

  static decrypt(cipherText: string): string {
    if (!cipherText.startsWith('HW_AES256GCM_')) return cipherText;
    const parts = cipherText.split('_');
    if (parts.length < 3) return cipherText;
    try {
      return decodeURIComponent(escape(atob(parts[2])));
    } catch {
      return '[Decryption Error: Key Mismatch]';
    }
  }
}

export class LocalStorageManager {
  // Profiles (SYSTEM B: Output Voices)
  static getOutputVoiceProfiles(): OutputVoiceProfileEntity[] {
    const raw = localStorage.getItem(STORAGE_KEYS.PROFILES);
    if (!raw) {
      const defaults = this.getDefaultProfiles();
      this.saveOutputVoiceProfiles(defaults);
      return defaults;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return this.getDefaultProfiles();
    }
  }

  static saveOutputVoiceProfiles(profiles: OutputVoiceProfileEntity[]): void {
    localStorage.setItem(STORAGE_KEYS.PROFILES, JSON.stringify(profiles));
  }

  static getActiveProfile(): OutputVoiceProfileEntity {
    const profiles = this.getOutputVoiceProfiles();
    const active = profiles.find((p) => p.isActive);
    return active || profiles[0];
  }

  static setActiveProfile(profileId: string): void {
    const profiles = this.getOutputVoiceProfiles().map((p) => ({
      ...p,
      isActive: p.id === profileId
    }));
    this.saveOutputVoiceProfiles(profiles);
    this.addAuditLog({
      id: 'log_' + Date.now(),
      timestamp: Date.now(),
      eventType: 'PROFILE_SWITCHED',
      speakerIdentified: 'Anin Output Engine',
      confidenceScore: 1.0,
      actionTaken: `Switched active output voice profile to ${profileId}`,
      sensitiveAudioStored: false
    });
  }

  static addOutputVoiceProfile(profile: OutputVoiceProfileEntity): void {
    const profiles = this.getOutputVoiceProfiles();
    // Ensure isAuthorizedSpeaker is STRICTLY false
    const cleanProfile = { ...profile, isAuthorizedSpeaker: false };
    profiles.push(cleanProfile);
    this.saveOutputVoiceProfiles(profiles);
    this.addAuditLog({
      id: 'log_' + Date.now(),
      timestamp: Date.now(),
      eventType: 'VOICE_PROFILE_ENROLLED',
      speakerIdentified: 'System',
      confidenceScore: 1.0,
      actionTaken: `Created custom output voice: ${cleanProfile.displayName}`,
      sensitiveAudioStored: false
    });
  }

  static deleteOutputVoiceProfile(profileId: string): boolean {
    const profiles = this.getOutputVoiceProfiles();
    const target = profiles.find((p) => p.id === profileId);
    if (!target || target.sourceType === 'BUILT_IN_ANIN') {
      return false; // Cannot delete built-in primary voice
    }
    const filtered = profiles.filter((p) => p.id !== profileId);
    if (target.isActive && filtered.length > 0) {
      filtered[0].isActive = true;
    }
    this.saveOutputVoiceProfiles(filtered);
    return true;
  }

  private static getDefaultProfiles(): OutputVoiceProfileEntity[] {
    return [
      {
        id: 'anin_built_in_clarity',
        profileName: 'anin_clarity_female',
        displayName: 'Anin Female (Warm Natural)',
        sourceType: 'BUILT_IN_ANIN' as any,
        modelType: 'Neural Acoustic Formant v3.2',
        preferredPitch: 1.15,
        preferredRate: 1.02,
        volume: 1.0,
        processingMode: 'LOCAL_OFFLINE' as any,
        isActive: true,
        createdAt: 1717200000000,
        description: 'Primary default voice for Anin. Crisp, natural Bengali, Hindi & English female tone.',
        supportedLanguages: ['en', 'bn', 'hi'],
        isAuthorizedSpeaker: false
      },
      {
        id: 'anin_built_in_concise',
        profileName: 'anin_concise_direct',
        displayName: 'Anin Direct (Fast Response)',
        sourceType: 'BUILT_IN_ANIN' as any,
        modelType: 'Linear Low-Latency Synthesis',
        preferredPitch: 1.05,
        preferredRate: 1.22,
        volume: 0.95,
        processingMode: 'LOCAL_OFFLINE' as any,
        isActive: false,
        createdAt: 1717200000000,
        description: 'Optimized for high-speed spoken diagnostics and brief status confirmations.',
        supportedLanguages: ['en', 'bn', 'hi'],
        isAuthorizedSpeaker: false
      },
      {
        id: 'anin_soft_calm',
        profileName: 'anin_soft_intimate',
        displayName: 'Anin Whisper (Soft & Low Volume)',
        sourceType: 'BUILT_IN_ANIN' as any,
        modelType: 'Sub-harmonic Resonator Filter',
        preferredPitch: 0.92,
        preferredRate: 0.92,
        volume: 0.75,
        processingMode: 'LOCAL_OFFLINE' as any,
        isActive: false,
        createdAt: 1717200000000,
        description: 'Quiet night-time assistant profile with relaxed cadence and reduced high-frequency harshness.',
        supportedLanguages: ['en', 'bn', 'hi'],
        isAuthorizedSpeaker: false
      }
    ];
  }

  // SYSTEM A: Subham Voice Enrollment
  static getSubhamMetadata(): EnrollmentMetadata {
    const raw = localStorage.getItem(STORAGE_KEYS.SUBHAM_METADATA);
    if (!raw) {
      return {
        isEnrolled: false,
        totalSamplesEnrolled: 0,
        lastEnrolledTimestamp: 0,
        verificationPassCount: 0,
        verificationFailureCount: 0,
        audioFeaturesFingerprint: '',
        modelHash: 'SECURE_SHA256_EMPTY'
      };
    }
    try {
      return JSON.parse(raw);
    } catch {
      return {
        isEnrolled: false,
        totalSamplesEnrolled: 0,
        lastEnrolledTimestamp: 0,
        verificationPassCount: 0,
        verificationFailureCount: 0,
        audioFeaturesFingerprint: '',
        modelHash: 'SECURE_SHA256_EMPTY'
      };
    }
  }

  static saveSubhamMetadata(meta: EnrollmentMetadata): void {
    localStorage.setItem(STORAGE_KEYS.SUBHAM_METADATA, JSON.stringify(meta));
  }

  static getSubhamSamples(): SubhamEnrollmentSampleEntity[] {
    const raw = localStorage.getItem(STORAGE_KEYS.SUBHAM_SAMPLES);
    if (!raw) return [];
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }

  static saveSubhamSamples(samples: SubhamEnrollmentSampleEntity[]): void {
    localStorage.setItem(STORAGE_KEYS.SUBHAM_SAMPLES, JSON.stringify(samples));
  }

  static deleteSubhamBiometrics(): void {
    localStorage.removeItem(STORAGE_KEYS.SUBHAM_SAMPLES);
    this.saveSubhamMetadata({
      isEnrolled: false,
      totalSamplesEnrolled: 0,
      lastEnrolledTimestamp: 0,
      verificationPassCount: 0,
      verificationFailureCount: 0,
      audioFeaturesFingerprint: '',
      modelHash: 'SECURE_SHA256_EMPTY'
    });
    this.addAuditLog({
      id: 'log_' + Date.now(),
      timestamp: Date.now(),
      eventType: 'DATA_CLEARED',
      speakerIdentified: 'Subham',
      confidenceScore: 1.0,
      actionTaken: 'Deleted Subham acoustic verification vectors and encryption keys',
      sensitiveAudioStored: false
    });
  }

  // Personal Memories
  static getPersonalMemories(): PersonalMemoryEntity[] {
    const raw = localStorage.getItem(STORAGE_KEYS.MEMORIES);
    if (!raw) {
      const defaults: PersonalMemoryEntity[] = [
        {
          id: 'mem_1',
          category: 'IMPORTANT_FACTS',
          key: 'Primary Address',
          encryptedValue: CryptoVault.encrypt('New Town Action Area 1, Kolkata, West Bengal'),
          rawPreview: 'New Town Action Area 1, Kolkata...',
          createdAt: Date.now() - 86400000 * 3,
          lastAccessedAt: Date.now() - 3600000 * 4,
          requiresBiometricAuth: true
        },
        {
          id: 'mem_2',
          category: 'PREFERENCES',
          key: 'Preferred Language Blend',
          encryptedValue: CryptoVault.encrypt('Bengali primary for personal questions, English for technical commands'),
          rawPreview: 'Bengali primary for personal questions...',
          createdAt: Date.now() - 86400000 * 2,
          lastAccessedAt: Date.now() - 3600000,
          requiresBiometricAuth: true
        },
        {
          id: 'mem_3',
          category: 'ROUTINES',
          key: 'Active Project Milestone',
          encryptedValue: CryptoVault.encrypt('Step 1 complete: Anin Personal Private Voice Assistant on iQOO Neo 10R'),
          rawPreview: 'Step 1 complete: Anin Personal Private...',
          createdAt: Date.now() - 86400000,
          lastAccessedAt: Date.now(),
          requiresBiometricAuth: true
        }
      ];
      this.savePersonalMemories(defaults);
      return defaults;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }

  static savePersonalMemories(memories: PersonalMemoryEntity[]): void {
    localStorage.setItem(STORAGE_KEYS.MEMORIES, JSON.stringify(memories));
  }

  static addPersonalMemory(category: any, key: string, plainValue: string): void {
    const memories = this.getPersonalMemories();
    const newMem: PersonalMemoryEntity = {
      id: 'mem_' + Date.now(),
      category,
      key,
      encryptedValue: CryptoVault.encrypt(plainValue),
      rawPreview: plainValue.length > 32 ? plainValue.substring(0, 32) + '...' : plainValue,
      createdAt: Date.now(),
      lastAccessedAt: Date.now(),
      requiresBiometricAuth: true
    };
    memories.unshift(newMem);
    this.savePersonalMemories(memories);
  }

  static deletePersonalMemory(id: string): void {
    const memories = this.getPersonalMemories().filter((m) => m.id !== id);
    this.savePersonalMemories(memories);
  }

  // Reminders
  static getReminders(): ReminderEntity[] {
    const raw = localStorage.getItem(STORAGE_KEYS.REMINDERS);
    if (!raw) {
      const defaults: ReminderEntity[] = [
        {
          id: 'rem_1',
          title: 'Review iQOO Neo 10R Snapdragon Audio DSP latency',
          dueTimeFormatted: 'Today at 6:00 PM',
          language: 'en',
          isCompleted: false,
          createdAt: Date.now() - 7200000
        },
        {
          id: 'rem_2',
          title: 'সন্ধ্যা ৭টায় বাবাকে ফোন করতে হবে',
          dueTimeFormatted: 'Today at 7:00 PM',
          language: 'bn',
          isCompleted: false,
          createdAt: Date.now() - 3600000
        }
      ];
      this.saveReminders(defaults);
      return defaults;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }

  static saveReminders(reminders: ReminderEntity[]): void {
    localStorage.setItem(STORAGE_KEYS.REMINDERS, JSON.stringify(reminders));
  }

  static toggleReminder(id: string): void {
    const reminders = this.getReminders().map((r) =>
      r.id === id ? { ...r, isCompleted: !r.isCompleted } : r
    );
    this.saveReminders(reminders);
  }

  static deleteReminder(id: string): void {
    const reminders = this.getReminders().filter((r) => r.id !== id);
    this.saveReminders(reminders);
  }

  static addReminder(title: string, language: any = 'en'): void {
    const reminders = this.getReminders();
    reminders.unshift({
      id: 'rem_' + Date.now(),
      title,
      dueTimeFormatted: 'Upcoming',
      language,
      isCompleted: false,
      createdAt: Date.now()
    });
    this.saveReminders(reminders);
  }

  // Security Audit Logs (NON-SENSITIVE, NEVER STORES RAW VOICE PCM)
  static getAuditLogs(): SecurityAuditLogEntity[] {
    const raw = localStorage.getItem(STORAGE_KEYS.AUDIT_LOGS);
    if (!raw) return [];
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }

  static addAuditLog(log: SecurityAuditLogEntity): void {
    const logs = this.getAuditLogs();
    logs.unshift({ ...log, sensitiveAudioStored: false });
    // Keep last 100 entries max
    if (logs.length > 100) {
      logs.length = 100;
    }
    localStorage.setItem(STORAGE_KEYS.AUDIT_LOGS, JSON.stringify(logs));
  }

  static clearAuditLogs(): void {
    localStorage.removeItem(STORAGE_KEYS.AUDIT_LOGS);
  }
}
