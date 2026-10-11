// Persistent memory — built by Jordan (Core logic)
// ----------------------------------------------------
// Two kinds of memory, both stored on-device (localStorage), so the
// assistant remembers things even after you close the app and reopen it
// days later — nothing is sent anywhere except as part of normal /chat
// requests to your own backend.
//
//  1. Conversation history — the actual back-and-forth, so reopening the
//     app shows your prior conversation instead of a blank slate.
//  2. Taught facts — explicit things you asked it to remember ("remember
//     that my dog's name is Max"), sent with every request so the AI can
//     use them naturally without you repeating yourself.
//
// Note: this is per-device memory (this tablet only), not synced across
// devices — there's no account system here, on purpose, to keep this free
// and simple. If you use the app on a different device, it starts fresh.

const HISTORY_KEY = "wizi:history";
const FACTS_KEY = "wizi:facts";
const SETTINGS_KEY = "wizi:settings";
const MAX_STORED_MESSAGES = 60; // ~30 turns — keeps storage and prompt size bounded

const WiziMemory = {
  loadHistory() {
    try {
      const raw = localStorage.getItem(HISTORY_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (err) {
      console.warn("Couldn't load conversation memory", err);
      return [];
    }
  },

  saveHistory(history) {
    try {
      const trimmed = history.slice(-MAX_STORED_MESSAGES);
      localStorage.setItem(HISTORY_KEY, JSON.stringify(trimmed));
    } catch (err) {
      console.warn("Couldn't save conversation memory", err);
    }
  },

  clearHistory() {
    localStorage.removeItem(HISTORY_KEY);
  },

  loadFacts() {
    try {
      const raw = localStorage.getItem(FACTS_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (err) {
      console.warn("Couldn't load taught facts", err);
      return [];
    }
  },

  addFact(fact) {
    const facts = WiziMemory.loadFacts();
    facts.push(fact.trim());
    try {
      localStorage.setItem(FACTS_KEY, JSON.stringify(facts.slice(-50)));
    } catch (err) {
      console.warn("Couldn't save taught fact", err);
    }
  },

  clearFacts() {
    localStorage.removeItem(FACTS_KEY);
  },

  clearAll() {
    WiziMemory.clearHistory();
    WiziMemory.clearFacts();
  },

  // Small persisted preferences — e.g. whether "always listening" mode
  // should be on by default the next time the app opens.
  loadSettings() {
    try {
      const raw = localStorage.getItem(SETTINGS_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (err) {
      return {};
    }
  },

  saveSetting(key, value) {
    const settings = WiziMemory.loadSettings();
    settings[key] = value;
    try {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    } catch (err) {
      console.warn("Couldn't save setting", err);
    }
  },
};

window.WiziMemory = WiziMemory;
