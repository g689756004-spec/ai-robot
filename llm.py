"""Groq chat/vision calls over plain HTTPS (httpx). No SDK needed."""

import json
import logging
import re
from typing import List, Optional, Tuple

import httpx

import config

log = logging.getLogger("wizi.llm")

GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

_client: Optional[httpx.AsyncClient] = None


def http() -> httpx.AsyncClient:
    """One shared connection pool for the whole app."""
    global _client
    if _client is None:
        _client = httpx.AsyncClient(timeout=config.REQUEST_TIMEOUT, follow_redirects=True)
    return _client


class LLMError(Exception):
    """An error whose message is safe to show to the user."""

    def __init__(self, message: str, status: int = 502):
        super().__init__(message)
        self.message = message
        self.status = status


def strip_for_speech(text: str) -> str:
    """Remove markdown so text-to-speech doesn't read out asterisks."""
    text = re.sub(r"[*_`#]+", "", text or "")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _model_chain(primary: Optional[str] = None) -> List[str]:
    first = primary or config.GROQ_MODEL
    return [first] + [m for m in config.GROQ_FALLBACK_MODELS if m != first]


async def chat(
    messages: list,
    *,
    max_tokens: int = 500,
    temperature: float = 0.7,
    json_mode: bool = False,
) -> Tuple[str, str]:
    """Returns (text, model_used). Tries each model in the fallback chain."""
    if not config.GROQ_API_KEY:
        raise LLMError("GROQ_API_KEY is not set on the server.", 500)

    last: Optional[LLMError] = None

    for model in _model_chain():
        body = {
            "model": model,
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
        if model.startswith("openai/gpt-oss"):
            body["reasoning_effort"] = "low"      # keeps answers fast and short
        if json_mode:
            body["response_format"] = {"type": "json_object"}

        try:
            resp = await http().post(
                GROQ_URL,
                headers={"Authorization": f"Bearer {config.GROQ_API_KEY}"},
                json=body,
            )
        except httpx.TimeoutException:
            last = LLMError("The model took too long to respond. Please try again.", 504)
            log.warning("Timeout on %s", model)
            continue
        except httpx.HTTPError as e:
            last = LLMError("Can't reach Groq right now. Check the internet connection.", 502)
            log.warning("Connection error on %s: %s", model, e)
            continue

        if resp.status_code == 401:
            raise LLMError("Invalid GROQ_API_KEY. Check the key set on the server.", 502)

        if resp.status_code != 200:
            if resp.status_code == 429:
                last = LLMError("I'm getting a lot of use right now. Give it a minute and try again.", 429)
            else:
                last = LLMError("The assistant is unavailable right now. Please try again.", 502)
            log.warning("Groq %s on %s: %s", resp.status_code, model, resp.text[:300])
            continue

        try:
            text = (resp.json()["choices"][0]["message"].get("content") or "").strip()
        except Exception:
            text = ""

        if not text:
            log.warning("Empty reply from %s, trying next model", model)
            last = LLMError("The assistant gave an empty answer. Please try again.", 502)
            continue

        return text, model

    raise last or LLMError("The assistant is unavailable right now.", 502)


def parse_json(text: str) -> dict:
    """Pull a JSON object out of a model reply, even if it added fences/prose."""
    text = (text or "").strip()
    text = re.sub(r"^```(?:json)?\s*|\s*```\s*$", "", text)
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if match:
        text = match.group(0)
    return json.loads(text)


async def vision(image_b64: str, system_prompt: str, user_text: Optional[str] = None) -> str:
    """Ask the Groq vision model about an image (base64 JPEG/PNG)."""
    if not config.GROQ_API_KEY:
        raise LLMError("GROQ_API_KEY is not set on the server.", 500)

    content = [{"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}}]
    if user_text:
        content.append({"type": "text", "text": user_text})

    body = {
        "model": config.GROQ_VISION_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": content},
        ],
        "temperature": 0.2,
        "max_tokens": 1000,
        "response_format": {"type": "json_object"},
    }

    for attempt in range(2):
        try:
            resp = await http().post(
                GROQ_URL,
                headers={"Authorization": f"Bearer {config.GROQ_API_KEY}"},
                json=body,
            )
        except httpx.HTTPError as e:
            raise LLMError(f"Vision request failed: {e}", 502)

        if resp.status_code == 400 and attempt == 0 and "response_format" in body:
            body.pop("response_format")          # this model may not support JSON mode
            continue
        if resp.status_code != 200:
            log.warning("Vision %s: %s", resp.status_code, resp.text[:300])
            raise LLMError(f"Vision model error ({resp.status_code}). Check GROQ_VISION_MODEL.", 502)

        return (resp.json()["choices"][0]["message"].get("content") or "").strip()

    raise LLMError("Vision request failed.", 502)
