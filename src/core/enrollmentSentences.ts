import { EnrollmentSentence } from '../types';

export const ENROLLMENT_SENTENCES: EnrollmentSentence[] = [
  // English samples
  {
    index: 1,
    language: 'en',
    promptText: 'Hey Anin, I am Subham and this is my primary voice.',
    speakingPace: 'normal',
    tone: 'conversational',
    phoneticPattern: 'Vowels and front fricatives (s, h, th)'
  },
  {
    index: 2,
    language: 'en',
    promptText: 'Please secure all my private files and confidential data.',
    speakingPace: 'normal',
    tone: 'formal',
    phoneticPattern: 'Plosives and sibilants (p, s, d, t)'
  },
  {
    index: 3,
    language: 'en',
    promptText: 'What is my current battery percentage and device memory?',
    speakingPace: 'fast',
    tone: 'inquiry',
    phoneticPattern: 'High-frequency transitions and question intonation'
  },
  {
    index: 4,
    language: 'en',
    promptText: 'Whenever I say stop, cancel all ongoing speech immediately.',
    speakingPace: 'slow',
    tone: 'formal',
    phoneticPattern: 'Stops and nasal phonemes (m, n, ng)'
  },

  // Bengali samples
  {
    index: 5,
    language: 'bn',
    promptText: 'হে অনিন, আমি শুভম। আমার নির্দেশ ছাড়া কোনো কাজ করবে না।',
    speakingPace: 'normal',
    tone: 'conversational',
    phoneticPattern: 'Bengali retroflex and dental plosives (ট, ত, ন)'
  },
  {
    index: 6,
    language: 'bn',
    promptText: 'আজকের প্রয়োজনীয় সব কাজ আর রিমাইন্ডার আমাকে দেখাও।',
    speakingPace: 'normal',
    tone: 'inquiry',
    phoneticPattern: 'Bengali open vowel forms (আ, এ, ও)'
  },
  {
    index: 7,
    language: 'bn',
    promptText: 'যদি অন্য কেউ কথা বলে, তুমি একদম শান্ত এবং নীরব থাকবে।',
    speakingPace: 'slow',
    tone: 'formal',
    phoneticPattern: 'Sibilants and aspirates (শ, খ, থ)'
  },
  {
    index: 8,
    language: 'bn',
    promptText: 'আমার ব্যক্তিগত নোট আর সুরক্ষিত মেমোরি ঠিকমতো এনক্রিপ্ট করো।',
    speakingPace: 'fast',
    tone: 'conversational',
    phoneticPattern: 'Nasalized vowels and conjuncts (ঙ্ক, ম)'
  },

  // Hindi samples
  {
    index: 9,
    language: 'hi',
    promptText: 'हे अनिन, मैं शुभम हूँ। मेरी आवाज़ को सुरक्षित रूप से पहचानो।',
    speakingPace: 'normal',
    tone: 'conversational',
    phoneticPattern: 'Hindi aspirated consonants (भ, म, ह)'
  },
  {
    index: 10,
    language: 'hi',
    promptText: 'बिना मेरी अनुमति के कोई भी कॉल या मैसेज मत भेजना।',
    speakingPace: 'normal',
    tone: 'formal',
    phoneticPattern: 'Dental stops and conjuncts (न, त, म)'
  },
  {
    index: 11,
    language: 'hi',
    promptText: 'अगर कोई अनजान व्यक्ति बोले, तो तुरंत पूरी तरह शांत हो जाओ।',
    speakingPace: 'slow',
    tone: 'formal',
    phoneticPattern: 'Voiced aspirates and fricatives (झ, स, श)'
  },
  {
    index: 12,
    language: 'hi',
    promptText: 'आज का तापमान और फोन का सिस्टम स्वास्थ्य कैसा है?',
    speakingPace: 'fast',
    tone: 'inquiry',
    phoneticPattern: 'Syllabic stress and inquiry contour'
  }
];
