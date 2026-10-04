import { ActionSafetyLevel, ActionClassification } from '../types';

export interface SafetyCheckResult {
  allowed: boolean;
  level: ActionSafetyLevel;
  requiresConfirmation: boolean;
  refusalReason?: string;
  suggestedManualAction?: string;
}

export class ActionSafetyGate {
  private static readonly FINANCIAL_PATTERNS = [
    /\b(upi|gpay|google pay|phonepe|paytm|bhim)\b/i,
    /\b(send|transfer|pay|send money|pay money|taka pathao|paise bhejo)\s+(\d+|rs|inr|rupees|টাকা|रुपये)/i,
    /\b(bank transfer|neft|rtgs|imps|wire transfer)\b/i,
    /\b(credit card|debit card|cvv|pin code|atm pin)\b/i,
    /\b(approve payment|approve transaction|confirm transfer)\b/i,
    /\b(টাকা পাঠাও|পেমেন্ট করো|টাকা ট্রান্সফার|ইউপিআই)\b/i,
    /\b(पैसे भेजो|पेमेंट करो|बैंक ट्रांसफर|यूपीआई)\b/i
  ];

  /**
   * Evaluates any user query or planned tool invocation for strict financial & security safety.
   */
  static evaluateSafety(intentName: string, queryText: string, parameters?: Record<string, any>): SafetyCheckResult {
    const text = queryText.toLowerCase();

    // Check for hard-prohibited Level 3 financial and credential operations
    for (const pattern of this.FINANCIAL_PATTERNS) {
      if (pattern.test(text)) {
        return {
          allowed: false,
          level: ActionSafetyLevel.LEVEL_3_HIGH_RISK_PROHIBITED,
          requiresConfirmation: false,
          refusalReason:
            'Strict Financial Safety Boundary: Anin is strictly prohibited from executing UPI payments, bank transfers, financial transactions, or automated OTP approvals.',
          suggestedManualAction:
            'Please open your verified banking or UPI app (e.g. PhonePe, GPay, or NetBanking) to review and perform financial transfers manually.'
        };
      }
    }

    // Check specific intent safety
    switch (intentName) {
      // Level 0: Read-only operations (zero risk)
      case 'web_search':
      case 'get_device_status':
      case 'get_battery':
      case 'search_memory':
      case 'list_reminders':
      case 'calendar_lookup':
      case 'search_conversation_history':
        return {
          allowed: true,
          level: ActionSafetyLevel.LEVEL_0_READ_ONLY,
          requiresConfirmation: false
        };

      // Level 1: Local reversible actions (safe with undo)
      case 'save_note':
      case 'create_reminder':
      case 'cancel_reminder':
      case 'update_preference':
        return {
          allowed: true,
          level: ActionSafetyLevel.LEVEL_1_LOCAL_REVERSIBLE,
          requiresConfirmation: false
        };

      // Level 2: External side-effects (requires permission/confirmation)
      case 'prepare_call':
      case 'prepare_message':
      case 'calendar_create_event':
      case 'delete_memory':
      case 'clear_all_memories':
      case 'wipe_biometrics':
        return {
          allowed: true,
          level: ActionSafetyLevel.LEVEL_2_EXTERNAL_SIDE_EFFECT,
          requiresConfirmation: intentName.startsWith('delete_') || intentName.startsWith('clear_') || intentName.startsWith('wipe_')
        };

      // Level 3: Prohibited
      case 'financial_transfer':
      case 'approve_transaction':
      case 'submit_otp_transaction':
        return {
          allowed: false,
          level: ActionSafetyLevel.LEVEL_3_HIGH_RISK_PROHIBITED,
          requiresConfirmation: false,
          refusalReason: 'Financial transactions cannot be automated by Anin.'
        };

      default:
        return {
          allowed: true,
          level: ActionSafetyLevel.LEVEL_0_READ_ONLY,
          requiresConfirmation: false
        };
    }
  }
}
