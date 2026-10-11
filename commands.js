// Voice command router — built by Riley (Integrations)
// -------------------------------------------------------
// Looks at the user's spoken text BEFORE sending it to the AI. If it matches
// a device action (open something, call/text someone, set a timer/alarm),
// it performs that action directly and returns a spoken confirmation.
// Anything that doesn't match falls through to the normal AI chat.
//
// Honesty notes baked into the behavior itself:
//  - Calls and texts open the native Phone/Messages app pre-filled, but
//    Android never lets a web page send/dial silently — the user still
//    taps to confirm. We tell the user that out loud so it's not a surprise.
//  - Timers/alarms use Android's standard AlarmClock intents (SET_TIMER /
//    SET_ALARM), which real Clock apps support. We also run an in-app
//    countdown as a backup in case the device's Clock app doesn't respond
//    to the intent (some heavily-customized Android skins vary).

const KNOWN_SITES = {
  youtube: "https://www.youtube.com",
  maps: "https://www.google.com/maps",
  gmail: "https://mail.google.com",
  email: "https://mail.google.com",
  amazon: "https://www.amazon.com",
  netflix: "https://www.netflix.com",
  spotify: "https://open.spotify.com",
  weather: "https://www.google.com/search?q=weather",
  news: "https://news.google.com",
  wikipedia: "https://www.wikipedia.org",
  gmaps: "https://www.google.com/maps",
  calendar: "https://calendar.google.com",
  instagram: "https://www.instagram.com",
  facebook: "https://www.facebook.com",
  twitter: "https://www.x.com",
  x: "https://www.x.com",
  reddit: "https://www.reddit.com",
  whatsapp: "https://web.whatsapp.com",
  twitch: "https://www.twitch.tv",
  tiktok: "https://www.tiktok.com",
  uber: "https://m.uber.com",
  drive: "https://drive.google.com",
  photos: "https://photos.google.com",
  translate: "https://translate.google.com",
  linkedin: "https://www.linkedin.com",
  pinterest: "https://www.pinterest.com",
};

const NUMBER_WORDS = {
  zero: 0, one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7,
  eight: 8, nine: 9, ten: 10, eleven: 11, twelve: 12, thirteen: 13,
  fourteen: 14, fifteen: 15, twenty: 20, thirty: 30, forty: 40, fifty: 50,
  sixty: 60, ninety: 90,
};

function wordsToNumber(text) {
  const digits = text.match(/\d+/);
  if (digits) return parseInt(digits[0], 10);
  const word = text.trim().toLowerCase();
  if (NUMBER_WORDS[word] !== undefined) return NUMBER_WORDS[word];
  return null;
}

/**
 * Try to handle `text` as a device command.
 * Returns { handled: true, spoken: "..." } if it did something,
 * or { handled: false } to fall through to the AI chat.
 */
function tryHandleCommand(text) {
  let t = text.trim().toLowerCase();
  // Strip polite filler so "please open Gmail" / "can you call ..." /
  // "hey, set a timer" still match the same patterns as the bare command.
  t = t.replace(/^(hey|ok|okay)[,\s]+/i, "")
       .replace(/^wizi[,\s]+/i, "")
       .replace(/^(please|can you|could you|would you)\s+/i, "")
       .replace(/^(please|go ahead and)\s+/i, "");

  // --- Timers -------------------------------------------------------
  let m = t.match(/set (?:a )?timer for (\d+|\w+)\s*(second|minute|hour)s?/);
  if (m) {
    const amount = wordsToNumber(m[1]);
    const unit = m[2];
    if (amount) {
      const seconds = unit === "hour" ? amount * 3600 : unit === "minute" ? amount * 60 : amount;
      launchAndroidIntent(
        `intent:#Intent;action=android.intent.action.SET_TIMER;i.android.intent.extra.alarm.LENGTH=${seconds};S.android.intent.extra.alarm.MESSAGE=Voice assistant timer;end`
      );
      startFallbackCountdown(seconds, `Your ${m[1]} ${unit} timer is done.`);
      return { handled: true, spoken: `Setting a timer for ${m[1]} ${unit}${amount > 1 ? "s" : ""}. Check your Clock app to confirm it started.` };
    }
  }

  // --- Alarms ---------------------------------------------------------
  m = t.match(/set (?:an )?alarm for (\d{1,2})(?::(\d{2}))?\s*(am|pm)?/);
  if (m) {
    let hour = parseInt(m[1], 10);
    const minute = m[2] ? parseInt(m[2], 10) : 0;
    const meridiem = m[3];
    if (meridiem === "pm" && hour < 12) hour += 12;
    if (meridiem === "am" && hour === 12) hour = 0;
    launchAndroidIntent(
      `intent:#Intent;action=android.intent.action.SET_ALARM;i.android.intent.extra.alarm.HOUR=${hour};i.android.intent.extra.alarm.MINUTES=${minute};S.android.intent.extra.alarm.MESSAGE=Voice assistant alarm;end`
    );
    return { handled: true, spoken: `Opening your Clock app to set an alarm for ${m[1]}${m[2] ? ":" + m[2] : ""} ${meridiem || ""}. Confirm it there.` };
  }

  // --- Calling ----------------------------------------------------------
  m = t.match(/^(?:call|dial|phone)\s+(.+)/);
  if (m) {
    const target = m[1].trim();
    const digits = target.replace(/[^\d+]/g, "");
    if (digits.length >= 7) {
      window.location.href = `tel:${digits}`;
      return { handled: true, spoken: `Opening the dialer for ${target}. Tap call to confirm — I can't dial for you.` };
    }
    // Named contact — use the Contact Picker API if available so the user
    // picks the right person; we can't look up a name to a number ourselves.
    if (navigator.contacts && navigator.contacts.select) {
      navigator.contacts.select(["tel"], { multiple: false }).then((contacts) => {
        if (contacts[0]?.tel?.[0]) window.location.href = `tel:${contacts[0].tel[0]}`;
      }).catch(() => {});
      return { handled: true, spoken: `Pick ${target} from your contacts and I'll open the dialer.` };
    }
    return { handled: true, spoken: `I can't look up ${target}'s number myself — try saying the phone number instead.` };
  }

  // --- Texting -------------------------------------------------------
  m = t.match(/(?:text|message)\s+(.+?)(?:\s+(?:saying|that says|with the message)\s+(.+))?$/);
  if (m && (t.startsWith("text ") || t.startsWith("message "))) {
    const target = m[1].trim();
    const body = m[2] ? m[2].trim() : "";
    const digits = target.replace(/[^\d+]/g, "");
    const smsUrl = digits.length >= 7
      ? `sms:${digits}${body ? "?body=" + encodeURIComponent(body) : ""}`
      : null;
    if (smsUrl) {
      window.location.href = smsUrl;
      return { handled: true, spoken: `Opening a message to ${target}. Tap send to confirm — I can't send it for you.` };
    }
    if (navigator.contacts && navigator.contacts.select) {
      navigator.contacts.select(["tel"], { multiple: false }).then((contacts) => {
        if (contacts[0]?.tel?.[0]) {
          window.location.href = `sms:${contacts[0].tel[0]}${body ? "?body=" + encodeURIComponent(body) : ""}`;
        }
      }).catch(() => {});
      return { handled: true, spoken: `Pick ${target} from your contacts and I'll open the message.` };
    }
    return { handled: true, spoken: `I can't look up ${target}'s number myself — try saying the phone number instead.` };
  }

  // --- Searching -------------------------------------------------------
  m = t.match(/^(?:search for|google|look up)\s+(.+)/);
  if (m) {
    const query = m[1].trim();
    const url = `https://www.google.com/search?q=${encodeURIComponent(query)}`;
    return {
      handled: true,
      spoken: `Here's what I found for ${query}.`,
      // Navigating happens AFTER speaking, not immediately: browsers silently
      // block window.open() when it's triggered by a voice result instead of
      // a direct click, so we speak first, then navigate the same tab —
      // that kind of navigation is never blocked.
      deferredAction: () => { window.location.href = url; },
    };
  }

  // --- Opening sites/apps ------------------------------------------------
  m = t.match(/^open\s+(.+)/);
  if (m) {
    const rawTarget = m[1].trim().toLowerCase()
      .replace(/^(the|my)\s+/, "")
      .replace(/\s+(app|application|website|site|for me|please|now|up)$/g, "")
      .trim();

    // Try an exact match first, then fall back to checking whether a known
    // site name appears anywhere in the phrase as a whole word — real speech
    // often adds extra words ("open Gmail for me", "open my Gmail app")
    // that broke a strict exact-match lookup before.
    let url = KNOWN_SITES[rawTarget];
    if (!url) {
      const matchedKey = Object.keys(KNOWN_SITES).find((key) =>
        new RegExp(`\\b${key}\\b`).test(rawTarget)
      );
      if (matchedKey) url = KNOWN_SITES[matchedKey];
    }
    if (!url) url = `https://www.google.com/search?q=${encodeURIComponent(rawTarget)}`;

    return {
      handled: true,
      spoken: `Opening ${m[1].trim()}.`,
      deferredAction: () => { window.location.href = url; },
    };
  }

  // --- Memory: teaching it facts to remember across sessions -----------
  m = t.match(/^remember (?:that )?(.+)/);
  if (m) {
    const fact = m[1].trim();
    if (window.WiziMemory) window.WiziMemory.addFact(fact);
    return { handled: true, spoken: `Got it, I'll remember that ${fact}.` };
  }

  if (/^what do you remember( about me)?\??$/.test(t)) {
    const facts = window.WiziMemory ? window.WiziMemory.loadFacts() : [];
    if (facts.length === 0) {
      return { handled: true, spoken: "You haven't asked me to remember anything yet." };
    }
    return { handled: true, spoken: `Here's what I remember: ${facts.join(". ")}.` };
  }

  if (/^forget everything( you know about me)?$/.test(t)) {
    if (window.WiziMemory) window.WiziMemory.clearFacts();
    return { handled: true, spoken: "Done — I've forgotten everything you taught me." };
  }

  // --- Sharing / copying the last reply to another app --------------------
  if (/^share (that|this)$/.test(t)) {
    const text = window.lastAssistantReply || "";
    if (!text) return { handled: true, spoken: "There's nothing to share yet." };
    if (navigator.share) {
      navigator.share({ text }).catch(() => {});
      return { handled: true, spoken: "Opening the share menu." };
    }
    return { handled: true, spoken: "Sharing isn't supported in this browser." };
  }

  if (/^copy (that|this)$/.test(t)) {
    const text = window.lastAssistantReply || "";
    if (!text) return { handled: true, spoken: "There's nothing to copy yet." };
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).catch(() => {});
      return { handled: true, spoken: "Copied to your clipboard." };
    }
    return { handled: true, spoken: "Clipboard access isn't supported in this browser." };
  }

  // --- Location -----------------------------------------------------------
  if (/where am i|my location|current location/.test(t)) {
    if (!navigator.geolocation) {
      return { handled: true, spoken: "Location isn't supported in this browser." };
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude, longitude } = pos.coords;
        window.location.href = `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;
      },
      () => {
        sayManaged("I couldn't get your location — check that location access is allowed for this app.");
      }
    );
    return { handled: true, spoken: "Finding your location now." };
  }

  // --- Keeping the screen awake -------------------------------------------
  if (/keep the screen on|don'?t let the screen (turn off|sleep|lock)/.test(t)) {
    requestWakeLock();
    return { handled: true, spoken: "I'll try to keep the screen on while this app is open." };
  }
  if (/let the screen (turn off|sleep|lock)|screen can turn off/.test(t)) {
    releaseWakeLock();
    return { handled: true, spoken: "Okay, the screen can sleep normally again." };
  }

  return { handled: false };
}

// ---------------------------------------------------------------------------
// Screen Wake Lock — genuinely keeps the tablet's display on. Real browser
// API (Chrome on Android supports it), not a workaround.
// ---------------------------------------------------------------------------

let wakeLock = null;

async function requestWakeLock() {
  if (!("wakeLock" in navigator)) return;
  try {
    wakeLock = await navigator.wakeLock.request("screen");
  } catch (err) {
    console.warn("Wake lock request failed", err);
  }
}

function releaseWakeLock() {
  if (wakeLock) {
    wakeLock.release().catch(() => {});
    wakeLock = null;
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function launchAndroidIntent(intentUrl) {
  // Navigating to an intent: URL is how a web page triggers a native Android
  // action. If the target app can't handle it, Android silently ignores it
  // (no crash) — that's why we also run a fallback in-app countdown for timers.
  try {
    window.location.href = intentUrl;
  } catch (err) {
    console.warn("Intent launch failed", err);
  }
}

// Speak through app.js so the microphone is closed while Wizi talks. (Calling
// speechSynthesis directly here made Wizi hear itself and answer.)
function sayManaged(text) {
  if (window.WiziSay) window.WiziSay(text);
  else if (window.speechSynthesis) window.speechSynthesis.speak(new SpeechSynthesisUtterance(text));
}

let fallbackTimerId = null;

function startFallbackCountdown(seconds, message) {
  if (fallbackTimerId) clearTimeout(fallbackTimerId);
  fallbackTimerId = setTimeout(() => {
    sayManaged(message);
  }, seconds * 1000);
}

window.tryHandleCommand = tryHandleCommand;
window.WiziWakeLock = { request: requestWakeLock, release: releaseWakeLock };
