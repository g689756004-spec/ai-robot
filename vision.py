"""Screenshot analysis: look at the screen, suggest the next action."""

import logging
from typing import Optional

import llm

log = logging.getLogger("wizi.vision")

WAIT = {"type": "wait", "target": None, "params": {}}

PROMPT = """You are an autonomous Android agent controlling a tablet by looking at screenshots.
Analyze the screen and decide the next action.

Context: {context}

Return ONLY valid JSON:
{{
  "analysis": "what is visible on screen",
  "ui_elements": [{{"type": "button", "label": "example", "location": "x,y"}}],
  "next_action": {{
    "type": "click|type|scroll|open_app|open_url|back|home|wait",
    "target": "what to interact with",
    "params": {{}}
  }},
  "confidence": 0.0,
  "reasoning": "why"
}}
Rules: if unsure use wait. Never invent buttons. Use coordinates when possible. Be concise."""


async def analyze_screen(image_b64: str, context: Optional[str] = None) -> dict:
    if not image_b64:
        return {"analysis": "No image received", "next_action": dict(WAIT), "confidence": 0, "error": True}

    try:
        raw = await llm.vision(image_b64, PROMPT.format(context=context or "No context provided"))
        result = llm.parse_json(raw)
        if not isinstance(result.get("next_action"), dict):
            result["next_action"] = dict(WAIT)
        return result
    except Exception as e:
        log.warning("Vision analysis failed: %s", e)
        return {
            "analysis": f"Vision error: {e}",
            "next_action": dict(WAIT),
            "confidence": 0,
            "error": True,
        }
