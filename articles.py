"""Fetch a web page and pull out its readable text (used by /summarize and research)."""

import ipaddress
import socket
from typing import Tuple
from urllib.parse import urlparse

from bs4 import BeautifulSoup

from llm import http


class ArticleError(Exception):
    pass


def is_public_url(url: str) -> bool:
    """Only allow http(s) URLs that point at the public internet (not localhost/LAN)."""
    try:
        parts = urlparse(url)
        if parts.scheme not in ("http", "https") or not parts.hostname:
            return False
        for info in socket.getaddrinfo(parts.hostname, None):
            ip = ipaddress.ip_address(info[4][0])
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
                return False
        return True
    except Exception:
        return False


async def fetch_article(url: str, max_chars: int = 8000) -> Tuple[str, str]:
    """Returns (title, text). Raises ArticleError with a user-safe message."""
    if not is_public_url(url):
        raise ArticleError("That link doesn't look like a public web page.")

    try:
        resp = await http().get(
            url,
            timeout=15,
            headers={"User-Agent": "Mozilla/5.0 (compatible; WiziBot/1.0)"},
        )
        resp.raise_for_status()
    except Exception:
        raise ArticleError("Couldn't fetch that page. It may be blocking automated requests.")

    soup = BeautifulSoup(resp.text, "html.parser")
    title = soup.title.string.strip() if soup.title and soup.title.string else url

    for tag in soup(["script", "style", "nav", "footer", "header", "aside", "form"]):
        tag.decompose()

    container = soup.find("article") or soup.find("main") or soup.body or soup
    paragraphs = [p.get_text(" ", strip=True) for p in container.find_all("p")]
    text = "\n".join(p for p in paragraphs if len(p) > 40)

    if not text:
        text = container.get_text(" ", strip=True)
    if len(text) < 200:
        raise ArticleError("Couldn't find readable article text on that page.")

    return title, text[:max_chars]
