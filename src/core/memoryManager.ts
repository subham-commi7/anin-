import { MemoryCategory, PersonalMemoryEntity, MEMORY_CATEGORIES } from '../types';
import { LocalStorageManager, CryptoVault } from './storage';

export interface MemorySearchResult {
  item: PersonalMemoryEntity;
  decryptedValue: string;
  relevanceScore: number;
  categoryLabel: string;
}

export class MemoryManager {
  private static isMemoryEnabled: boolean = true;

  static setMemoryEnabled(enabled: boolean): void {
    this.isMemoryEnabled = enabled;
  }

  static getIsMemoryEnabled(): boolean {
    return this.isMemoryEnabled;
  }

  /**
   * Internal tool: save_note(text)
   * Validates text, classifies category, stores securely into encrypted vault, avoids duplicates.
   */
  static saveNote(text: string, categoryOverride?: MemoryCategory): { success: boolean; memoryId?: string; message: string; category: MemoryCategory } {
    if (!this.isMemoryEnabled) {
      return {
        success: false,
        message: 'Personal memory system is currently disabled by user setting.',
        category: 'IMPORTANT_FACTS'
      };
    }

    const cleanText = text.trim();
    if (cleanText.length < 3) {
      return {
        success: false,
        message: 'Memory text too short. Please provide clear details.',
        category: 'IMPORTANT_FACTS'
      };
    }

    const category = categoryOverride || this.classifyMemoryCategory(cleanText);
    const key = this.generateMemoryKey(cleanText, category);

    // Duplicate check
    const existing = LocalStorageManager.getPersonalMemories();
    const isDuplicate = existing.some((m) => {
      const decrypted = CryptoVault.decrypt(m.encryptedValue);
      return decrypted.toLowerCase() === cleanText.toLowerCase();
    });

    if (isDuplicate) {
      return {
        success: true,
        message: `Subham, this information is already recorded in your ${category} memory vault.`,
        category
      };
    }

    const newMem: PersonalMemoryEntity = {
      id: 'mem_' + Date.now() + '_' + Math.random().toString(36).substring(2, 6),
      category,
      key,
      encryptedValue: CryptoVault.encrypt(cleanText),
      rawPreview: cleanText.length > 35 ? cleanText.substring(0, 35) + '...' : cleanText,
      createdAt: Date.now(),
      lastAccessedAt: Date.now(),
      requiresBiometricAuth: true
    };

    existing.unshift(newMem);
    LocalStorageManager.savePersonalMemories(existing);

    return {
      success: true,
      memoryId: newMem.id,
      message: `Subham, I have saved this note under ${category} in your encrypted memory vault.`,
      category
    };
  }

  /**
   * Internal tool: search_conversation_history(query, days_back?)
   * Searches locally available conversation/history data and returns matches.
   */
  static searchConversationHistory(query: string, daysBack: number = 7): Array<{ id: string; query: string; response: string; timestamp: number }> {
    const raw = localStorage.getItem('anin_conversation_history');
    if (!raw) return [];
    try {
      const history = JSON.parse(raw) as Array<{ id: string; query: string; response: string; timestamp: number }>;
      const cutoff = Date.now() - daysBack * 86400000;
      const clean = query.toLowerCase();

      return history.filter(
        (h) => h.timestamp >= cutoff && (h.query.toLowerCase().includes(clean) || h.response.toLowerCase().includes(clean))
      );
    } catch {
      return [];
    }
  }

  /**
   * Search structured memory across all 11 categories.
   */
  static searchMemory(query: string, categoryFilter?: MemoryCategory): MemorySearchResult[] {
    const all = LocalStorageManager.getPersonalMemories();
    const cleanQuery = query.toLowerCase().trim();
    const words = cleanQuery.split(/\s+/).filter((w) => w.length > 1);

    const results: MemorySearchResult[] = [];

    for (const mem of all) {
      if (categoryFilter && mem.category !== categoryFilter) continue;

      const plain = CryptoVault.decrypt(mem.encryptedValue).toLowerCase();
      const keyLower = mem.key.toLowerCase();

      let score = 0;
      if (plain.includes(cleanQuery) || keyLower.includes(cleanQuery)) {
        score += 1.0;
      }
      for (const w of words) {
        if (plain.includes(w)) score += 0.35;
        if (keyLower.includes(w)) score += 0.5;
      }

      if (score > 0) {
        const catConfig = MEMORY_CATEGORIES.find((c) => c.id === mem.category);
        results.push({
          item: mem,
          decryptedValue: CryptoVault.decrypt(mem.encryptedValue),
          relevanceScore: Math.min(1.0, score),
          categoryLabel: catConfig ? catConfig.label : mem.category
        });
      }
    }

    results.sort((a, b) => b.relevanceScore - a.relevanceScore);
    return results;
  }

  /**
   * Delete specific memory by key or ID.
   */
  static deleteMemory(keyOrId: string): boolean {
    const existing = LocalStorageManager.getPersonalMemories();
    const clean = keyOrId.toLowerCase().trim();
    const initialLen = existing.length;

    const filtered = existing.filter(
      (m) => m.id !== keyOrId && m.key.toLowerCase() !== clean && !CryptoVault.decrypt(m.encryptedValue).toLowerCase().includes(clean)
    );

    if (filtered.length < initialLen) {
      LocalStorageManager.savePersonalMemories(filtered);
      return true;
    }
    return false;
  }

  /**
   * Clear all memories.
   */
  static clearAllMemories(): void {
    LocalStorageManager.savePersonalMemories([]);
  }

  /**
   * Detect memory command intent from natural text.
   */
  static parseMemoryIntent(text: string): {
    isMemoryCommand: boolean;
    operation?: 'SAVE' | 'READ' | 'DELETE' | 'CLEAR';
    targetText?: string;
  } {
    const t = text.trim();
    const lower = t.toLowerCase();

    // CLEAR ALL
    if (
      lower.includes('delete all my memories') ||
      lower.includes('clear all memories') ||
      lower.includes('সব মেমরি মুছে ফেলো') ||
      lower.includes('सारी मेमोरी मिटा दो')
    ) {
      return { isMemoryCommand: true, operation: 'CLEAR' };
    }

    // READ ALL / INQUIRE
    if (
      lower.includes('what do you remember about me') ||
      lower.includes('what do you know about me') ||
      lower.includes('আমার সম্পর্কে কি জানো') ||
      lower.includes('मेरे बारे में क्या याद है') ||
      lower.includes('আমার কি কি মনে রেখেছো')
    ) {
      return { isMemoryCommand: true, operation: 'READ' };
    }

    // FORGET / DELETE SPECIFIC
    const forgetMatch = t.match(/^(?:forget|delete what i told you about|remove memory|ভুলে যাও|মুছে ফেলো|भूल जाओ|हटा दो)\s+(.+)/i);
    if (forgetMatch && forgetMatch[1]) {
      return { isMemoryCommand: true, operation: 'DELETE', targetText: forgetMatch[1] };
    }

    // SAVE EXPLICIT
    const rememberMatch = t.match(/^(?:remember that|remember this|please remember|মনে রাখো যে|মনে রাখো|याद रखो कि|याद रखो)\s+(.+)/i);
    if (rememberMatch && rememberMatch[1]) {
      return { isMemoryCommand: true, operation: 'SAVE', targetText: rememberMatch[1] };
    }

    return { isMemoryCommand: false };
  }

  private static classifyMemoryCategory(text: string): MemoryCategory {
    const lower = text.toLowerCase();
    if (lower.includes('friend') || lower.includes('shubhrata') || lower.includes('brother') || lower.includes('baba') || lower.includes('dad') || lower.includes('bondhu') || lower.includes('dost')) {
      return 'PEOPLE';
    }
    if (lower.includes('sleep') || lower.includes('wake up') || lower.includes('routine') || lower.includes('workout') || lower.includes('gym')) {
      return 'ROUTINES';
    }
    if (lower.includes('prefer') || lower.includes('like') || lower.includes('bengali') || lower.includes('hindi') || lower.includes('favorite') || lower.includes('bhalo lage')) {
      return 'PREFERENCES';
    }
    if (lower.includes('call me') || lower.includes('my name') || lower.includes('subham')) {
      return 'USER_PROFILE';
    }
    if (lower.includes('iqoo') || lower.includes('battery') || lower.includes('snapdragon') || lower.includes('phone')) {
      return 'DEVICE_PREFERENCES';
    }
    return 'IMPORTANT_FACTS';
  }

  private static generateMemoryKey(text: string, category: MemoryCategory): string {
    const words = text.split(/\s+/).slice(0, 4).join(' ');
    return `${category}: ${words}`;
  }
}
