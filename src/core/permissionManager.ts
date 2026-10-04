export interface PermissionRecord {
  id: string;
  name: string;
  androidPermission: string;
  feature: string;
  whyRequired: string;
  isRuntime: boolean;
  isSpecialAccess: boolean;
  isGranted: boolean;
  denialBehavior: string;
  settingsAction: string;
}

export class PermissionManager {
  private static readonly STORAGE_KEY = 'anin_permissions_registry';

  private static defaultRegistry: PermissionRecord[] = [
    {
      id: 'mic',
      name: 'Microphone & Audio Capture',
      androidPermission: 'android.permission.RECORD_AUDIO',
      feature: 'Wake-Word & Subham Voice Biometrics',
      whyRequired: 'Captures 16 kHz PCM audio for local "Hey Anin" wake detection and biometric speaker verification.',
      isRuntime: true,
      isSpecialAccess: false,
      isGranted: true,
      denialBehavior: 'Speech interaction disabled; text input and device diagnostics remain operational.',
      settingsAction: 'android.settings.APPLICATION_DETAILS_SETTINGS'
    },
    {
      id: 'notif',
      name: 'Notifications & Alerts',
      androidPermission: 'android.permission.POST_NOTIFICATIONS',
      feature: 'Reminders & Service Indicator',
      whyRequired: 'Displays scheduled reminder alerts and persistent audio capture foreground notification on Android 13+.',
      isRuntime: true,
      isSpecialAccess: false,
      isGranted: true,
      denialBehavior: 'Reminders save silently into Room vault, but visual banners will not appear.',
      settingsAction: 'android.settings.APP_NOTIFICATION_SETTINGS'
    },
    {
      id: 'contacts',
      name: 'Contacts Directory',
      androidPermission: 'android.permission.READ_CONTACTS',
      feature: 'Contact Name Resolution',
      whyRequired: 'Allows Anin to resolve spoken names like "Shubhrata" or "Rahul" to phone numbers for calling and messaging.',
      isRuntime: true,
      isSpecialAccess: false,
      isGranted: true,
      denialBehavior: 'Name lookup disabled; user must dictate full phone numbers manually.',
      settingsAction: 'android.settings.APPLICATION_DETAILS_SETTINGS'
    },
    {
      id: 'phone',
      name: 'Phone & Dialer',
      androidPermission: 'android.permission.CALL_PHONE',
      feature: 'Direct Telephony Calling',
      whyRequired: 'Required for initiating cellular calls without showing manual confirmation keypad.',
      isRuntime: true,
      isSpecialAccess: false,
      isGranted: false, // Starts ungranted: Anin safely defaults to ACTION_DIAL
      denialBehavior: 'Anin opens Android dialer keypad with number pre-filled instead of placing call silently.',
      settingsAction: 'android.settings.APPLICATION_DETAILS_SETTINGS'
    },
    {
      id: 'sms',
      name: 'SMS Messaging',
      androidPermission: 'android.permission.SEND_SMS',
      feature: 'Direct Text Messaging',
      whyRequired: 'Enables Anin to dispatch SMS text messages directly when requested.',
      isRuntime: true,
      isSpecialAccess: false,
      isGranted: false, // Defaults safely to ACTION_SENDTO draft
      denialBehavior: 'Anin opens Messages app with draft pre-filled for user review.',
      settingsAction: 'android.settings.APPLICATION_DETAILS_SETTINGS'
    },
    {
      id: 'calendar',
      name: 'Calendar Access',
      androidPermission: 'android.permission.WRITE_CALENDAR',
      feature: 'Event Scheduling & Agendas',
      whyRequired: 'Allows Anin to query daily events and insert new appointments into device calendar.',
      isRuntime: true,
      isSpecialAccess: false,
      isGranted: true,
      denialBehavior: 'Direct calendar synchronization disabled; Anin drafts events via system Intent.',
      settingsAction: 'android.settings.APPLICATION_DETAILS_SETTINGS'
    },
    {
      id: 'alarm',
      name: 'System Alarms',
      androidPermission: 'com.android.alarm.permission.SET_ALARM',
      feature: 'Clock Alarms & Timers',
      whyRequired: 'Allows Anin to program Android system alarms directly via AlarmClock provider.',
      isRuntime: false,
      isSpecialAccess: false,
      isGranted: true,
      denialBehavior: 'Alarms cannot be automated; Anin opens Clock application directly.',
      settingsAction: 'android.settings.APPLICATION_DETAILS_SETTINGS'
    },
    {
      id: 'location',
      name: 'Location & Weather',
      androidPermission: 'android.permission.ACCESS_FINE_LOCATION',
      feature: 'Local Weather & Maps Directions',
      whyRequired: 'Provides accurate latitude/longitude coordinates for local weather reports and navigation.',
      isRuntime: true,
      isSpecialAccess: false,
      isGranted: true,
      denialBehavior: 'Defaults to user-configured home city (Kolkata) for weather and navigation.',
      settingsAction: 'android.settings.LOCATION_SOURCE_SETTINGS'
    },
    {
      id: 'notif_listener',
      name: 'Notification Listener',
      androidPermission: 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',
      feature: 'OTP & Verification Code Reader',
      whyRequired: 'Allows Anin to read incoming OTP security codes from Google Messages. (Financial execution is strictly blocked).',
      isRuntime: false,
      isSpecialAccess: true,
      isGranted: false,
      denialBehavior: 'Cannot read OTP from notifications; user must enter codes manually.',
      settingsAction: 'android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS'
    }
  ];

  static getAllPermissions(): PermissionRecord[] {
    const raw = localStorage.getItem(this.STORAGE_KEY);
    if (!raw) {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.defaultRegistry));
      return this.defaultRegistry;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return this.defaultRegistry;
    }
  }

  static isGranted(id: string): boolean {
    const all = this.getAllPermissions();
    const perm = all.find((p) => p.id === id);
    return perm ? perm.isGranted : false;
  }

  static togglePermission(id: string): PermissionRecord[] {
    const all = this.getAllPermissions();
    const updated = all.map((p) => {
      if (p.id === id) {
        return { ...p, isGranted: !p.isGranted };
      }
      return p;
    });
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(updated));
    return updated;
  }

  static resetAll(): PermissionRecord[] {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.defaultRegistry));
    return this.defaultRegistry;
  }
}
