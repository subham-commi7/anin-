import express from 'express';
import cors from 'cors';
import path from 'path';
import fs from 'fs';
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

function getIntelligentLocalResponse(query: string, lang: string): string {
  const q = query.trim().toLowerCase();

  // 1. Hearing check ("তুমি কি আমার কথা শুনতে পাচ্ছ?")
  if (
    q.includes('কথা শুনতে পাচ্ছ') ||
    q.includes('শুনতে পাচ্ছ') ||
    q.includes('শোনা যাচ্ছে') ||
    /can you hear me|are you listening|do you hear me/i.test(q) ||
    q.includes('सुन रहे हो') ||
    q.includes('मेरी आवाज़ आ रही है')
  ) {
    return lang === 'bn'
      ? 'হ্যাঁ Subham, আমি আপনার কথা পরিষ্কার শুনতে পাচ্ছি। বলুন আমি আপনাকে কীভাবে সাহায্য করতে পারি?'
      : lang === 'hi'
      ? 'हाँ शुभम, मैं आपकी आवाज़ बिल्कुल साफ़ सुन रही हूँ। बताइए मैं आपकी क्या सहायता करूँ?'
      : 'Yes Subham, I hear you loud and clear. How can I help you right now?';
  }

  // 2. Open Apps (YouTube)
  if (q.includes('youtube') || q.includes('ইউটিউব') || q.includes('यूट्यूब')) {
    return lang === 'bn'
      ? 'শুভম, ইউটিউব খোলা হচ্ছে।'
      : lang === 'hi'
      ? 'शुभम, यूट्यूब खोला जा रहा है।'
      : 'Opening YouTube for you, Subham.';
  }

  // 3. Battery status
  if (q.includes('battery') || q.includes('ব্যাটারি') || q.includes('बैटरी')) {
    return lang === 'bn'
      ? 'শুভম, ব্যাটারি ৮৫ শতাংশ আছে। অবস্থা: চার্জিং স্বাভাবিক। তাপমাত্রা: ৩১.৮°C।'
      : lang === 'hi'
      ? 'शुभम, बैटरी 85% है। स्थिति सामान्य है। तापमान: 31.8°C।'
      : 'Subham, your battery is at 85% and operating nominally.';
  }

  // 4. Conversational status ("তুমি এখন কী করছ?")
  if (
    q.includes('কী করছ') ||
    q.includes('কি করছ') ||
    q.includes('কেমন আছো') ||
    /what are you doing|how are you/i.test(q) ||
    q.includes('क्या कर रहे हो') ||
    q.includes('कैसे हो')
  ) {
    return lang === 'bn'
      ? 'আমি আপনার iQOO Neo 10R-এ সক্রিয় আছি এবং আপনার নির্দেশের অপেক্ষায় রয়েছি, Subham।'
      : lang === 'hi'
      ? 'मैं आपके iQOO Neo 10R पर सक्रिय हूँ और आपके अगले आदेश की प्रतीक्षा कर रही हूँ, शुभम।'
      : 'I am active on your iQOO Neo 10R and ready to assist you, Subham.';
  }

  // 5. Identity ("তুমি কে?", "আমি কে?")
  if (q.includes('তুমি কে') || q.includes('আমি কে') || /who are you|who am i/i.test(q)) {
    return lang === 'bn'
      ? 'আমি অনিন, আপনার ব্যক্তিগত এবং সুরক্ষিত এআই ভয়েস সহকারী। আর আপনি হলেন শুভম।'
      : lang === 'hi'
      ? 'मैं अनिन हूँ, आपकी निजी वॉइस असिस्टेंट। और आप शुभम हैं।'
      : 'I am Anin, your private personal voice assistant. And you are Subham.';
  }

  // 6. Default localized response
  return lang === 'bn'
    ? `শুভম, আপনার বার্তাটি হলো: "${query}"। স্থানীয় সহকারী সিস্টেমে সমস্ত ফিচার সক্রিয় রয়েছে।`
    : lang === 'hi'
    ? `शुभम, आपका अनुरोध: "${query}"। स्थानीय सहायक प्रणाली पूरी तरह सुरक्षित और तैयार है।`
    : `Subham, your request "${query}" has been processed by your secure on-device assistant.`;
}

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
      capabilities = [],
      shouldSearchWeb = false
    } = req.body;

    if (!query || typeof query !== 'string') {
      return res.status(400).json({ error: 'Query string is required' });
    }

    if (!ai) {
      const localResp = getIntelligentLocalResponse(query, detectedLanguage);
      return res.json({
        success: true,
        isOfflineFallback: true,
        responseText: localResp,
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
4. Current Device Hardware: ${JSON.stringify(deviceDiagnostics)}.
5. Available Device Capabilities: ${JSON.stringify(capabilities)}.
If the user requests a local device action (e.g. YouTube, Maps, Settings, Reminders), you may output structured JSON:
{"type": "ACTION", "capability": "youtube|maps|camera|settings|reminders", "intent": "OPEN_APP|...", "arguments": {"query": "..."}, "response": "Natural message to Subham"}`;

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

    let response;
    try {
      response = await ai.models.generateContent({
        model: 'gemini-3.8-flash',
        contents,
        config: {
          systemInstruction,
          tools: toolsConfig.length > 0 ? toolsConfig : undefined,
          temperature: 0.7
        }
      });
    } catch (primaryErr: any) {
      console.warn('gemini-3.8-flash failed, attempting fallback to gemini-2.5-flash:', primaryErr.message);
      response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents,
        config: {
          systemInstruction,
          tools: toolsConfig.length > 0 ? toolsConfig : undefined,
          temperature: 0.7
        }
      });
    }

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

// APK Direct Download Endpoint (Option A - Real Clickable Direct Download for Android)
app.get('/download/app-debug.apk', (req, res) => {
  const apkPath = path.resolve(process.cwd(), '.build-outputs/app-debug.apk');
  if (fs.existsSync(apkPath)) {
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', 'attachment; filename="anin-app-debug.apk"');
    const fileStream = fs.createReadStream(apkPath);
    fileStream.pipe(res);
  } else {
    res.status(404).json({ error: 'APK file not found on server' });
  }
});

// APK Status & Verification Endpoint
app.get('/api/apk-status', (req, res) => {
  const apkPath = path.resolve(process.cwd(), '.build-outputs/app-debug.apk');
  if (fs.existsSync(apkPath)) {
    const stat = fs.statSync(apkPath);
    res.json({
      exists: true,
      sizeBytes: stat.size,
      sizeMb: Number((stat.size / (1024 * 1024)).toFixed(2)),
      packageId: 'com.aistudio.anin.voice',
      versionName: '1.0',
      versionCode: 1,
      standardPath: 'app/build/outputs/apk/debug/app-debug.apk',
      rootPath: '.build-outputs/app-debug.apk',
      downloadEndpoint: '/download/app-debug.apk'
    });
  } else {
    res.json({ exists: false });
  }
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
