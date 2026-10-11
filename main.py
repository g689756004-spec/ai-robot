"""
Wizi backend  (FastAPI)

  GET  /health               status check
  POST /chat                 text chat        {message, history, memories} -> {reply}
  POST /summarize            summarize a link or text -> {summary, title}
  POST /api/research         find + summarize a trusted article (needs CLAUDE_API_KEY)
  POST /api/analyze-screen   screenshot -> next action
  POST /api/task/create      plan a multi-step task
  GET  /api/task/{id}/next-action | /status
  WS   /ws/agent             live voice channel for the Android app
  GET  /                     the Wizi web app (files in ./static)

Voice input is protected against two real-world problems:
  * the same phrase arriving several times (duplicate), and
  * Wizi hearing its OWN spoken reply through the mic and answering it (echo).
Both are dropped here on the server, whatever the client does.
"""

import asyncio
import difflib
import json
import logging
import os
import re
import time
from typing import Optional

from fastapi import FastAPI, File, Form, Request, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

import articles
import config
import llm
import research
import tasks
import vision

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("wizi")

APP_VERSION = "6"      # must match APP_VERSION in static/app.js

app = FastAPI(title="Wizi backend", version="2.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def _startup():
    if not config.GROQ_API_KEY:
        log.warning("GROQ_API_KEY is NOT set - chat will not work until you add it in Render.")
    log.info("Wizi started. model=%s research=%s", config.GROQ_MODEL, research.available())


@app.on_event("shutdown")
async def _shutdown():
    await llm.http().aclose()


# ---------------------------------------------------------------------------
# Personality
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = (
    "Your name is Wizi. You are a voice assistant that lives on the user's "
    "tablet as an installed app, built for spoken conversation and real "
    "device actions. You are NOT ChatGPT, GPT, or any OpenAI product, even "
    "though the model you're running on may have been released by OpenAI - "
    "never introduce yourself as ChatGPT. Always identify yourself only as Wizi.\n\n"
    "Your actual capabilities, if asked what you can do: having spoken "
    "conversations; opening apps and websites by voice ('open Gmail', "
    "'open YouTube'); doing web searches by voice; setting real timers and "
    "alarms on the device's clock; opening the phone dialer or a text "
    "message pre-filled for the user to send; summarizing articles the "
    "user shares to you from any other app; finding and summarizing news "
    "articles from trusted sources; sharing or copying your last reply to "
    "another app; checking the user's current location; and remembering "
    "facts the user explicitly asks you to remember, across sessions. "
    "Describe only these real capabilities.\n\n"
    "Keep replies short and natural, like something a person would say out "
    "loud - usually 1 to 3 sentences. Never use markdown, bullet points, "
    "asterisks, or headers, since your reply is converted directly to "
    "speech. Spell out numbers and abbreviations the way you'd say them aloud."
)


def build_messages(text: str, history=None, memories=None) -> list:
    system = SYSTEM_PROMPT

    facts = [str(m).strip() for m in (memories or []) if str(m).strip()][: config.MAX_MEMORIES]
    if facts:
        system += (
            "\n\nThings the user has told you to remember about them: "
            + "; ".join(facts)
            + ". Use these naturally when relevant, but don't force them into every reply."
        )

    clean = []
    for turn in (history or [])[-config.MAX_HISTORY_TURNS:]:
        if not isinstance(turn, dict):
            continue
        role, content = turn.get("role"), turn.get("content")
        if role in ("user", "assistant") and isinstance(content, str) and content.strip():
            clean.append({"role": role, "content": content.strip()})

    # The web app already includes the current message at the end of history.
    if clean and clean[-1]["role"] == "user" and clean[-1]["content"] == text:
        clean.pop()

    return [{"role": "system", "content": system}, *clean, {"role": "user", "content": text}]


# ---------------------------------------------------------------------------
# Duplicate / echo / busy protection
# ---------------------------------------------------------------------------
# One "client state" per connected client (browser id or WebSocket). It makes
# sure that, whatever the client does:
#   * a phrase that arrives again right after (duplicate) is dropped,
#   * a phrase that arrives while the previous one is still being answered
#     (busy) is dropped, and
#   * text that is just Wizi's own last reply coming back through the
#     microphone (echo) is dropped.

STOPWORDS = set(
    "a an the and or but if so to of in on at for with is are was were be been am it its "
    "i you he she we they me my your this that these those what who how why when where do "
    "does did can could would should will just yes no ok okay hi hello hey please".split()
)


def norm(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9 ]+", " ", (text or "").lower())).strip()


def looks_like_echo(heard: str, reply: str) -> bool:
    """heard/reply are already normalised."""
    if not heard or not reply:
        return False
    words = heard.split()
    if len(words) >= 2 and heard in reply:
        return True
    content = [w for w in words if w not in STOPWORDS]
    if len(content) >= 2:
        reply_words = set(reply.split())
        if sum(1 for w in content if w in reply_words) / len(content) >= 0.65:
            return True
    if len(words) >= 3 and words[:3] == reply.split()[:3]:
        return True                       # starts exactly like the reply
    if len(words) >= 4 and difflib.SequenceMatcher(None, heard, reply).ratio() > 0.8:
        return True
    return False


def new_state() -> dict:
    return {
        "last_user": "", "last_reply": "", "last_done_at": 0.0,
        "busy": False, "busy_since": 0.0,
        "last_turn": "", "last_result": None,
    }


BUSY_TIMEOUT_SEC = 90          # a request stuck longer than this stops blocking new ones


def echo_window(state: dict) -> float:
    # Long replies take longer to speak, so they can be "heard back" for longer.
    return max(config.ECHO_WINDOW_SEC, 8 + 0.6 * len(state["last_reply"].split()))


def should_skip(state: dict, text: str) -> str:
    """Returns "empty", "busy", "duplicate", "echo", or "" (fine to process)."""
    now = time.time()
    t = norm(text)
    if not t:
        return "empty"

    if state["busy"] and now - state["busy_since"] < BUSY_TIMEOUT_SEC:
        return "busy"

    if t == state["last_user"] and now - state["last_done_at"] < config.DUPLICATE_WINDOW_SEC:
        return "duplicate"

    if state["last_reply"] and now - state["last_done_at"] < echo_window(state):
        if looks_like_echo(t, state["last_reply"]):
            return "echo"
    return ""


def claim(state: dict, text: str) -> None:
    now = time.time()
    state["busy"] = True
    state["busy_since"] = now
    state["last_user"] = norm(text)
    state["last_done_at"] = now


def release(state: dict, reply: Optional[str] = None, failed: bool = False) -> None:
    state["busy"] = False
    state["last_done_at"] = time.time()
    if failed:
        state["last_user"] = ""            # let the user retry the same phrase right away
    elif reply is not None:
        state["last_reply"] = norm(reply)


# HTTP clients: one state per client_id, bounded so memory can't grow forever.
_client_states: dict = {}
_MAX_CLIENTS = 500


def state_for(cid: str) -> dict:
    st = _client_states.get(cid)
    if st is None:
        if len(_client_states) >= _MAX_CLIENTS:
            _client_states.pop(next(iter(_client_states)))
        st = _client_states[cid] = new_state()
    return st


def client_id(request: Request) -> str:
    fwd = request.headers.get("x-forwarded-for", "")
    return fwd.split(",")[0].strip() or (request.client.host if request.client else "?")


# ---------------------------------------------------------------------------
# Shared brain: used by /chat and the WebSocket
# ---------------------------------------------------------------------------

RESEARCH_HINT = re.compile(
    r"\b(article|articles|news story|news about|read (me )?(about|up on)|"
    r"summari[sz]e|look up|find (me )?(a |an |the )?(story|news))\b",
    re.I,
)

CLASSIFY_PROMPT = (
    "Classify the user's message. If they are asking to find, look up, search for, read, "
    "or get a summary of an article, news story, or piece of writing about some topic, "
    'respond with exactly: {"intent": "research", "topic": "<clean search query>"}. '
    'For anything else respond with exactly: {"intent": "chat"}. Respond with ONLY the JSON.'
)


async def detect_research_topic(text: str) -> Optional[str]:
    # Cheap checks first, so ordinary chat never pays for an extra model call.
    if not research.available() or not RESEARCH_HINT.search(text):
        return None
    try:
        raw, _ = await llm.chat(
            [{"role": "system", "content": CLASSIFY_PROMPT}, {"role": "user", "content": text}],
            temperature=0, max_tokens=150, json_mode=True,
        )
        result = llm.parse_json(raw)
        if result.get("intent") == "research" and result.get("topic"):
            return str(result["topic"])
    except Exception as e:
        log.info("Intent check failed, treating as chat: %s", e)
    return None


async def respond(text: str, history=None, memories=None) -> dict:
    """Returns {"reply": str, "model": str, "research": {...}?}. Raises llm.LLMError."""
    topic = await detect_research_topic(text)
    if topic:
        try:
            result = await research.research_and_summarize(topic)
            return {"reply": result["summary"], "model": config.CLAUDE_MODEL, "research": result}
        except research.ResearchError as e:
            log.warning("Research failed: %s", e)
            # fall through to a normal answer instead of dead-ending

    reply, model = await llm.chat(build_messages(text, history, memories))
    return {"reply": llm.strip_for_speech(reply), "model": model}


# ---------------------------------------------------------------------------
# HTTP routes
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {
        "status": "ok",
        "version": APP_VERSION,
        "groq_configured": bool(config.GROQ_API_KEY),
        "model": config.GROQ_MODEL,
        "research_enabled": research.available(),
    }


async def read_json(request: Request) -> dict:
    try:
        data = await request.json()
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


@app.post("/chat")
async def chat(request: Request):
    data = await read_json(request)
    text = (data.get("message") or "").strip()
    history = data.get("history") or []
    memories = data.get("memories") or []

    if not text:
        return JSONResponse({"error": "message is required"}, status_code=400)
    if not isinstance(history, list):
        return JSONResponse({"error": "history must be a list"}, status_code=400)
    if not isinstance(memories, list):
        memories = []

    cid = str(data.get("client_id") or client_id(request))[:80]
    turn_id = str(data.get("turn_id") or "")[:80]
    state = state_for(cid)

    # The very same turn sent twice (network retry): give back the same answer.
    if turn_id and turn_id == state["last_turn"] and state["last_result"]:
        return state["last_result"]

    skip = should_skip(state, text)
    if skip:
        log.info("Ignored /chat (%s): %r", skip, text)
        return {"reply": "", "ignored": skip}

    claim(state, text)
    try:
        result = await respond(text, history, memories)
    except llm.LLMError as e:
        release(state, failed=True)
        return JSONResponse({"error": e.message}, status_code=e.status)
    except Exception:
        release(state, failed=True)
        log.exception("Chat failed")
        return JSONResponse({"error": "The assistant is unavailable right now. Please try again."}, status_code=502)

    release(state, reply=result["reply"])
    state["last_turn"] = turn_id
    state["last_result"] = result
    return result


@app.post("/summarize")
async def summarize(request: Request):
    data = await read_json(request)
    url = (data.get("url") or "").strip()
    raw_text = (data.get("text") or "").strip()

    if not url and not raw_text:
        return JSONResponse({"error": "Provide a url or text to summarize"}, status_code=400)

    try:
        if url:
            title, body = await articles.fetch_article(url)
        else:
            title, body = "shared text", raw_text[:8000]

        prompt = (
            "Summarize the following in 3 to 4 spoken sentences - no markdown, no bullet "
            f"points, just something natural to say out loud. Title: {title}\n\nText:\n{body}"
        )
        summary, model = await llm.chat(
            [{"role": "system", "content": SYSTEM_PROMPT}, {"role": "user", "content": prompt}]
        )
    except articles.ArticleError as e:
        return JSONResponse({"error": str(e)}, status_code=422)
    except llm.LLMError as e:
        return JSONResponse({"error": e.message}, status_code=e.status)
    except Exception:
        log.exception("Summarize failed")
        return JSONResponse({"error": "Something went wrong summarizing that. Please try again."}, status_code=502)

    return {"summary": llm.strip_for_speech(summary), "title": title, "model": model}




@app.post("/api/research")
async def api_research(request: Request):
    data = await read_json(request)
    topic = (data.get("query") or data.get("topic") or "").strip()
    try:
        return await research.research_and_summarize(topic)
    except research.ResearchError as e:
        return JSONResponse({"error": str(e)}, status_code=502)


@app.post("/api/analyze-screen")
async def api_analyze_screen(
    request: Request,
    file: UploadFile = File(...),
    context: Optional[str] = Form(None),
):
    import base64

    context = context or request.query_params.get("context")
    image = base64.b64encode(await file.read()).decode()
    return await vision.analyze_screen(image, context)


@app.post("/api/task/create")
async def api_task_create(request: Request):
    data = await read_json(request)
    task_id = str(data.get("task_id") or "").strip()
    if not task_id:
        return JSONResponse({"error": "task_id is required"}, status_code=400)
    task = await tasks.create_plan(task_id, str(data.get("goal", "")), str(data.get("user_command", "")))
    return task.to_dict()


@app.get("/api/task/{task_id}/next-action")
async def api_task_next(task_id: str):
    action = tasks.next_action(task_id)
    if action is None:
        return JSONResponse({"error": "No task found or no remaining steps"}, status_code=404)
    return action


@app.get("/api/task/{task_id}/status")
async def api_task_status(task_id: str):
    task = tasks.get(task_id)
    if task is None:
        return JSONResponse({"error": "Task not found"}, status_code=404)
    return task.to_dict()


# ---------------------------------------------------------------------------
# WebSocket for the Android app
# ---------------------------------------------------------------------------

@app.websocket("/ws/agent")
async def ws_agent(ws: WebSocket):
    await ws.accept()
    log.info("Android connected")

    state = new_state()
    send_lock = asyncio.Lock()     # one writer at a time
    work_lock = asyncio.Lock()     # answer one request at a time, in order
    pending: set = set()

    async def send(payload: dict):
        async with send_lock:
            await ws.send_json(payload)

    async def handle_voice(text: str):
        async with work_lock:
            failed = True
            try:
                result = await respond(text)
                release(state, reply=result["reply"])
                failed = False

                payload = {
                    "type": "chat_response",
                    "message": result["reply"],
                    "timestamp": int(time.time() * 1000),
                }
                found = result.get("research")
                if found:
                    payload["action"] = {"type": "open_url", "target": found["url"], "params": {}}
                    payload["research"] = found
                await send(payload)

            except llm.LLMError as e:
                await send({"type": "chat_response", "message": e.message})
            except WebSocketDisconnect:
                pass
            except Exception:
                log.exception("voice_command failed")
                try:
                    await send({"type": "chat_response", "message": "Sorry, I had a problem processing that."})
                except Exception:
                    pass
            finally:
                if failed:
                    release(state, failed=True)

    async def handle_research(topic: str):
        async with work_lock:
            try:
                r = await research.research_and_summarize(topic)
                state["last_reply"] = norm(r["summary"])
                state["last_done_at"] = time.time()
                await send({
                    "type": "research_result",
                    "title": r["title"], "url": r["url"], "source": r.get("source"),
                    "summary": r["summary"],
                    "action": {"type": "open_url", "target": r["url"], "params": {}},
                })
            except research.ResearchError as e:
                log.warning("research_command failed: %s", e)
                await send({"type": "chat_response", "message": "Sorry, I couldn't find a good, trustworthy article on that."})
            except Exception:
                log.exception("research_command failed")

    async def handle_screenshot(data: dict):
        async with work_lock:
            analysis = await vision.analyze_screen(data.get("screenshot"), data.get("context"))
            await send({
                "type": "action",
                "action": analysis.get("next_action") or {"type": "wait"},
                "analysis": analysis,
            })

    def spawn(coro):
        t = asyncio.create_task(coro)
        pending.add(t)
        t.add_done_callback(pending.discard)

    try:
        while True:
            raw = await ws.receive_text()
            try:
                data = json.loads(raw)
                kind = data.get("type")
            except Exception:
                continue                                  # ignore garbage, keep the socket open

            if kind == "ping":
                await send({"type": "pong"})

            elif kind == "voice_command":
                text = (data.get("message") or "").strip()
                log.info("User said: %r", text)

                skip = should_skip(state, text)
                if skip:
                    log.info("Ignored voice_command (%s): %r", skip, text)
                    await send({"type": "ignored", "reason": skip})
                    continue

                # Claim immediately, so copies arriving while this one is being answered are dropped.
                claim(state, text)
                spawn(handle_voice(text))

            elif kind == "research_command":
                spawn(handle_research(data.get("topic") or data.get("message") or ""))

            elif kind == "screenshot":
                spawn(handle_screenshot(data))

            elif kind == "action_result":
                tasks.log_result(
                    data.get("task_id", ""),
                    str(data.get("result", "")),
                    data.get("status") == "failed",
                )

    except WebSocketDisconnect:
        pass
    except Exception as e:
        log.warning("WebSocket error: %s", e)
    finally:
        for t in list(pending):
            t.cancel()
        log.info("Android disconnected")


# ---------------------------------------------------------------------------
# The web app (served last, so it never shadows an API route)
# ---------------------------------------------------------------------------

STATIC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "static")


class NoCacheStatic(StaticFiles):
    """Always revalidate, so a new app.js reaches the tablet immediately."""

    async def get_response(self, path, scope):
        resp = await super().get_response(path, scope)
        resp.headers["Cache-Control"] = "no-cache"
        return resp


if os.path.isdir(STATIC_DIR):
    app.mount("/", NoCacheStatic(directory=STATIC_DIR, html=True), name="web")
else:
    log.warning("static/ folder not found next to main.py - the web app will not be served. "
                "Make sure the static folder was uploaded to GitHub together with main.py.")

    @app.get("/")
    async def root():
        return {"status": "running", "service": "Wizi backend", "note": "static/ folder is missing"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "10000")))
