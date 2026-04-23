const API_KEY = "AIzaSyAJiu2lu_lQxnRAcZ9sv_1DsXwwHOmbR7I";
const MODEL   = "gemini-3-flash-preview";
const URL     = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${API_KEY}`;

const voiceBtn       = document.getElementById('voiceBtn');
const actionName     = document.getElementById('actionName'); // Mini status
const userTranscript = document.getElementById('userTranscript');
const botResponse    = document.getElementById('botResponse');
const langEn         = document.getElementById('langEn');
const langHi         = document.getElementById('langHi');
const fixMicBtn      = document.getElementById('fixMicBtn');
const manualSend     = document.getElementById('manualSend');
const manualInput    = document.getElementById('manualInput');

let history = [];
let recognition;
let isListening = false;
let currentLang = 'en-US';

const updateStatus = (msg) => {
    actionName.innerText = msg;
    console.log("[AXON]", msg);
};

// --- Language Toggle ---
langEn.onclick = () => {
    currentLang = 'en-US';
    langEn.classList.add('active');
    langHi.classList.remove('active');
    if (recognition) recognition.lang = currentLang;
};

langHi.onclick = () => {
    currentLang = 'hi-IN';
    langHi.classList.add('active');
    langEn.classList.remove('active');
    if (recognition) recognition.lang = currentLang;
};

// --- Speech Recognition Init ---
if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
    const SpeechRecognition = window.webkitSpeechRecognition || window.SpeechRecognition;
    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.lang = currentLang;

    recognition.onstart = () => {
        isListening = true;
        voiceBtn.classList.add('listening');
        updateStatus("Listening...");
        userTranscript.innerText = "";
    };

    recognition.onresult = (event) => {
        let interimTranscript = '';
        for (let i = event.resultIndex; i < event.results.length; ++i) {
            if (event.results[i].isFinal) {
                userTranscript.innerText = event.results[i][0].transcript;
            } else {
                interimTranscript += event.results[i][0].transcript;
                userTranscript.innerText = interimTranscript;
            }
        }
    };

    recognition.onend = () => {
        isListening = false;
        voiceBtn.classList.remove('listening');
        
        setTimeout(() => {
            if (userTranscript.innerText) {
                handleVoiceCommand(userTranscript.innerText);
            } else {
                updateStatus("Ready");
            }
        }, 400);
    };

    recognition.onerror = (event) => {
        console.error("Speech Error:", event.error);
        updateStatus("Error: " + event.error);
        voiceBtn.classList.remove('listening');
    };
}

// --- Gemini API Logic ---
const SYSTEM_PROMPT = `
You are AXON, a friendly female personal assistant. 
Speak in NATURAL HINDI (Hinglish mix is okay). 
Your tone should be polite, helpful, and like a young girl.
Respond ONLY with JSON: { "spoken": "...", "action": { ... } }
`;

async function handleVoiceCommand(text) {
    voiceBtn.classList.add('thinking');
    updateStatus("Thinking");
    try {
        const response = await fetch(URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
                contents: history.concat([{ role: "user", parts: [{ text }] }]),
                generationConfig: { temperature: 0.8, maxOutputTokens: 500 }
            })
        });

        if (!response.ok) {
            const errBody = await response.json();
            throw new Error(errBody.error?.message || "API Error");
        }

        const data = await response.json();
        const rawText = data.candidates[0].content.parts[0].text;
        
        const start = rawText.indexOf('{');
        const end = rawText.lastIndexOf('}');
        const cleanJson = (start !== -1 && end !== -1) ? rawText.substring(start, end + 1) : null;
        
        const axonRes = cleanJson ? JSON.parse(cleanJson) : { spoken: rawText, action: null };

        history.push({ role: "user", parts: [{ text }] });
        history.push({ role: "model", parts: [{ text: rawText }] });

        // Hidden botResponse.innerText = axonRes.spoken;
        voiceBtn.classList.remove('thinking');
        speak(axonRes.spoken);
        updateStatus("Speaking");

    } catch (err) {
        voiceBtn.classList.remove('thinking');
        updateStatus("Error");
        speak("Sorry, error aa gaya.");
    }
}

function speak(text) {
    const utterance = new SpeechSynthesisUtterance(text);
    const voices = window.speechSynthesis.getVoices();
    const bestVoice = voices.find(v => v.lang.includes('hi-IN') && v.name.toLowerCase().includes('google')) || voices.find(v => v.lang.includes('hi-IN'));
    if (bestVoice) utterance.voice = bestVoice;
    
    utterance.lang = 'hi-IN';
    utterance.pitch = 1.05;
    utterance.rate = 1.0;
    utterance.onend = () => { updateStatus("Ready"); };
    window.speechSynthesis.speak(utterance);
}

voiceBtn.addEventListener('click', () => {
    if (isListening) recognition.stop();
    else recognition.start();
});

manualSend.addEventListener('click', () => {
    const text = manualInput.value.trim();
    if (text) {
        userTranscript.innerText = text;
        handleVoiceCommand(text);
        manualInput.value = '';
    }
});

fixMicBtn.addEventListener('click', async () => {
    try {
        await navigator.mediaDevices.getUserMedia({ audio: true });
        updateStatus("Mic Ready.");
    } catch (err) {
        updateStatus("Permission Denied.");
    }
});
