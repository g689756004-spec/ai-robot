# Wizi

Voice assistant for your tablet. One FastAPI server (Groq for the AI) that

- serves the Wizi web app at `/` (the files in `static/`), and
- is the backend for the Wizi Android app (WebSocket at `/ws/agent`, plus `/api/...`).

## Files

```
main.py            server: routes, WebSocket, duplicate/echo protection
config.py          settings from environment variables
llm.py             Groq calls, automatic fallback to other models
research.py        optional "find me an article about ..." (needs CLAUDE_API_KEY)
articles.py        fetch + extract article text
vision.py          screenshot analysis (Android agent)
tasks.py           multi-step task planner (Android agent)
static/            the web app: index.html, app.js, commands.js, memory.js, style.css, ...
requirements.txt  Dockerfile  render.yaml  .env.example
```

`static/` must stay next to `main.py`.

## Deploy on Render
1. Replace everything in the GitHub repo with these files (keep the `static` folder).
2. Render -> your service -> Environment -> set `GROQ_API_KEY`
   (optional: `CLAUDE_API_KEY` for the article feature).
3. Manual Deploy -> **Clear build cache & deploy**.
4. Open `https://YOUR-SERVICE.onrender.com/health` -> should show `"status":"ok"`.
5. Open `https://YOUR-SERVICE.onrender.com/` on the tablet (Chrome). Allow the microphone.
   If you used Wizi before, fully close it and reopen it once so the new version loads.

The free Render plan sleeps when idle; the first message after a long break can take up to a minute.

## Run locally
```
pip install -r requirements.txt
export GROQ_API_KEY=gsk_...
python main.py        # http://localhost:10000
```

## How the voice loop avoids repeating / answering itself
- The microphone is open only while Wizi is listening; it is closed while thinking and speaking and
  reopens only after speech has really ended plus a short cooldown.
- Each listening session uses only its first result; "hi hi hi" is collapsed to "hi".
- Wizi remembers what it just said; if the mic hears that back, it is ignored.
- Only one window can use the mic at a time.
- The server also drops duplicates, echoes, and anything that arrives while it is still answering
  (HTTP `/chat` and the Android WebSocket alike).
