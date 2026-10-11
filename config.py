"""Wizi backend settings. Everything comes from environment variables
(set them in the Render dashboard). Nothing here can crash at import time."""

import os


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


# --- Groq (chat + vision) ---------------------------------------------------
GROQ_API_KEY = _env("GROQ_API_KEY")
GROQ_MODEL = _env("GROQ_MODEL", "openai/gpt-oss-120b")

# If one model is rate-limited or unavailable, the next one is tried.
# Each Groq model has its own quota, so this makes "rate limit" rare.
GROQ_FALLBACK_MODELS = [
    "openai/gpt-oss-120b",
    "llama-3.3-70b-versatile",
    "openai/gpt-oss-20b",
    "llama-3.1-8b-instant",
]

GROQ_VISION_MODEL = _env("GROQ_VISION_MODEL", "meta-llama/llama-4-scout-17b-16e-instruct")

# --- Claude (optional: only used by the "find me an article" feature) -------
CLAUDE_API_KEY = _env("CLAUDE_API_KEY")
CLAUDE_MODEL = _env("CLAUDE_MODEL", "claude-sonnet-5-5")

# --- Behaviour --------------------------------------------------------------
MAX_HISTORY_TURNS = 12
MAX_MEMORIES = 50
REQUEST_TIMEOUT = 30          # seconds, per model attempt

# Duplicate / echo protection (see main.py)
DUPLICATE_WINDOW_SEC = 8      # the same phrase again within this time = duplicate
ECHO_WINDOW_SEC = 25          # text that matches Wizi's last reply = it heard itself
