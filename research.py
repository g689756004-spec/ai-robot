"""Optional 'find me an article about X' feature. Needs CLAUDE_API_KEY.

1. Claude (with web search limited to trusted sites) picks the best article.
2. We fetch that page ourselves and Claude summarizes the full text.
If CLAUDE_API_KEY isn't set, the feature is simply off and Wizi chats normally.
"""

import logging

import config
import llm
from articles import ArticleError, fetch_article

log = logging.getLogger("wizi.research")

TRUSTED_DOMAINS = [
    "reuters.com", "apnews.com", "bbc.com", "bbc.co.uk", "npr.org",
    "theguardian.com", "aljazeera.com", "economist.com",
    "nature.com", "science.org", "scientificamerican.com",
    "arstechnica.com", "wired.com", "technologyreview.com",
    "wikipedia.org", "britannica.com", "nih.gov", "nasa.gov", "who.int",
]

ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"


class ResearchError(Exception):
    pass


def available() -> bool:
    return bool(config.CLAUDE_API_KEY)


async def _claude(payload: dict) -> str:
    if not available():
        raise ResearchError("CLAUDE_API_KEY is not set.")
    try:
        resp = await llm.http().post(
            ANTHROPIC_URL,
            headers={
                "x-api-key": config.CLAUDE_API_KEY,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
            },
            json={"model": config.CLAUDE_MODEL, **payload},
            timeout=60,
        )
    except Exception as e:
        raise ResearchError(f"Claude request failed: {e}")

    if resp.status_code != 200:
        log.warning("Claude %s: %s", resp.status_code, resp.text[:300])
        raise ResearchError(f"Claude returned {resp.status_code}")

    blocks = resp.json().get("content", [])
    return "".join(b.get("text", "") for b in blocks if b.get("type") == "text")


async def research_and_summarize(topic: str) -> dict:
    topic = (topic or "").strip()
    if not topic:
        raise ResearchError("No topic given.")

    # 1) pick one trusted article
    text = await _claude({
        "max_tokens": 1024,
        "tools": [{
            "type": "web_search_20250305",
            "name": "web_search",
            "max_uses": 3,
            "allowed_domains": TRUSTED_DOMAINS,
        }],
        "messages": [{
            "role": "user",
            "content": (
                f"Search for the single best, most current, most trustworthy article about: {topic}\n\n"
                "Reply with ONLY this JSON and nothing else:\n"
                '{"title": "...", "url": "...", "source": "..."}'
            ),
        }],
    })
    try:
        pick = llm.parse_json(text)
        url = pick["url"]
    except Exception:
        raise ResearchError("Couldn't pick an article.")

    # 2) read the whole page
    try:
        page_title, body = await fetch_article(url, max_chars=12000)
    except ArticleError as e:
        raise ResearchError(str(e))

    # 3) summarize the full text for speaking aloud
    summary = (await _claude({
        "max_tokens": 1200,
        "messages": [{
            "role": "user",
            "content": (
                "Summarize this article for reading aloud: clear spoken paragraphs, no bullets, "
                "no markdown. Keep the concrete facts (names, numbers, dates, conclusions). "
                "Don't add anything the article doesn't say.\n\n"
                f"Title: {pick.get('title') or page_title}\n\nArticle:\n{body}"
            ),
        }],
    })).strip()
    if not summary:
        raise ResearchError("Empty summary.")

    return {
        "title": pick.get("title") or page_title,
        "url": url,
        "source": pick.get("source"),
        "summary": llm.strip_for_speech(summary),
    }
