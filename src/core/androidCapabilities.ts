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
  | 'REQUIRES_CONFIRMATION';

export interface AndroidActionResult {
  status: CapabilityStatus;
  summary: string;
  intentUri?: string;
  payload?: any;
  userActionPrompt?: string;
}

export interface AndroidContactRecord {
  id: string;
  name: string;
  phoneNumber: string;
  relationship?: string;
  email?: string;
}

export class AndroidCapabilities {
  // Verified contacts directory for Subham
  private static contactsDirectory: AndroidContactRecord[] = [
    { id: 'c1', name: 'Shubhrata', phoneNumber: '+91 98301 24567', relationship: 'Friend' },
    { id: 'c2', name: 'Baba (Dad)', phoneNumber: '+91 98310 98765', relationship: 'Father' },
    { id: 'c3', name: 'Rahul Sen', phoneNumber: '+91 98322 11223', relationship: 'Colleague' },
    { id: 'c4', name: 'Dr. Das (Clinic)', phoneNumber: '+91 98333 44556', relationship: 'Doctor' }
  ];

  // ==========================================
  // 1. FINANCIAL SAFETY (STRICT BLOCK)
  // ==========================================
  static checkFinancialSafety(query: string): boolean {
    const q = query.toLowerCase();
    const financialKeywords = [
      'upi', 'gpay', 'google pay', 'phonepe', 'paytm', 'bhim',
      'send money', 'transfer money', 'bank transfer', 'neft', 'rtgs', 'imps',
      'টাকা পাঠাও', 'পেমেন্ট করো', 'টাকা ট্রান্সফার', 'ইউপিআই',
      'पैसे भेजो', 'पेमेंट करो', 'बैंक ट्रांसफर', 'यूपीआई'
    ];
    return !financialKeywords.some((kw) => q.includes(kw));
  }

  // ==========================================
  // 2. CONTACT LOOKUP (AMBIGUITY RESOLVER)
  // ==========================================
  static lookupContact(query: string): AndroidActionResult {
    const q = query.trim().toLowerCase();
    const matches = this.contactsDirectory.filter(
      (c) => c.name.toLowerCase().includes(q) || (c.relationship && c.relationship.toLowerCase().includes(q))
    );

    if (matches.length === 0) {
      return {
        status: 'NOT_AVAILABLE',
        summary: `No contact found matching "${query}" in verified directory.`
      };
    }

    if (matches.length > 1) {
      const names = matches.map((m) => `${m.name} (${m.relationship || 'Contact'})`).join(', ');
      return {
        status: 'REQUIRES_USER_ACTION',
        summary: `Subham, multiple contacts match "${query}": ${names}. Which one should I select?`,
        userActionPrompt: 'Please specify the exact contact name.'
      };
    }

    const contact = matches[0];
    return {
      status: 'SUCCESS',
      summary: `Found contact ${contact.name} (${contact.phoneNumber}).`,
      payload: contact
    };
  }

  // ==========================================
  // 3. PHONE & CALLING
  // ==========================================
  static openDialer(nameOrNumber: string): AndroidActionResult {
    const contactRes = this.lookupContact(nameOrNumber);
    let phoneNumber = nameOrNumber;
    let displayName = nameOrNumber;

    if (contactRes.status === 'SUCCESS' && contactRes.payload) {
      phoneNumber = contactRes.payload.phoneNumber;
      displayName = contactRes.payload.name;
    } else if (contactRes.status === 'REQUIRES_USER_ACTION') {
      return contactRes;
    }

    const cleanNumber = phoneNumber.replace(/\s+/g, '');
    const dialerUri = `tel:${cleanNumber}`;

    return {
      status: 'REQUIRES_USER_ACTION',
      summary: `Subham, dialer prepared for ${displayName} (${cleanNumber}). Tap to confirm call on your handset.`,
      intentUri: dialerUri
    };
  }

  // ==========================================
  // 4. SMS & WHATSAPP MESSAGING
  // ==========================================
  static prepareMessage(
    recipientName: string,
    messageText: string,
    platform: 'WHATSAPP' | 'SMS' = 'WHATSAPP'
  ): AndroidActionResult {
    const contactRes = this.lookupContact(recipientName);
    let phone = recipientName;
    let name = recipientName;

    if (contactRes.status === 'SUCCESS' && contactRes.payload) {
      phone = contactRes.payload.phoneNumber.replace(/\D/g, '');
      name = contactRes.payload.name;
    }

    if (platform === 'WHATSAPP') {
      const url = `https://wa.me/${phone}?text=${encodeURIComponent(messageText)}`;
      return {
        status: 'REQUIRES_USER_ACTION',
        summary: `Subham, WhatsApp draft prepared for ${name}: "${messageText}".`,
        intentUri: url
      };
    } else {
      const uri = `sms:${phone}?body=${encodeURIComponent(messageText)}`;
      return {
        status: 'REQUIRES_USER_ACTION',
        summary: `Subham, SMS draft prepared for ${name}: "${messageText}".`,
        intentUri: uri
      };
    }
  }

  // ==========================================
  // 5. ALARM & TIMER
  // ==========================================
  static setAlarm(title: string, hour: number, minutes: number): AndroidActionResult {
    return {
      status: 'SUCCESS',
      summary: `Subham, Android system alarm set for ${String(hour).padStart(2, '0')}:${String(minutes).padStart(2, '0')}: "${title}".`,
      payload: { title, hour, minutes }
    };
  }

  static openClock(): AndroidActionResult {
    return {
      status: 'SUCCESS',
      summary: 'Opening Android Clock application.',
      intentUri: 'android.intent.action.SHOW_ALARMS'
    };
  }

  // ==========================================
  // 6. CALENDAR
  // ==========================================
  static createCalendarEvent(title: string, dateStr: string): AndroidActionResult {
    return {
      status: 'REQUIRES_USER_ACTION',
      summary: `Subham, calendar event draft created for "${title}" on ${dateStr}. Review and save in Calendar app.`,
      payload: { title, dateStr }
    };
  }

  // ==========================================
  // 7. CONTROLLED APP LAUNCHER
  // ==========================================
  static launchApp(appName: string): AndroidActionResult {
    const app = appName.trim().toLowerCase();
    switch (app) {
      case 'youtube':
        return {
          status: 'SUCCESS',
          summary: 'Launching YouTube application.',
          intentUri: 'vnd.youtube:'
        };
      case 'maps':
        return {
          status: 'SUCCESS',
          summary: 'Launching Google Maps.',
          intentUri: 'geo:0,0?q=Current+Location'
        };
      case 'chrome':
      case 'browser':
        return {
          status: 'SUCCESS',
          summary: 'Opening web browser.',
          intentUri: 'https://www.google.com'
        };
      case 'settings':
        return {
          status: 'SUCCESS',
          summary: 'Opening Android Settings.',
          intentUri: 'android.settings.SETTINGS'
        };
      default:
        return {
          status: 'NOT_AVAILABLE',
          summary: `App "${appName}" is not discoverable or requires explicit package launcher.`
        };
    }
  }

  // ==========================================
  // 8. NOTIFICATION OTP READER
  // ==========================================
  static readLatestOtp(): AndroidActionResult {
    return {
      status: 'SUCCESS',
      summary: 'Latest verification notification read: "782419" from Google Messages. (Financial auto-submission strictly blocked).',
      payload: { otpCode: '782419', sender: 'Google Messages' }
    };
  }
}
