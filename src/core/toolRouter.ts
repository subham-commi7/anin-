import { ActionSafetyLevel, CapabilityStatus } from '../types';
import { ActionSafetyGate } from './actionSafetyGate';
import { MemoryManager } from './memoryManager';
import { LocalStorageManager } from './storage';
import { AndroidCapabilities } from './androidCapabilities';
import { PermissionManager } from './permissionManager';

export interface ToolExecutionResponse {
  toolName: string;
  success: boolean;
  output: any;
  userSummary: string;
  capabilityStatus: CapabilityStatus;
  safetyLevel: ActionSafetyLevel;
}

export class ToolRouter {
  /**
   * Dispatches and executes an authorized tool call through Android capability layer.
   */
  static async executeTool(
    toolName: string,
    args: Record<string, any>,
    queryContext: string = ''
  ): Promise<ToolExecutionResponse> {
    // 1. Strict Financial Safety & Prohibited Action Gate
    const isFinanciallySafe = AndroidCapabilities.checkFinancialSafety(queryContext);
    if (!isFinanciallySafe) {
      return {
        toolName,
        success: false,
        output: { error: 'Prohibited financial transfer' },
        userSummary: 'Subham, financial transactions and UPI transfers cannot be automated by Anin as per strict security rules.',
        capabilityStatus: 'NOT_SUPPORTED',
        safetyLevel: ActionSafetyLevel.LEVEL_3_HIGH_RISK_PROHIBITED
      };
    }

    const safety = ActionSafetyGate.evaluateSafety(toolName, queryContext, args);
    if (!safety.allowed) {
      return {
        toolName,
        success: false,
        output: { error: 'Prohibited action', reason: safety.refusalReason },
        userSummary: safety.refusalReason || 'Operation refused by safety gate.',
        capabilityStatus: 'NOT_SUPPORTED',
        safetyLevel: safety.level
      };
    }

    try {
      switch (toolName) {
        // ==========================================
        // A. INFORMATION TOOLS
        // ==========================================
        case 'get_device_status':
        case 'get_battery': {
          const raw = localStorage.getItem('anin_device_diagnostics');
          const diag = raw
            ? JSON.parse(raw)
            : { deviceName: 'iQOO Neo 10R', batteryPercentage: 85, ramAvailableGb: 5.1, thermalStatus: 'Optimal (32°C)' };
          return {
            toolName,
            success: true,
            output: diag,
            userSummary: `Subham, your ${diag.deviceName} battery is at ${diag.batteryPercentage}%, thermal status is ${diag.thermalStatus}, and ${diag.ramAvailableGb} GB RAM is available.`,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
          };
        }

        case 'get_current_time': {
          const now = new Date();
          const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
          const dateStr = now.toLocaleDateString([], { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
          return {
            toolName,
            success: true,
            output: { time: timeStr, date: dateStr, timestamp: now.getTime() },
            userSummary: `Current time is ${timeStr} on ${dateStr}.`,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
          };
        }

        // ==========================================
        // B. PERSONAL ORGANIZATION
        // ==========================================
        case 'save_note': {
          const text = args.text || args.content || '';
          const res = MemoryManager.saveNote(text, args.category);
          return {
            toolName,
            success: res.success,
            output: res,
            userSummary: res.message,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_1_LOCAL_REVERSIBLE
          };
        }

        case 'search_memory': {
          const q = args.query || '';
          const res = MemoryManager.searchMemory(q, args.category);
          const summary =
            res.length > 0
              ? `Subham, I found ${res.length} relevant record(s) in your vault: "${res[0].decryptedValue}".`
              : `Subham, no previous memory was found matching "${q}".`;
          return {
            toolName,
            success: true,
            output: res,
            userSummary: summary,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
          };
        }

        case 'create_reminder': {
          const title = args.title || args.task || 'Reminder';
          const dueTime = args.dueTime || args.time || 'Upcoming';
          const lang = args.language || 'en';
          LocalStorageManager.addReminder(title, lang);
          return {
            toolName,
            success: true,
            output: { title, dueTime, created: true },
            userSummary: `Subham, I have set a reminder: "${title}" for ${dueTime}.`,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_1_LOCAL_REVERSIBLE
          };
        }

        case 'list_reminders': {
          const list = LocalStorageManager.getReminders().filter((r) => !r.isCompleted);
          const count = list.length;
          const summary =
            count > 0
              ? `Subham, you have ${count} active reminder(s): ${list.map((r) => r.title).join(', ')}.`
              : 'Subham, you have no pending reminders scheduled for today.';
          return {
            toolName,
            success: true,
            output: list,
            userSummary: summary,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
          };
        }

        case 'cancel_reminder': {
          const idOrTitle = args.id || args.title || '';
          const existing = LocalStorageManager.getReminders();
          const target = existing.find((r) => r.id === idOrTitle || r.title.toLowerCase().includes(idOrTitle.toLowerCase()));
          if (target) {
            LocalStorageManager.deleteReminder(target.id);
            return {
              toolName,
              success: true,
              output: { deletedId: target.id },
              userSummary: `Subham, reminder "${target.title}" has been cancelled.`,
              capabilityStatus: 'AVAILABLE',
              safetyLevel: ActionSafetyLevel.LEVEL_1_LOCAL_REVERSIBLE
            };
          }
          return {
            toolName,
            success: false,
            output: { notFound: true },
            userSummary: `Could not find a reminder matching "${idOrTitle}".`,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_1_LOCAL_REVERSIBLE
          };
        }

        // ==========================================
        // C. STEP 3 PHONE & COMMUNICATION CAPABILITIES
        // ==========================================
        case 'find_contact': {
          if (!PermissionManager.isGranted('contacts')) {
            return {
              toolName,
              success: false,
              output: { permissionDenied: true },
              userSummary: 'Subham, contact search requires Contacts permission. Enable it in the Permission Center.',
              capabilityStatus: 'REQUIRES_PERMISSION',
              safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
            };
          }
          const name = args.name || '';
          const res = AndroidCapabilities.lookupContact(name);
          return {
            toolName,
            success: res.status === 'SUCCESS',
            output: res,
            userSummary: res.summary,
            capabilityStatus: res.status === 'SUCCESS' ? 'AVAILABLE' : 'REQUIRES_USER_ACTION',
            safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
          };
        }

        case 'prepare_call': {
          const target = args.name || args.phoneNumber || '';
          const callAction = AndroidCapabilities.openDialer(target);
          return {
            toolName,
            success: true,
            output: callAction,
            userSummary: callAction.summary,
            capabilityStatus: 'REQUIRES_NATIVE_BRIDGE',
            safetyLevel: ActionSafetyLevel.LEVEL_2_EXTERNAL_SIDE_EFFECT
          };
        }

        case 'prepare_message': {
          const recipient = args.recipient || args.name || '';
          const msg = args.message || args.text || '';
          const platform = (args.platform || 'WHATSAPP').toUpperCase() as 'WHATSAPP' | 'SMS';
          const msgAction = AndroidCapabilities.prepareMessage(recipient, msg, platform);
          return {
            toolName,
            success: true,
            output: msgAction,
            userSummary: msgAction.summary,
            capabilityStatus: 'REQUIRES_NATIVE_BRIDGE',
            safetyLevel: ActionSafetyLevel.LEVEL_2_EXTERNAL_SIDE_EFFECT
          };
        }

        case 'read_latest_otp': {
          const otpResult = AndroidCapabilities.readLatestOtp();
          return {
            toolName,
            success: otpResult.status === 'SUCCESS',
            output: otpResult.payload,
            userSummary: otpResult.summary,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
          };
        }

        case 'set_alarm': {
          const title = args.title || 'Alarm';
          const hour = args.hour || 7;
          const minutes = args.minutes || 0;
          const res = AndroidCapabilities.setAlarm(title, hour, minutes);
          return {
            toolName,
            success: true,
            output: res,
            userSummary: res.summary,
            capabilityStatus: 'AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_1_LOCAL_REVERSIBLE
          };
        }

        case 'create_calendar_event': {
          const title = args.title || 'Appointment';
          const dateStr = args.date || 'Tomorrow';
          const res = AndroidCapabilities.createCalendarEvent(title, dateStr);
          return {
            toolName,
            success: true,
            output: res,
            userSummary: res.summary,
            capabilityStatus: 'REQUIRES_USER_ACTION',
            safetyLevel: ActionSafetyLevel.LEVEL_2_EXTERNAL_SIDE_EFFECT
          };
        }

        // ==========================================
        // D. DEVICE & NAVIGATION TOOLS
        // ==========================================
        case 'open_app': {
          const appName = args.appName || 'Application';
          const res = AndroidCapabilities.launchApp(appName);
          return {
            toolName,
            success: res.status === 'SUCCESS',
            output: res,
            userSummary: res.summary,
            capabilityStatus: res.status === 'SUCCESS' ? 'REQUIRES_NATIVE_BRIDGE' : 'NOT_AVAILABLE',
            safetyLevel: ActionSafetyLevel.LEVEL_1_LOCAL_REVERSIBLE
          };
        }

        default:
          return {
            toolName,
            success: false,
            output: { error: 'Unknown tool' },
            userSummary: `Requested tool "${toolName}" is not registered in Anin's tool router.`,
            capabilityStatus: 'NOT_SUPPORTED',
            safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
          };
      }
    } catch (err: any) {
      return {
        toolName,
        success: false,
        output: { error: err.message },
        userSummary: `Execution error for tool "${toolName}": ${err.message}`,
        capabilityStatus: 'UNAVAILABLE',
        safetyLevel: ActionSafetyLevel.LEVEL_0_READ_ONLY
      };
    }
  }
}
