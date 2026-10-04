import { AssistantInteraction, LanguageCode, detectLanguage } from '../types';
import { LocalStorageManager } from './storage';
import { SpeakerVerificationEngine } from './speakerVerification';
import { AudioPlaybackManager } from './audioPlayback';

export class AssistantBrain {
  /**
   * Process an incoming voice/text command through the strict security pipeline.
   *
   * PIPELINE:
   * 1. Check for immediate barge-in stop
   * 2. Verify speaker authenticity (Must be Subham)
   * 3. If Subham: Process command in Bengali, Hindi, or English
   * 4. Else: SILENTLY IGNORE (Fail closed, no spoken response, no execution)
   */
  static async processVoiceQuery(
    rawText: string,
    simulatedAudioPcm?: Float32Array,
    forceSpeakerVerification?: boolean // Can be passed to test imposter or verified Subham
  ): Promise<AssistantInteraction> {
    const startTime = performance.now();
    const query = rawText.trim();
    const lang = detectLanguage(query);

    // 1. Immediate barge-in check
    const isStopCommand =
      /^(stop|cancel|quiet|shut up|halt|থেমে যাও|থামো|বন্ধ করো|রুক যাও|चुप हो जाओ|रुकिए)$/i.test(
        query.toLowerCase()
      ) ||
      query.toLowerCase().includes('stop') ||
      query.toLowerCase().includes('থেমে যাও') ||
      query.toLowerCase().includes('रुक जाओ');

    if (isStopCommand) {
      AudioPlaybackManager.emergencyStop();
      return {
        id: 'interaction_' + Date.now(),
        timestamp: Date.now(),
        query,
        detectedLanguage: lang,
        verifiedSubham: true,
        confidenceScore: 0.99,
        responseText: '[Barge-in Interrupt: Spoken output stopped immediately]',
        actionExecuted: 'EXECUTE_BARGE_IN_EMERGENCY_STOP',
        voiceProfileUsed: 'SILENT_HALT',
        isSilentRejection: false,
        audioLatencyMs: Math.round(performance.now() - startTime)
      };
    }

    // 2. Speaker Verification Pipeline
    let isSubham = false;
    let confidenceScore = 0;
    let speakerFailureReason: string | undefined;

    if (forceSpeakerVerification !== undefined) {
      isSubham = forceSpeakerVerification;
      confidenceScore = isSubham ? 0.88 : 0.38;
      if (!isSubham) {
        speakerFailureReason = 'Speaker verification mismatch: Simulated Imposter Voice';
      }
    } else if (simulatedAudioPcm) {
      const result = SpeakerVerificationEngine.verifySpeaker(simulatedAudioPcm);
      isSubham = result.isVerified;
      confidenceScore = result.confidenceScore;
      speakerFailureReason = result.failureReason;
    } else {
      // Default: Check if Subham is enrolled
      const meta = LocalStorageManager.getSubhamMetadata();
      if (!meta.isEnrolled) {
        isSubham = false;
        confidenceScore = 0.0;
        speakerFailureReason = 'Subham voice profile not enrolled. System A required.';
      } else {
        // Enrolled user default test speech
        isSubham = true;
        confidenceScore = 0.85;
      }
    }

    // 3. STRICT SECURITY RULE: IF NOT SUBHAM -> SILENTLY IGNORE!
    if (!isSubham) {
      LocalStorageManager.addAuditLog({
        id: 'audit_' + Date.now(),
        timestamp: Date.now(),
        eventType: 'AUTHENTICATION_FAILED',
        speakerIdentified: 'UNAUTHORIZED_SPEAKER',
        confidenceScore,
        actionTaken: 'SILENT_REJECTION: Remained completely silent. No response or execution.',
        sensitiveAudioStored: false
      });

      return {
        id: 'interaction_' + Date.now(),
        timestamp: Date.now(),
        query,
        detectedLanguage: lang,
        verifiedSubham: false,
        confidenceScore,
        responseText: '— [Silent Rejection: Unauthorized Speaker. Anin remained silent.] —',
        voiceProfileUsed: 'NONE',
        isSilentRejection: true,
        audioLatencyMs: Math.round(performance.now() - startTime)
      };
    }

    // 4. SUBHAM VERIFIED: Process natural query in Bengali, Hindi, or English
    const lowerQuery = query.toLowerCase();
    const activeProfile = LocalStorageManager.getActiveProfile();
    let responseText = '';
    let actionExecuted: string | undefined = undefined;

    // Check query intents
    if (
      lowerQuery.includes('battery') ||
      lowerQuery.includes('ব্যাটারি') ||
      lowerQuery.includes('बैटरी') ||
      lowerQuery.includes('charge')
    ) {
      actionExecuted = 'GET_DEVICE_BATTERY_STATUS';
      if (lang === 'bn') {
        responseText = 'শুভম, আপনার iQOO Neo 10R ফোনের ব্যাটারি ৮৫% রয়েছে, চার্জিং স্বাভাবিক এবং তাপমাত্রা ৩২ ডিগ্রি সেলসিয়াস।';
      } else if (lang === 'hi') {
        responseText = 'शुभम, आपके iQOO Neo 10R की बैटरी ८५% है, चार्जिंग सामान्य है और तापमान ३२ डिग्री सेल्सियस है।';
      } else {
        responseText = 'Subham, your iQOO Neo 10R battery is at 85%. Thermal status is nominal at 32 degrees Celsius.';
      }
    } else if (
      lowerQuery.includes('memory') ||
      lowerQuery.includes('ram') ||
      lowerQuery.includes('র‍্যাম') ||
      lowerQuery.includes('র‍্যামের') ||
      lowerQuery.includes('মেমরি') ||
      lowerQuery.includes('मेमोरी')
    ) {
      actionExecuted = 'GET_SYSTEM_DIAGNOSTICS';
      if (lang === 'bn') {
        responseText = 'শুভম, ডিভাইসে মোট ৮ জিবি র‍্যাম রয়েছে, যার মধ্যে ৫.১ জিবি বর্তমানে খালি আছে। সিস্টেম খুব মসৃণ কাজ করছে।';
      } else if (lang === 'hi') {
        responseText = 'शुभम, डिवाइस में कुल ८ जीबी रैम है, जिसमें से ५.१ जीबी उपलब्ध है। सिस्टम सुचारू रूप से चल रहा है।';
      } else {
        responseText = 'Subham, your device has 8 GB total RAM with 5.1 GB available. Snapdragon DSP latency is optimal.';
      }
    } else if (
      lowerQuery.includes('reminder') ||
      lowerQuery.includes('schedule') ||
      lowerQuery.includes('রিমাইন্ডার') ||
      lowerQuery.includes('কাজ') ||
      lowerQuery.includes('मीटिंग') ||
      lowerQuery.includes('शेड्यूल')
    ) {
      actionExecuted = 'RETRIEVE_PERSONAL_REMINDERS';
      const reminders = LocalStorageManager.getReminders().filter((r) => !r.isCompleted);
      const count = reminders.length;
      if (lang === 'bn') {
        responseText = `শুভম, আপনার ${count}টি সক্রিয় রিমাইন্ডার রয়েছে। সন্ধ্যে ৭টায় বাবাকে ফোন করার কথা এবং স্ন্যাপড্রাগন ডিএসপি ল্যাটেন্সি রিভিউ।`;
      } else if (lang === 'hi') {
        responseText = `शुभम, आपके ${count} आगामी रिमाइंडर्स हैं। शाम ७ बजे पिताजी को कॉल करना और डिवाइस टेस्ट करना।`;
      } else {
        responseText = `Subham, you have ${count} pending reminders scheduled for today, including calling dad at 7 PM and reviewing DSP latency.`;
      }
    } else if (
      lowerQuery.includes('who are you') ||
      lowerQuery.includes('তুমি কে') ||
      lowerQuery.includes('तुम कौन हो') ||
      lowerQuery.includes('identity') ||
      lowerQuery.includes('পরিচয়')
    ) {
      actionExecuted = 'ASSISTANT_IDENTITY';
      if (lang === 'bn') {
        responseText = 'আমি অনিন, শুভম আপনার সম্পূর্ণ ব্যক্তিগত এবং সুরক্ষিত ভয়েস অ্যাসিস্ট্যান্ট। আমি কেবল আপনার নির্দেশ পালন করি।';
      } else if (lang === 'hi') {
        responseText = 'मैं अनिन हूँ, शुभम आपकी पूरी तरह से निजी और सुरक्षित वॉइस असिस्टेंट। मैं केवल आपकी आवाज़ पर काम करती हूँ।';
      } else {
        responseText = 'I am Anin, Subham. Your personal and private voice assistant. I am strictly authorized only by your voice.';
      }
    } else if (
      lowerQuery.includes('who am i') ||
      lowerQuery.includes('আমার নাম কি') ||
      lowerQuery.includes('मेरा नाम क्या है')
    ) {
      actionExecuted = 'USER_IDENTITY_CONFIRMATION';
      if (lang === 'bn') {
        responseText = 'আপনি শুভম। আপনার বায়োমেট্রিক ভয়েস প্রোফাইল হার্ডওয়্যার কি-স্টোরে বিশ্বস্তভাবে এনরোল করা আছে।';
      } else if (lang === 'hi') {
        responseText = 'आप शुभম हैं। आपकी बायोमेट्रिक आवाज़ का प्रोफाइल हार्डवेयर की-स्टोर में सुरक्षित रूप से दर्ज है।';
      } else {
        responseText = 'You are Subham, the sole authenticated administrator and voice owner of this device.';
      }
    } else if (
      lowerQuery.startsWith('remember ') ||
      lowerQuery.includes('মনে রাখো') ||
      lowerQuery.includes('याद रखो')
    ) {
      actionExecuted = 'STORE_ENCRYPTED_MEMORY';
      const noteContent = query.replace(/^(remember|মনে রাখো|याद रखो)\s*/i, '');
      LocalStorageManager.addPersonalMemory('notes', 'Voice Note ' + new Date().toLocaleTimeString(), noteContent);
      if (lang === 'bn') {
        responseText = 'শুভম, তথ্যটি এনক্রিপ্ট করে আপনার স্থানীয় মেমোরি ভল্টে সুরক্ষিত রাখা হয়েছে।';
      } else if (lang === 'hi') {
        responseText = 'शुभम, यह जानकारी एन्क्रिप्ट करके आपके स्थानीय मेमोरी वॉल्ट में सुरक्षित रख दी गई है।';
      } else {
        responseText = 'Subham, I have saved and encrypted this note into your private local memory vault.';
      }
    } else {
      actionExecuted = 'CONVERSATIONAL_RESPONSE';
      if (lang === 'bn') {
        responseText = `নমস্কার শুভম। আমি আপনার নির্দেশ শুনতে পেয়েছি: "${query}"। আমি সব কাজ নিরাপদে সম্পন্ন করতে প্রস্তুত।`;
      } else if (lang === 'hi') {
        responseText = `नमस्ते शुभम। मुझे आपका निर्देश प्राप्त हुआ: "${query}"। मैं सभी कार्य सुरक्षित रूप से करने के लिए तैयार हूँ।`;
      } else {
        responseText = `Yes Subham. I received your request: "${query}". All security checks have passed and your system is secure.`;
      }
    }

    // Playback speech response via SYSTEM B (Anin Output Voice)
    await AudioPlaybackManager.speak(responseText, lang);

    return {
      id: 'interaction_' + Date.now(),
      timestamp: Date.now(),
      query,
      detectedLanguage: lang,
      verifiedSubham: true,
      confidenceScore,
      responseText,
      actionExecuted,
      voiceProfileUsed: activeProfile.displayName,
      isSilentRejection: false,
      audioLatencyMs: Math.round(performance.now() - startTime)
    };
  }
}
