export interface ResolvedEntities {
  person?: string;
  dateTimeText?: string;
  parsedTimestamp?: number;
  location?: string;
  appName?: string;
  targetQuery?: string;
  isAmbiguous: boolean;
  clarificationPrompt?: string;
}

export class EntityResolver {
  private static readonly RELATIVE_PEOPLE_MAP: Record<string, string> = {
    'her': 'Shubhrata',
    'him': 'Rahul Sen',
    'dad': 'Baba (Dad)',
    'বাবা': 'Baba (Dad)',
    'baba': 'Baba (Dad)',
    'doctor': 'Dr. Das (Clinic)',
    'shubhrata': 'Shubhrata',
    'শুভ্রতা': 'Shubhrata',
    'friend': 'Shubhrata',
    'বন্ধু': 'Shubhrata',
    'दोस्त': 'Shubhrata'
  };

  /**
   * Resolves entities using conversation context and query text.
   */
  static resolveEntities(
    queryText: string,
    activeContextEntity?: string
  ): ResolvedEntities {
    const text = queryText.toLowerCase();
    let person: string | undefined = undefined;
    let dateTimeText: string | undefined = undefined;
    let parsedTimestamp: number | undefined = undefined;
    let location: string | undefined = undefined;
    let appName: string | undefined = undefined;
    let isAmbiguous = false;
    let clarificationPrompt: string | undefined = undefined;

    // 1. Resolve Person
    for (const [trigger, resolvedName] of Object.entries(this.RELATIVE_PEOPLE_MAP)) {
      if (text.includes(trigger)) {
        person = resolvedName;
        break;
      }
    }

    if (!person && activeContextEntity) {
      if (text.includes('her') || text.includes('him') || text.includes('ওকে') || text.includes('তাকে') || text.includes('उसे')) {
        person = activeContextEntity;
      }
    }

    // 2. Resolve App Name
    if (text.includes('whatsapp') || text.includes('হোয়াটসঅ্যাপ')) appName = 'WhatsApp';
    else if (text.includes('telegram') || text.includes('টেলিগ্রাম')) appName = 'Telegram';
    else if (text.includes('youtube') || text.includes('ইউটিউব')) appName = 'YouTube';
    else if (text.includes('map') || text.includes('ম্যাপ')) appName = 'Google Maps';
    else if (text.includes('dialer') || text.includes('ফোন')) appName = 'Phone Dialer';

    // 3. Resolve Location
    if (text.includes('kolkata') || text.includes('কলকাতা')) location = 'Kolkata';
    else if (text.includes('delhi') || text.includes('দিল্লি')) location = 'Delhi';
    else if (text.includes('mumbai') || text.includes('মুম্বাই')) location = 'Mumbai';
    else if (text.includes('bangalore') || text.includes('বেঙ্গালুরু')) location = 'Bangalore';

    // 4. Resolve Date & Time
    const now = new Date();
    if (text.includes('tomorrow') || text.includes('কাল') || text.includes('আগামীকাল') || text.includes('कल')) {
      const tomorrow = new Date(now.getTime() + 86400000);
      dateTimeText = 'Tomorrow';
      parsedTimestamp = tomorrow.getTime();
    } else if (text.includes('today') || text.includes('আজ') || text.includes('आज')) {
      dateTimeText = 'Today';
      parsedTimestamp = now.getTime();
    }

    // Time parsing (e.g. "10 AM", "সন্ধ্যা ৭টা", "রাত ৯টায়", "after 30 minutes")
    const timeMatch = queryText.match(/(\d{1,2})(?::(\d{2}))?\s*(am|pm|টা|बजे)?/i);
    if (timeMatch) {
      let hours = parseInt(timeMatch[1], 10);
      const isPm = /pm|সন্ধ্যা|রাত|শাম/i.test(queryText);
      if (isPm && hours < 12) hours += 12;
      dateTimeText = (dateTimeText ? `${dateTimeText} at ` : 'At ') + `${hours}:00`;
    }

    return {
      person,
      dateTimeText,
      parsedTimestamp,
      location,
      appName,
      targetQuery: queryText,
      isAmbiguous,
      clarificationPrompt
    };
  }
}
