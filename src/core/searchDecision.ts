export interface SearchDecisionResult {
  shouldSearch: boolean;
  reason: string;
  suggestedQuery?: string;
  isExplicitSearch: boolean;
}

export class SearchDecisionEngine {
  private static readonly EXPLICIT_SEARCH_TRIGGERS = [
    /^(search|search the web|search internet|google|look up|find online|খোঁজ করো|অনুসন্ধান করো|सर्च करो|ढूंढो)\s+(.+)/i,
    /\b(search the internet for|look up on web|search google for)\b/i
  ];

  private static readonly TIME_SENSITIVE_TRIGGERS = [
    /\b(weather|temperature|forecast|rain|cloudy|আবহাওয়া|বৃষ্টি|मौसम|तापमान|बारिश)\b/i,
    /\b(today|tomorrow|yesterday|tonight|this week|আজকের|কালকের|आज|कल)\b/i,
    /\b(latest news|breaking news|current events|খবর|সংবাদ|समाचार|ताज़ा ख़बर)\b/i,
    /\b(stock price|market price|gold rate|bitcoin|crypto|দাম কত|भाव क्या है)\b/i,
    /\b(cricket score|match score|who won|scorecard|খেলা|ম্যাচ|স্কোর|मैच)\b/i,
    /\b(flight status|train status|traffic live|live traffic)\b/i
  ];

  private static readonly LOCAL_DEVICE_OR_OFFLINE_PATTERNS = [
    /\b(battery|charge|ram|memory|diagnostics|storage|device spec|ব্যাটারি|মেমোরি|बैटरी)\b/i,
    /\b(my name|who am i|who are you|who is anin|আমার নাম|তুমি কে|तुम कौन हो)\b/i,
    /\b(my address|my preference|my routine|my note|remember|remind me)\b/i
  ];

  /**
   * Evaluates if a query genuinely requires Google Web Search grounding.
   */
  static shouldSearchWeb(query: string): SearchDecisionResult {
    const clean = query.trim();
    const lower = clean.toLowerCase();

    // 1. Explicit search instruction from user
    for (const pattern of this.EXPLICIT_SEARCH_TRIGGERS) {
      const match = clean.match(pattern);
      if (match) {
        return {
          shouldSearch: true,
          reason: 'User explicitly requested an online web search.',
          suggestedQuery: match[2] || clean,
          isExplicitSearch: true
        };
      }
    }

    // 2. Disqualify if it's purely a local device diagnostic or personal memory query
    for (const pattern of this.LOCAL_DEVICE_OR_OFFLINE_PATTERNS) {
      if (pattern.test(lower)) {
        return {
          shouldSearch: false,
          reason: 'Handled via local device diagnostics or local encrypted memory vault.',
          isExplicitSearch: false
        };
      }
    }

    // 3. Time-sensitive topics (weather, live news, match scores, market rates)
    for (const pattern of this.TIME_SENSITIVE_TRIGGERS) {
      if (pattern.test(lower)) {
        return {
          shouldSearch: true,
          reason: 'Time-sensitive information detected (weather, news, live sports, or rates). Freshness required.',
          suggestedQuery: clean,
          isExplicitSearch: false
        };
      }
    }

    // 4. Default: Casual conversational responses and offline knowledge do not call search
    return {
      shouldSearch: false,
      reason: 'General conversational or offline knowledge does not require web search.',
      isExplicitSearch: false
    };
  }
}
