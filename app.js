// Wizi web app — voice loop.
//
// Design rule: ONE thing owns the microphone at a time, and the mic is only
// ever open while state === "listening". Everything else (thinking, speaking,
// cooldown) has the mic fully closed. That is what stops Wizi from
// (a) taking one "hi" several times and (b) hearing its own voice and
// answering itself.
//
//   idle ──► listening ──► thinking ──► speaking ──► (cooldown) ──► idle ...
//
// Safeguards, in the order they matter:
//   1. Every mic session is a brand-new SpeechRecognition object with a
//      session number. Events from an older session are ignored, so a late
//      or repeated event can never trigger a second turn.
//   2. Only the FIRST final result of a session is used, and repeated
//      phrases ("hi hi hi hi") are collapsed.
//   3. The mic reopens from ONE place (scheduleListen) and only after speech
//      has verifiably finished + a cooldown.
//   4. Heard text that matches Wizi's recent replies is dropped as echo.
//   5. Only one tab/window may own the mic (Web Locks).
//   6. The server drops duplicates and echoes too (see main.py).

(() => {
  "use strict";

  const APP_VERSION = "6";            // must match APP_VERSION in main.py
  const BACKEND_URL = window.VOICE_AI_BACKEND_URL || "";

  const ECHO_COOLDOWN_MS = 1400;      // mic stays closed this long after Wizi stops talking
  const DUPLICATE_WINDOW_MS = 6000;   // same phrase again within this = duplicate
  const ECHO_MEMORY_MS = 30000;       // how long a reply can still be "heard back"
  const REQUEST_TIMEOUT_MS = 65000;   // covers a cold-starting free Render server

  const $ = (id) => document.getElementById(id);
  const orbButton = $("orbButton");
  const statusLabel = $("statusLabel");
  const hintText = $("hintText");
  const conversationLog = $("conversationLog");
  const autoListenToggle = $("autoListenToggle");
  const clearBtn = $("clearBtn");
  const unsupportedBanner = $("unsupportedBanner");

  const SpeechRecognitionAPI = window.SpeechRecognition || window.webkitSpeechRecognition;
  const synth = window.speechSynthesis;

  // ---------------------------------------------------------------------------
  // Small helpers
  // ---------------------------------------------------------------------------

  const STOPWORDS = new Set(
    "a an the and or but if so to of in on at for with is are was were be been am it its it's i you he she we they me my your this that these those what who how why when where do does did can could would should will just yes no ok okay hi hello hey please".split(" ")
  );

  function norm(text) {
    return (text || "").toLowerCase().replace(/[^a-z0-9 ]+/g, " ").replace(/\s+/g, " ").trim();
  }

  // "hi hi hi hi" -> "hi", "how are you how are you" -> "how are you"
  function collapseRepeats(text) {
    const words = text.trim().split(/\s+/);
    const n = words.length;
    for (let size = 1; size <= Math.floor(n / 2); size++) {
      if (n % size !== 0) continue;
      const block = words.slice(0, size).join(" ").toLowerCase().replace(/[^a-z0-9 ]+/g, "");
      let same = true;
      for (let i = size; i < n; i += size) {
        const chunk = words.slice(i, i + size).join(" ").toLowerCase().replace(/[^a-z0-9 ]+/g, "");
        if (chunk !== block) { same = false; break; }
      }
      if (same) return words.slice(0, size).join(" ");
    }
    return text.trim();
  }

  function uid() {
    return (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2));
  }

  function getClientId() {
    try {
      let id = localStorage.getItem("wizi:client");
      if (!id) { id = uid(); localStorage.setItem("wizi:client", id); }
      return id;
    } catch (e) {
      return uid();
    }
  }
  const CLIENT_ID = getClientId();

  // ---------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------

  const savedSettings = window.WiziMemory ? window.WiziMemory.loadSettings() : {};
  autoListenToggle.checked = savedSettings.autoListen !== false;

  let state = "idle";              // idle | listening | thinking | speaking | error
  let history = window.WiziMemory ? window.WiziMemory.loadHistory() : [];
  window.lastAssistantReply = "";  // used by commands.js ("share that", "copy that")

  let session = 0;                 // bumped every time the mic is opened or closed
  let rec = null;                  // the current SpeechRecognition (or null)
  let listenTimer = null;          // the ONE pending "open the mic" timer
  let ignoreUntil = 0;             // mic results are ignored until this time
  let turnSeq = 0;                 // bumped for every turn / cancel
  let userPaused = false;          // user tapped the orb to stop listening
  let micOwner = true;             // false if another Wizi window holds the mic
  let permissionDenied = false;
  let errorStreak = 0;

  let lastKey = "";
  let lastKeyAt = 0;
  const recentReplies = [];        // [{key, endedAt|null}]

  // ---------------------------------------------------------------------------
  // UI
  // ---------------------------------------------------------------------------

  function setState(next) {
    state = next;
    orbButton.classList.remove("listening", "thinking", "speaking");
    statusLabel.classList.remove("listening", "thinking", "speaking", "error");

    statusLabel.textContent = {
      idle: "STANDBY", listening: "LISTENING", thinking: "THINKING",
      speaking: "SPEAKING", error: "ERROR",
    }[next] || "STANDBY";

    if (next === "listening" || next === "thinking" || next === "speaking") {
      orbButton.classList.add(next);
      statusLabel.classList.add(next);
    }
    if (next === "error") statusLabel.classList.add("error");

    hintText.textContent = {
      idle: userPaused || !autoListenToggle.checked ? "Tap to talk" : "Ready…",
      listening: "Listening…",
      thinking: "Thinking…",
      speaking: "Speaking… (tap to stop)",
      error: "Tap to try again",
    }[next];
  }

  function addBubble(role, text) {
    if (!text || !String(text).trim()) return document.createElement("div");   // never draw an empty bubble
    const el = document.createElement("div");
    el.className = `bubble ${role}`;
    el.textContent = text;
    conversationLog.appendChild(el);
    conversationLog.scrollTop = conversationLog.scrollHeight;
    return el;
  }

  function addSystemBubble(text) {
    return addBubble("system", text);
  }

  // ---------------------------------------------------------------------------
  // Microphone
  // ---------------------------------------------------------------------------

  function wantsMic() {
    return !!SpeechRecognitionAPI && micOwner && !permissionDenied && !userPaused &&
      autoListenToggle.checked && !document.hidden;
  }

  /** Close the mic completely. After this, no event from it can reach us. */
  function stopMic() {
    session++;
    clearTimeout(listenTimer);
    listenTimer = null;
    const r = rec;
    rec = null;
    if (r) {
      r.onresult = r.onerror = r.onend = r.onstart = null;
      try { r.abort(); } catch (e) { /* already stopped */ }
    }
  }

  /** The only place that schedules the mic to open again. */
  function scheduleListen(delay = 0) {
    clearTimeout(listenTimer);
    listenTimer = null;
    if (!wantsMic()) return;
    listenTimer = setTimeout(startListening, Math.max(delay, 0));
  }

  function startListening() {
    clearTimeout(listenTimer);
    listenTimer = null;
    if (!SpeechRecognitionAPI || state !== "idle" || rec) return;

    // Never open the mic while the cooldown runs or the speaker is still going.
    const wait = ignoreUntil - Date.now();
    if (wait > 0) { listenTimer = setTimeout(startListening, wait + 30); return; }
    if (synth && (synth.speaking || synth.pending)) { listenTimer = setTimeout(startListening, 300); return; }

    const mySession = ++session;
    const r = new SpeechRecognitionAPI();
    r.lang = "en-US";
    r.continuous = false;
    r.interimResults = false;
    r.maxAlternatives = 1;

    let accepted = false;
    let gotError = false;

    r.onresult = (event) => {
      if (mySession !== session || accepted) return;

      // Use ONLY the first final result. (Android Chrome can return the same
      // phrase as several results — that is where "hi hi hi hi" came from.)
      let transcript = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) { transcript = event.results[i][0].transcript; break; }
      }
      transcript = collapseRepeats(transcript || "");
      if (!transcript) return;

      if (!acceptable(transcript)) return;
      accepted = true;
      errorStreak = 0;
      stopMic();                       // mic closes BEFORE anything else happens
      handleUtterance(transcript);
    };

    r.onerror = (event) => {
      if (mySession !== session) return;
      const err = event.error;
      if (err === "not-allowed" || err === "service-not-allowed") {
        permissionDenied = true;
        gotError = true;
        setState("error");
        addSystemBubble("Microphone access was denied. Allow it in your browser's site settings, then tap the orb.");
      } else if (err === "no-speech" || err === "aborted") {
        // normal: silence, or we closed it ourselves
      } else {
        gotError = true;
        errorStreak++;
        if (errorStreak === 3) addSystemBubble(`Mic problem (${err}). Trying again…`);
      }
    };

    r.onend = () => {
      if (mySession !== session) return;        // stale session: ignore
      rec = null;
      session++;
      if (accepted || permissionDenied) return;
      if (state === "listening") setState("idle");
      // Silence timeout or error: keep "always listening" going, with backoff.
      const backoff = gotError ? Math.min(500 * 2 ** errorStreak, 8000) : 300;
      scheduleListen(backoff);
    };

    rec = r;
    try {
      r.start();
      setState("listening");
    } catch (err) {
      rec = null;
      console.warn("recognition.start() failed", err);
      scheduleListen(1000);
    }
  }

  /** Is this heard text something we should act on? */
  function acceptable(transcript) {
    const now = Date.now();
    if (now < ignoreUntil) return false;
    const key = norm(transcript);
    if (!key) return false;
    if (key === lastKey && now - lastKeyAt < DUPLICATE_WINDOW_MS) return false;
    if (looksLikeEcho(key)) return false;
    lastKey = key;
    lastKeyAt = now;
    return true;
  }

  // ---------------------------------------------------------------------------
  // Echo detection (Wizi hearing itself)
  // ---------------------------------------------------------------------------

  function rememberReply(text) {
    recentReplies.push({ key: norm(text), endedAt: null });
    while (recentReplies.length > 3) recentReplies.shift();
  }

  function looksLikeEcho(heardKey) {
    const now = Date.now();
    const heard = heardKey.split(" ").filter(Boolean);
    for (const r of recentReplies) {
      const age = r.endedAt === null ? 0 : now - r.endedAt;
      if (age > ECHO_MEMORY_MS) continue;
      if (!r.key) continue;
      if (heard.length >= 2 && r.key.includes(heardKey)) return true;          // a piece of the reply
      const replyWords = r.key.split(" ");
      // starts like the reply ("hey there i m wizzy ...") = Wizi's own opening
      if (heard.length >= 3 && heard.slice(0, 3).join(" ") === replyWords.slice(0, 3).join(" ")) return true;
      const content = heard.filter((w) => !STOPWORDS.has(w));
      if (content.length >= 2) {
        const words = new Set(replyWords);
        const hit = content.filter((w) => words.has(w)).length;
        // Right after Wizi spoke, even a partial match (echo mixed with noise) counts.
        const limit = age < 12000 ? 0.6 : 0.8;
        if (hit / content.length >= limit) return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------
  // One turn: heard text -> command or AI -> spoken reply
  // ---------------------------------------------------------------------------

  async function handleUtterance(text) {
    if (state === "thinking" || state === "speaking") return;   // one turn at a time
    const myTurn = ++turnSeq;
    setState("thinking");
    const userBubble = addBubble("user", text);

    // Device commands first (open X, set a timer, ...). They never go to the AI.
    const cmd = window.tryHandleCommand ? window.tryHandleCommand(text) : { handled: false };
    if (cmd.handled) {
      addBubble("assistant", cmd.spoken);
      window.lastAssistantReply = cmd.spoken;
      speak(cmd.spoken, cmd.deferredAction);
      return;
    }

    history.push({ role: "user", content: text });
    try {
      const data = await askBackend(text, history);
      if (myTurn !== turnSeq) return;                           // cancelled (Clear pressed)

      if (!data.reply) {
        // The server dropped it as a duplicate/echo. Say nothing, listen again.
        history.pop();
        userBubble.remove();
        setState("idle");
        scheduleListen(500);
        return;
      }

      addBubble("assistant", data.reply);
      window.lastAssistantReply = data.reply;
      history.push({ role: "assistant", content: data.reply });
      if (window.WiziMemory) window.WiziMemory.saveHistory(history);
      speak(data.reply);
    } catch (err) {
      if (myTurn !== turnSeq) return;
      console.error(err);
      history.pop();
      setState("error");
      addSystemBubble(
        err.name === "AbortError"
          ? "The server took too long (it may be waking up). Try again in a moment."
          : (err.message || "Couldn't reach the assistant. Check that the backend is running.")
      );
      setTimeout(() => {
        if (myTurn === turnSeq && state === "error") { setState("idle"); scheduleListen(500); }
      }, 2500);
    }
  }

  async function askBackend(message, historyForRequest) {
    const memories = window.WiziMemory ? window.WiziMemory.loadFacts() : [];
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), REQUEST_TIMEOUT_MS);
    try {
      const res = await fetch(`${BACKEND_URL}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: ctrl.signal,
        body: JSON.stringify({
          message,
          history: historyForRequest.slice(0, -1),   // exclude the message just added
          memories,
          client_id: CLIENT_ID,
          turn_id: uid(),
        }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || `Backend returned ${res.status}`);
      }
      return await res.json();
    } finally {
      clearTimeout(timer);
    }
  }

  // ---------------------------------------------------------------------------
  // Speaking
  // ---------------------------------------------------------------------------

  let speakToken = 0;
  let speakTimers = [];

  function clearSpeakTimers() {
    speakTimers.forEach(clearTimeout);
    speakTimers = [];
  }

  function splitForSpeech(text) {
    // Short chunks avoid Chrome cutting off long utterances.
    const sentences = text.match(/[^.!?]+[.!?]*\s*/g) || [text];
    const chunks = [];
    let cur = "";
    for (const s of sentences) {
      if ((cur + s).length > 180 && cur) { chunks.push(cur.trim()); cur = s; }
      else cur += s;
    }
    if (cur.trim()) chunks.push(cur.trim());
    return chunks.length ? chunks : [text];
  }

  /**
   * Speak `text`. The mic is closed for the whole time and only reopens
   * after the speech has verifiably ended plus a cooldown. `after` (optional)
   * runs instead of re-opening the mic (used for "open Gmail" navigation).
   */
  function speak(text, after) {
    if (!synth) { setState("idle"); if (after) after(); else scheduleListen(0); return; }

    const token = ++speakToken;
    stopMic();
    clearSpeakTimers();
    synth.cancel();
    setState("speaking");
    rememberReply(text);
    const entry = recentReplies[recentReplies.length - 1];

    const chunks = splitForSpeech(text);
    const startedAt = Date.now();
    // Some Android voices report "ended" early; never finish sooner than this.
    const minEnd = startedAt + 0.7 * text.length * 62;
    let idx = 0;
    let done = false;

    const finish = () => {
      if (done || token !== speakToken) return;
      done = true;
      clearSpeakTimers();
      entry.endedAt = Date.now();
      ignoreUntil = Date.now() + ECHO_COOLDOWN_MS;
      setState("idle");
      if (after) after();
      else scheduleListen(ECHO_COOLDOWN_MS);
    };

    const waitUntilSilent = (tries = 0) => {
      if (token !== speakToken || done) return;
      const early = minEnd - Date.now();
      if (early > 0) { speakTimers.push(setTimeout(() => waitUntilSilent(tries), early)); return; }
      if ((synth.speaking || synth.pending) && tries < 15) {
        speakTimers.push(setTimeout(() => waitUntilSilent(tries + 1), 200));
        return;
      }
      finish();
    };

    const next = () => {
      if (token !== speakToken || done) return;
      if (idx >= chunks.length) { waitUntilSilent(); return; }
      const u = new SpeechSynthesisUtterance(chunks[idx++]);
      u.rate = 1.0;
      u.pitch = 1.0;
      u.onend = next;
      u.onerror = (e) => {
        if (token !== speakToken) return;                      // cancelled by a newer speak()
        if (e.error === "interrupted" || e.error === "canceled") return;
        if (e.error === "not-allowed") {
          addSystemBubble("Your browser blocked Wizi's voice. Tap the orb once, then try again.");
        }
        waitUntilSilent();
      };
      synth.speak(u);
    };

    // Hard stop in case the browser never reports the end of speech.
    speakTimers.push(setTimeout(() => { if (token === speakToken) { synth.cancel(); finish(); } },
      Math.min(5000 + text.length * 110, 120000)));

    // Chrome can drop speak() called right after cancel(); give it a moment.
    speakTimers.push(setTimeout(next, 80));
  }

  function cancelSpeech() {
    speakToken++;
    clearSpeakTimers();
    if (synth) synth.cancel();
  }

  /** For commands.js (timer finished, location error, ...) — speaks without opening the mic on top of it. */
  window.WiziSay = function say(text, tries = 0) {
    if (state === "idle" && !rec) { addBubble("assistant", text); speak(text); return; }
    if (state === "listening") { stopMic(); setState("idle"); addBubble("assistant", text); speak(text); return; }
    if (tries < 40) setTimeout(() => window.WiziSay(text, tries + 1), 750);
  };

  // ---------------------------------------------------------------------------
  // Controls
  // ---------------------------------------------------------------------------

  orbButton.addEventListener("click", async () => {
    if (!SpeechRecognitionAPI) return;

    if (state === "speaking") {            // tap = stop talking
      cancelSpeech();
      ignoreUntil = Date.now() + 600;
      setState("idle");
      scheduleListen(600);
      return;
    }
    if (state === "thinking") return;

    if (state === "listening") {           // tap = stop listening
      userPaused = true;
      stopMic();
      setState("idle");
      return;
    }

    userPaused = false;
    permissionDenied = false;
    if (!micOwner) micOwner = await acquireMicOwnership();
    if (!micOwner) { addSystemBubble("Wizi is already open in another window. Close it, then tap again."); return; }
    if (state === "error") setState("idle");
    startListening();
  });

  autoListenToggle.addEventListener("change", () => {
    if (window.WiziMemory) window.WiziMemory.saveSetting("autoListen", autoListenToggle.checked);
    if (autoListenToggle.checked) {
      userPaused = false;
      if (window.WiziWakeLock) window.WiziWakeLock.request();
      if (state === "idle") scheduleListen(0);
    } else {
      if (window.WiziWakeLock) window.WiziWakeLock.release();
      if (state === "listening") { stopMic(); setState("idle"); }
      clearTimeout(listenTimer);
    }
  });

  clearBtn.addEventListener("click", () => {
    turnSeq++;                              // abandons any reply still on its way
    cancelSpeech();
    stopMic();
    history = [];
    if (window.WiziMemory) window.WiziMemory.clearHistory();
    conversationLog.innerHTML = "";
    recentReplies.length = 0;
    setState("idle");
    scheduleListen(500);
  });

  // Browsers close the mic when a page is hidden; reopen it when we're back.
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      stopMic();
      if (state === "listening") setState("idle");
    } else if (state === "idle") {
      scheduleListen(500);
    }
  });
  window.addEventListener("pagehide", () => { stopMic(); cancelSpeech(); });

  // Chrome only allows speech after a tap on the page; unlock it on the first one.
  window.addEventListener("pointerdown", () => {
    try {
      if (synth && !synth.speaking && !synth.pending) {
        const u = new SpeechSynthesisUtterance(" ");
        u.volume = 0;
        synth.speak(u);
      }
    } catch (e) { /* ignore */ }
  }, { once: true, capture: true });

  // ---------------------------------------------------------------------------
  // Only one window may use the mic (PWA + browser tab both open = double answers)
  // ---------------------------------------------------------------------------

  function acquireMicOwnership() {
    if (!navigator.locks || !navigator.locks.request) return Promise.resolve(true);
    return new Promise((resolve) => {
      navigator.locks.request("wizi-mic-owner", { ifAvailable: true }, (lock) => {
        if (!lock) { resolve(false); return undefined; }
        resolve(true);
        return new Promise(() => {});       // hold the lock until this window closes
      }).catch(() => resolve(true));
    });
  }

  // ---------------------------------------------------------------------------
  // Shared links (Android share sheet)
  // ---------------------------------------------------------------------------

  async function handleIncomingShare() {
    const params = new URLSearchParams(window.location.search);
    const sharedUrl = params.get("shared_url");
    const sharedText = params.get("shared_text");
    const sharedTitle = params.get("shared_title");
    if (!sharedUrl && !sharedText) return false;

    window.history.replaceState({}, "", window.location.pathname);
    addSystemBubble(`Received a share: ${sharedTitle || sharedUrl || "that"}`);
    const myTurn = ++turnSeq;
    setState("thinking");

    try {
      const res = await fetch(`${BACKEND_URL}/summarize`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: sharedUrl || "", text: sharedText || "" }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || `Backend returned ${res.status}`);
      }
      const data = await res.json();
      if (myTurn !== turnSeq) return true;
      addBubble("assistant", data.summary);
      window.lastAssistantReply = data.summary;
      speak(data.summary);
    } catch (err) {
      console.error(err);
      setState("error");
      addSystemBubble("Couldn't summarize that — " + err.message);
      setTimeout(() => { if (state === "error") { setState("idle"); scheduleListen(500); } }, 2500);
    }
    return true;
  }

  // ---------------------------------------------------------------------------
  // Start-up
  // ---------------------------------------------------------------------------

  // If the server has a newer version than this page (e.g. the app was left open
  // for days), clear old caches and reload once so the new code runs.
  async function checkVersion() {
    try {
      const res = await fetch(`${BACKEND_URL}/health`, { cache: "no-store" });
      const info = await res.json();
      if (info.version && info.version !== APP_VERSION) {
        const key = "wizi:reloaded-for";
        if (sessionStorage.getItem(key) === info.version) return;       // never loop
        sessionStorage.setItem(key, info.version);
        if (window.caches) { const keys = await caches.keys(); await Promise.all(keys.map((k) => caches.delete(k))); }
        if (navigator.serviceWorker) {
          const regs = await navigator.serviceWorker.getRegistrations();
          await Promise.all(regs.map((r) => r.unregister()));
        }
        location.reload();
      }
    } catch (e) { /* offline or server asleep: ignore */ }
  }

  async function boot() {
    const tag = document.getElementById("versionTag");
    if (tag) tag.textContent = "v" + APP_VERSION;
    checkVersion();
    setState("idle");
    history.forEach((turn) => addBubble(turn.role, turn.content));

    if (!SpeechRecognitionAPI || !synth) {
      unsupportedBanner.hidden = false;
      orbButton.disabled = true;
      hintText.textContent = "Voice input unavailable in this browser";
    } else {
      micOwner = await acquireMicOwnership();
      if (!micOwner) {
        addSystemBubble("Wizi is already open in another window or app, so the microphone is off here. Close the other one, then tap the orb.");
      }
    }

    const sharing = await handleIncomingShare();

    if (autoListenToggle.checked && SpeechRecognitionAPI && micOwner) {
      if (window.WiziWakeLock) window.WiziWakeLock.request();
      if (!sharing) scheduleListen(300);
    }
  }

  boot();

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("service-worker.js").catch((err) => {
        console.warn("Service worker registration failed", err);
      });
    });
  }

  // Exposed for tests only.
  window.__wizi = { version: APP_VERSION, norm, collapseRepeats, looksLikeEcho, getState: () => state };
})();
