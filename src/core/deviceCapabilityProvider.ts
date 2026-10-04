import { CapabilityStatus } from '../types';

export interface ContactItem {
  id: string;
  displayName: string;
  phoneNumber: string;
  relationship?: string; // e.g. "Friend", "Father", "Colleague"
  email?: string;
}

export interface PreparedCallAction {
  type: 'CALL_NOW' | 'OPEN_DIALER' | 'LOOKUP_CALL_HISTORY' | 'UNAVAILABLE';
  targetContact?: ContactItem;
  phoneNumber: string;
  status: CapabilityStatus;
  userExplanation: string;
  dialerUri?: string;
}

export interface PreparedMessageAction {
  type: 'MESSAGE_DRAFT' | 'OPEN_APP' | 'SEND_MESSAGE_IF_NATIVE_CAPABILITY_EXISTS' | 'UNAVAILABLE';
  platform: 'SMS' | 'WHATSAPP' | 'TELEGRAM';
  recipient: string;
  messageText: string;
  status: CapabilityStatus;
  userExplanation: string;
  launchUri?: string;
}

export interface NotificationItem {
  id: string;
  packageName: string;
  appName: string;
  title: string;
  bodyText: string;
  timestamp: number;
  containsOtp: boolean;
  extractedOtpCode?: string;
}

export class DeviceCapabilityProvider {
  // Built-in verified contacts registry for Subham
  private static contactsRegistry: ContactItem[] = [
    {
      id: 'c_shubhrata',
      displayName: 'Shubhrata',
      phoneNumber: '+91 98301 24567',
      relationship: 'Friend'
    },
    {
      id: 'c_dad',
      displayName: 'Baba (Dad)',
      phoneNumber: '+91 98310 98765',
      relationship: 'Father'
    },
    {
      id: 'c_rahul',
      displayName: 'Rahul Sen',
      phoneNumber: '+91 98322 11223',
      relationship: 'Colleague'
    },
    {
      id: 'c_dr_das',
      displayName: 'Dr. Das (Clinic)',
      phoneNumber: '+91 98333 44556',
      relationship: 'Doctor'
    }
  ];

  // Simulated device notification inbox for OTP inspection
  private static recentNotifications: NotificationItem[] = [
    {
      id: 'notif_1',
      packageName: 'com.google.android.apps.messaging',
      appName: 'Messages',
      title: 'Google Verification',
      bodyText: 'Your verification security code is 782419. Do not share this with anyone.',
      timestamp: Date.now() - 120000,
      containsOtp: true,
      extractedOtpCode: '782419'
    },
    {
      id: 'notif_2',
      packageName: 'com.amazon.mShop.android.shopping',
      appName: 'Amazon',
      title: 'Order Update',
      bodyText: 'Your package is out for delivery today.',
      timestamp: Date.now() - 360000,
      containsOtp: false
    }
  ];

  /**
   * Search contacts safely. Handles duplicates and ambiguity.
   */
  static searchContacts(query: string): { matches: ContactItem[]; isAmbiguous: boolean; clarificationNeeded?: string } {
    const clean = query.trim().toLowerCase();
    const matches = this.contactsRegistry.filter(
      (c) =>
        c.displayName.toLowerCase().includes(clean) ||
        (c.relationship && c.relationship.toLowerCase().includes(clean))
    );

    if (matches.length > 1) {
      const names = matches.map((m) => `${m.displayName} (${m.relationship || 'Contact'})`).join(', ');
      return {
        matches,
        isAmbiguous: true,
        clarificationNeeded: `Subham, I found multiple contacts matching "${query}": ${names}. Which person would you like to select?`
      };
    }

    return {
      matches,
      isAmbiguous: false
    };
  }

  /**
   * Prepares a phone call action honestly distinguishing browser vs Android native bridge.
   */
  static prepareCall(nameOrNumber: string): PreparedCallAction {
    const { matches, isAmbiguous, clarificationNeeded } = this.searchContacts(nameOrNumber);

    if (isAmbiguous) {
      return {
        type: 'UNAVAILABLE',
        phoneNumber: nameOrNumber,
        status: 'REQUIRES_PERMISSION',
        userExplanation: clarificationNeeded || 'Ambiguous contact match.'
      };
    }

    const contact = matches[0];
    const phoneNumber = contact ? contact.phoneNumber : nameOrNumber;
    const isBrowser = typeof window !== 'undefined';

    // In web preview container, direct silent telephony calls cannot be made without native TelephonyManager bridge.
    // Anin honestly generates a dialer tel: intent and prepares the native bridge structure.
    return {
      type: 'OPEN_DIALER',
      targetContact: contact,
      phoneNumber,
      status: 'REQUIRES_NATIVE_BRIDGE',
      userExplanation: `Subham, in this environment I cannot place a direct cellular call automatically. I have prepared the dialer action for ${
        contact ? contact.displayName : phoneNumber
      } (${phoneNumber}). On your physical iQOO Neo 10R device, the native bridge will launch TelecomManager directly.`,
      dialerUri: `tel:${phoneNumber.replace(/\s+/g, '')}`
    };
  }

  /**
   * Prepares an SMS or messaging action honestly.
   */
  static prepareMessage(nameOrNumber: string, messageText: string, platform: 'SMS' | 'WHATSAPP' | 'TELEGRAM' = 'WHATSAPP'): PreparedMessageAction {
    const { matches } = this.searchContacts(nameOrNumber);
    const recipientName = matches.length > 0 ? matches[0].displayName : nameOrNumber;
    const recipientPhone = matches.length > 0 ? matches[0].phoneNumber.replace(/\D/g, '') : '';

    let launchUri: string | undefined = undefined;
    if (platform === 'WHATSAPP') {
      launchUri = `https://wa.me/${recipientPhone}?text=${encodeURIComponent(messageText)}`;
    } else if (platform === 'SMS') {
      launchUri = `sms:${recipientPhone}?body=${encodeURIComponent(messageText)}`;
    }

    return {
      type: 'MESSAGE_DRAFT',
      platform,
      recipient: recipientName,
      messageText,
      status: 'REQUIRES_NATIVE_BRIDGE',
      userExplanation: `Subham, I have drafted your ${platform} message to ${recipientName}: "${messageText}". Because we are running in the AI Studio environment, the message is ready to launch via native intent and has not been silently dispatched without your review.`,
      launchUri
    };
  }

  /**
   * Reads notification OTP safely. Strictly refuses any automated transfer/payment execution.
   */
  static readLatestOtp(serviceName?: string): {
    status: CapabilityStatus;
    otpFound: boolean;
    otpCode?: string;
    senderApp?: string;
    message: string;
  } {
    const otpNotifs = this.recentNotifications.filter((n) => n.containsOtp && n.extractedOtpCode);

    if (otpNotifs.length === 0) {
      return {
        status: 'AVAILABLE',
        otpFound: false,
        message: 'No recent OTP or verification code notifications detected.'
      };
    }

    const latest = otpNotifs[0];
    return {
      status: 'AVAILABLE',
      otpFound: true,
      otpCode: latest.extractedOtpCode,
      senderApp: latest.appName,
      message: `Subham, your latest verification code from ${latest.appName} is ${latest.extractedOtpCode}. As per security rules, Anin cannot auto-submit this code into any payment or financial portal.`
    };
  }
}
