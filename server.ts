import express from 'express';
import cors from 'cors';
import { createServer as createViteServer } from 'vite';
import { GoogleGenAI } from '@google/genai';

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json());

// Initialize server-side Gemini client per skill instructions
const apiKey = process.env.GEMINI_API_KEY || '';
const ai = apiKey
  ? new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build'
        }
      }
    })
  : null;

/**
 * AI Orchestration Endpoint:
 * Performs server-side AI reasoning, Google Search grounding, and tool coordination
 */
app.post('/api/orchestrator/process', async (req, res) => {
  try {
    const {
      query,
      conversationHistory = [],
      memoryContext = [],
      deviceDiagnostics = {},
      detectedLanguage = 'en',
      shouldSearchWeb = false
    } = req.body;

    if (!query || typeof query !== 'string') {
      return res.status(400).json({ error: 'Query string is required' });
    }

    if (!ai) {
      return res.json({
        success: true,
        isOfflineFallback: true,
        responseText: `[Offline Mode] Subham, received your command: "${query}". Gemini server-side key is not set; offline assistant execution active.`,
        searchUsed: false,
        searchSources: []
      });
    }

    // Build Anin Persona System Instruction
    const systemInstruction = `You are Anin, a highly intelligent, secure, private female AI voice assistant running on Subham's iQOO Neo 10R Snapdragon device.
You ALWAYS address the user as "Subham".
You natively understand and speak Bengali, Hindi, and English, including natural code-mixed speech (e.g., "কাল সকাল 10 AM-এ meeting remind করো" or "আজকের weather কেমন?").
Tone: Warm, highly capable, concise when simple, thorough when needed.
Strict Security & Financial Rules:
1. You are strictly forbidden from automating or executing money transfers, UPI payments (GPay/PhonePe/Paytm), bank transfers, or card payments. If requested, refuse politely and suggest manual app access.
2. If web search is performed, ground your answer in verified real-time facts and synthesize clearly.
3. Relevant long-term memory context: ${JSON.stringify(memoryContext)}.
4. Current Device Hardware: ${JSON.stringify(deviceDiagnostics)}.`;

    // Configure tools: If web search is relevant, enable Google Search grounding
    const toolsConfig: any[] = [];
    if (shouldSearchWeb) {
      toolsConfig.push({ googleSearch: {} });
    }

    // Format contents with recent conversation history
    const contents: any[] = [];
    conversationHistory.slice(-4).forEach((turn: any) => {
      contents.push({
        role: turn.role === 'model' ? 'model' : 'user',
        parts: [{ text: turn.text }]
      });
    });

    contents.push({
      role: 'user',
      parts: [
        {
          text: `User Speech [Language: ${detectedLanguage}]: "${query}"`
        }
      ]
    });

    const response = await ai.models.generateContent({
      model: 'gemini-3.8-flash',
      contents,
      config: {
        systemInstruction,
        tools: toolsConfig.length > 0 ? toolsConfig : undefined,
        temperature: 0.7
      }
    });

    const responseText = response.text || '';

    // Extract grounding search sources if web search was used
    const searchSources: Array<{ title: string; uri: string }> = [];
    const groundingChunks = (response.candidates?.[0] as any)?.groundingMetadata?.groundingChunks;
    if (Array.isArray(groundingChunks)) {
      groundingChunks.forEach((chunk: any) => {
        if (chunk.web?.uri) {
          searchSources.push({
            title: chunk.web.title || chunk.web.uri,
            uri: chunk.web.uri
          });
        }
      });
    }

    res.json({
      success: true,
      responseText,
      searchUsed: searchSources.length > 0 || shouldSearchWeb,
      searchSources
    });
  } catch (err: any) {
    console.warn('Server orchestrator fallback triggered:', err.message);
    const lang = req.body?.detectedLanguage || 'en';
    const fallbackMsg =
      lang === 'bn'
        ? 'শুভম, অনলাইন কানেকশন ব্যাহত হওয়ায় অফলাইন লোকাল ইন্টেলিজেন্স মোড সক্রিয় করা হয়েছে।'
        : lang === 'hi'
        ? 'शुभम, ऑनलाइन सेवा अस्थायी रूप से अनुपलब्ध है, अतः स्थानीय ऑफ़लाइन इंटेलिजेंस मोड सक्रिय है।'
        : 'Subham, live online search is currently limited. Operating smoothly in local offline intelligence mode.';

    res.json({
      success: false,
      isOfflineFallback: true,
      error: err.message,
      responseText: fallbackMsg,
      searchUsed: false,
      searchSources: []
    });
  }
});

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', service: 'Anin AI Studio Orchestrator' });
});

// Vite middleware for frontend development
async function startServer() {
  const vite = await createViteServer({
    server: { middlewareMode: true },
    appType: 'spa'
  });

  app.use(vite.middlewares);

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Anin Full-Stack Dev Server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer();
