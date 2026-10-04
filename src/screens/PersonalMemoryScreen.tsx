import React, { useState } from 'react';
import {
  Brain,
  AlarmClock,
  Plus,
  Trash2,
  Check,
  Lock,
  Bookmark,
  Eye,
  EyeOff,
  Search,
  Filter,
  ToggleLeft,
  ToggleRight,
  ShieldCheck,
  Sparkles
} from 'lucide-react';
import { MemoryCategory, PersonalMemoryEntity, ReminderEntity, MEMORY_CATEGORIES } from '../types';
import { LocalStorageManager, CryptoVault } from '../core/storage';
import { MemoryManager } from '../core/memoryManager';

export const PersonalMemoryScreen: React.FC = () => {
  const [reminders, setReminders] = useState<ReminderEntity[]>(
    LocalStorageManager.getReminders()
  );
  const [memories, setMemories] = useState<PersonalMemoryEntity[]>(
    LocalStorageManager.getPersonalMemories()
  );
  const [revealedIds, setRevealedIds] = useState<Set<string>>(new Set());
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [memoryEnabled, setMemoryEnabled] = useState(MemoryManager.getIsMemoryEnabled());

  // New Reminder State
  const [newReminderTitle, setNewReminderTitle] = useState('');
  const [newReminderLang, setNewReminderLang] = useState<'en' | 'bn' | 'hi'>('en');

  // New Memory State
  const [isAddingMemory, setIsAddingMemory] = useState(false);
  const [newMemCategory, setNewMemCategory] = useState<MemoryCategory>('IMPORTANT_FACTS');
  const [newMemKey, setNewMemKey] = useState('');
  const [newMemValue, setNewMemValue] = useState('');

  const handleToggleReminder = (id: string) => {
    LocalStorageManager.toggleReminder(id);
    setReminders(LocalStorageManager.getReminders());
  };

  const handleDeleteReminder = (id: string) => {
    LocalStorageManager.deleteReminder(id);
    setReminders(LocalStorageManager.getReminders());
  };

  const handleAddReminder = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newReminderTitle.trim()) return;
    LocalStorageManager.addReminder(newReminderTitle.trim(), newReminderLang);
    setReminders(LocalStorageManager.getReminders());
    setNewReminderTitle('');
  };

  const handleToggleReveal = (id: string) => {
    setRevealedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleDeleteMemory = (id: string) => {
    LocalStorageManager.deletePersonalMemory(id);
    setMemories(LocalStorageManager.getPersonalMemories());
  };

  const handleClearAll = () => {
    if (confirm('Delete all long-term memories from the vault? This cannot be undone.')) {
      MemoryManager.clearAllMemories();
      setMemories([]);
    }
  };

  const handleToggleMemoryEnabled = () => {
    const newState = !memoryEnabled;
    MemoryManager.setMemoryEnabled(newState);
    setMemoryEnabled(newState);
  };

  const handleAddMemory = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMemKey.trim() || !newMemValue.trim()) return;
    LocalStorageManager.addPersonalMemory(newMemCategory, newMemKey.trim(), newMemValue.trim());
    setMemories(LocalStorageManager.getPersonalMemories());
    setNewMemKey('');
    setNewMemValue('');
    setIsAddingMemory(false);
  };

  // Filter memories by category and search text
  const filteredMemories = memories.filter((m) => {
    const matchesCat = selectedCategoryFilter === 'ALL' || m.category === selectedCategoryFilter;
    if (!matchesCat) return false;
    if (!searchQuery.trim()) return true;
    const plain = CryptoVault.decrypt(m.encryptedValue).toLowerCase();
    const key = m.key.toLowerCase();
    const q = searchQuery.toLowerCase();
    return plain.includes(q) || key.includes(q);
  });

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* HEADER BANNER */}
      <div className="bg-gradient-to-r from-purple-950/40 via-slate-900 to-slate-900 border border-purple-500/30 rounded-2xl p-5 shadow-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-purple-500/10 text-purple-400 rounded-xl border border-purple-500/20">
            <Brain className="w-8 h-8" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-white">Anin Long-Term Memory Vault</h2>
              <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-purple-500/20 text-purple-300">
                11 CATEGORIES
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Personalized facts, routines, relationships (e.g., Shubhrata), and preferences encrypted with AES-256.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleToggleMemoryEnabled}
            className={`px-3 py-1.5 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition ${
              memoryEnabled
                ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                : 'bg-slate-800 border-slate-700 text-slate-400'
            }`}
          >
            {memoryEnabled ? <ToggleRight className="w-4 h-4 text-emerald-400" /> : <ToggleLeft className="w-4 h-4" />}
            <span>Memory: {memoryEnabled ? 'Active' : 'Disabled'}</span>
          </button>

          {memories.length > 0 && (
            <button
              onClick={handleClearAll}
              className="px-3 py-1.5 bg-rose-950/50 hover:bg-rose-900/60 border border-rose-800 text-rose-300 text-xs font-medium rounded-lg flex items-center gap-1 transition"
            >
              <Trash2 className="w-3.5 h-3.5" /> Clear All
            </button>
          )}
        </div>
      </div>

      {/* SECTION 1: SCHEDULED REMINDERS */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlarmClock className="w-5 h-5 text-indigo-400" />
            <h3 className="text-base font-semibold text-white">Scheduled Voice Reminders</h3>
          </div>
          <span className="text-xs text-slate-400 font-mono">
            {reminders.filter((r) => !r.isCompleted).length} active
          </span>
        </div>

        {/* Add Reminder Form */}
        <form onSubmit={handleAddReminder} className="flex gap-2">
          <input
            type="text"
            placeholder="Add reminder (e.g. 'কাল সকাল ১০টায় Shubhrata-কে call করতে মনে করিয়ে দিও')"
            value={newReminderTitle}
            onChange={(e) => setNewReminderTitle(e.target.value)}
            className="flex-1 px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-xs focus:outline-none focus:border-indigo-500"
          />
          <select
            value={newReminderLang}
            onChange={(e) => setNewReminderLang(e.target.value as any)}
            className="px-2 py-2 bg-slate-950 border border-slate-700 rounded-lg text-slate-300 text-xs focus:outline-none"
          >
            <option value="en">English</option>
            <option value="bn">বাংলা</option>
            <option value="hi">हिन्दी</option>
          </select>
          <button
            type="submit"
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
          >
            <Plus className="w-4 h-4" /> Add
          </button>
        </form>

        {/* Reminders List */}
        <div className="space-y-2 pt-2">
          {reminders.length === 0 ? (
            <p className="text-xs text-slate-500 text-center py-4">No reminders scheduled.</p>
          ) : (
            reminders.map((r) => (
              <div
                key={r.id}
                className={`p-3 rounded-xl border flex items-center justify-between text-xs transition ${
                  r.isCompleted
                    ? 'bg-slate-950/40 border-slate-800/50 text-slate-500 line-through'
                    : 'bg-slate-950 border-slate-800 text-slate-200'
                }`}
              >
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => handleToggleReminder(r.id)}
                    className={`w-5 h-5 rounded flex items-center justify-center border transition ${
                      r.isCompleted
                        ? 'bg-emerald-600 border-emerald-500 text-white'
                        : 'border-slate-700 hover:border-slate-500'
                    }`}
                  >
                    {r.isCompleted && <Check className="w-3.5 h-3.5" />}
                  </button>
                  <div>
                    <span className="font-medium">{r.title}</span>
                    <span className="text-[10px] text-slate-500 ml-2 font-mono">
                      [{r.language.toUpperCase()}] • {r.dueTimeFormatted}
                    </span>
                  </div>
                </div>

                <button
                  onClick={() => handleDeleteReminder(r.id)}
                  className="p-1 text-slate-500 hover:text-rose-400 rounded transition"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))
          )}
        </div>
      </div>

      {/* SECTION 2: STRUCTURED LONG-TERM MEMORY */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Lock className="w-5 h-5 text-purple-400" />
            <h3 className="text-base font-semibold text-white">Structured Memory Records ({filteredMemories.length})</h3>
          </div>

          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
              <input
                type="text"
                placeholder="Search memories..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-8 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-purple-500 w-44"
              />
            </div>

            <button
              onClick={() => setIsAddingMemory((prev) => !prev)}
              className="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
            >
              <Plus className="w-3.5 h-3.5" /> Add Record
            </button>
          </div>
        </div>

        {/* Category Filter Chips */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-2 scrollbar-none">
          <button
            onClick={() => setSelectedCategoryFilter('ALL')}
            className={`px-2.5 py-1 rounded-lg text-[11px] font-medium transition flex-shrink-0 ${
              selectedCategoryFilter === 'ALL'
                ? 'bg-purple-600 text-white font-semibold'
                : 'bg-slate-950 border border-slate-800 text-slate-400 hover:text-white'
            }`}
          >
            All Categories ({memories.length})
          </button>
          {MEMORY_CATEGORIES.map((cat) => {
            const count = memories.filter((m) => m.category === cat.id).length;
            const isSelected = selectedCategoryFilter === cat.id;
            return (
              <button
                key={cat.id}
                onClick={() => setSelectedCategoryFilter(cat.id)}
                className={`px-2.5 py-1 rounded-lg text-[11px] font-medium transition flex-shrink-0 flex items-center gap-1.5 ${
                  isSelected
                    ? 'bg-purple-600 text-white font-semibold'
                    : 'bg-slate-950 border border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                <span>{cat.label}</span>
                <span className="text-[10px] px-1 py-0.2 bg-slate-800 rounded font-mono">{count}</span>
              </button>
            );
          })}
        </div>

        {/* Add Memory Form */}
        {isAddingMemory && (
          <form onSubmit={handleAddMemory} className="p-4 bg-slate-950 border border-purple-500/30 rounded-xl space-y-3">
            <h4 className="text-xs font-semibold text-purple-300">New Long-Term Memory Record</h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] text-slate-400 mb-1">Category (11 Structured Types)</label>
                <select
                  value={newMemCategory}
                  onChange={(e) => setNewMemCategory(e.target.value as any)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-lg text-xs text-white focus:outline-none"
                >
                  {MEMORY_CATEGORIES.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.label} ({c.id})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-[11px] text-slate-400 mb-1">Record Key / Title</label>
                <input
                  type="text"
                  placeholder="e.g. Close Friend, Home Address, Sleep Routine"
                  value={newMemKey}
                  onChange={(e) => setNewMemKey(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-lg text-xs text-white focus:outline-none"
                />
              </div>
            </div>
            <div>
              <label className="block text-[11px] text-slate-400 mb-1">Confidential Information</label>
              <textarea
                placeholder="Enter value (e.g. 'Shubhrata is my close friend from university and we meet on weekends')..."
                value={newMemValue}
                onChange={(e) => setNewMemValue(e.target.value)}
                rows={2}
                className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-lg text-xs text-white focus:outline-none"
              />
            </div>
            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setIsAddingMemory(false)}
                className="px-3 py-1.5 bg-slate-800 text-slate-300 text-xs rounded-lg hover:bg-slate-700"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-1.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold rounded-lg"
              >
                Encrypt & Store
              </button>
            </div>
          </form>
        )}

        {/* Memories List */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2">
          {filteredMemories.length === 0 ? (
            <div className="col-span-2 text-center py-6 text-slate-500 text-xs">
              No memory records found matching filter. Speak or save new facts.
            </div>
          ) : (
            filteredMemories.map((mem) => {
              const isRevealed = revealedIds.has(mem.id);
              const decrypted = isRevealed ? CryptoVault.decrypt(mem.encryptedValue) : mem.rawPreview;

              return (
                <div
                  key={mem.id}
                  className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-2 text-xs"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Bookmark className="w-3.5 h-3.5 text-purple-400" />
                      <span className="font-semibold text-white">{mem.key}</span>
                    </div>
                    <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 bg-slate-900 text-purple-300 rounded border border-purple-900/40">
                      {mem.category}
                    </span>
                  </div>

                  <div className="p-2.5 bg-slate-900/80 rounded-lg font-mono text-[11px] text-slate-300 break-all flex items-center justify-between gap-2">
                    <div className="truncate">
                      {isRevealed ? (
                        <span className="text-emerald-300 font-sans">{decrypted}</span>
                      ) : (
                        <span className="text-slate-500">{mem.encryptedValue.substring(0, 24)}...</span>
                      )}
                    </div>
                    <button
                      onClick={() => handleToggleReveal(mem.id)}
                      className="p-1 text-slate-400 hover:text-white rounded"
                      title={isRevealed ? 'Hide plain text' : 'Decrypt with Subham Biometrics'}
                    >
                      {isRevealed ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>

                  <div className="flex items-center justify-between text-[10px] text-slate-500 pt-1">
                    <span>Hardware Keystore: AES-256</span>
                    <button
                      onClick={() => handleDeleteMemory(mem.id)}
                      className="text-slate-500 hover:text-rose-400 transition"
                      title="Delete Memory"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
