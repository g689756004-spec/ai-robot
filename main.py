import logging
import base64

from fastapi import FastAPI, WebSocket, UploadFile, File
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

import uvicorn

from config import settings
from groq import Groq
from task_orchestrator import orchestrator
from groq_vision import GroqVisionAnalyzer


# ---------------------------------------------------
# LOGGING
# ---------------------------------------------------

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# ---------------------------------------------------
# FASTAPI APP
# ---------------------------------------------------

app = FastAPI(
    title="AI Robot Agent Backend",
    description="Backend for autonomous AI agent on Android tablet",
    version="0.1.0"
)


# ---------------------------------------------------
# CORS
# ---------------------------------------------------

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------
# GLOBALS
# ---------------------------------------------------

vision_analyzer = GroqVisionAnalyzer()
active_connections = []

groq_client = Groq(api_key=settings.groq_api_key)

# 🔥 FIXED MODEL (IMPORTANT)
GROQ_CHAT_MODEL = "llama-3.1-8b-instant"


# ---------------------------------------------------
# CHAT MODEL
# ---------------------------------------------------

class ChatRequest(BaseModel):
    message: str


# ---------------------------------------------------
# ROOT
# ---------------------------------------------------

@app.get("/")
async def root():
    return {
        "status": "running",
        "service": "AI Robot Agent Backend",
        "version": "0.1.0"
    }


@app.get("/health")
async def health():
    return {"status": "healthy"}


# ---------------------------------------------------
# CHAT ENDPOINT (FIXED)
# ---------------------------------------------------

@app.post("/chat")
async def chat(request: ChatRequest):

    try:
        response = groq_client.chat.completions.create(
            model=GROQ_CHAT_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "You are a smart, friendly, human-like AI assistant. "
                        "Talk naturally, like a real person. Keep responses short and helpful."
                    )
                },
                {
                    "role": "user",
                    "content": request.message
                }
            ],
            temperature=0.7,
            max_tokens=300
        )

        reply = response.choices[0].message.content

        return {"reply": reply}

    except Exception as e:
        logger.error(f"Chat error: {str(e)}")
        return {"error": str(e)}


# ---------------------------------------------------
# SCREEN ANALYSIS
# ---------------------------------------------------

@app.post("/api/analyze-screen")
async def analyze_screen(file: UploadFile = File(...), context: str = None):

    try:
        contents = await file.read()
        screenshot_base64 = base64.b64encode(contents).decode("utf-8")

        analysis = vision_analyzer.analyze_screen(
            screenshot_base64,
            context
        )

        return JSONResponse(content=analysis)

    except Exception as e:
        logger.error(f"Analyze screen error: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/api/extract-text")
async def extract_text(file: UploadFile = File(...)):

    try:
        contents = await file.read()
        screenshot_base64 = base64.b64encode(contents).decode("utf-8")

        text = vision_analyzer.extract_text(screenshot_base64)

        return JSONResponse(content={"text": text})

    except Exception as e:
        logger.error(f"Extract text error: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/api/understand-layout")
async def understand_layout(file: UploadFile = File(...)):

    try:
        contents = await file.read()
        screenshot_base64 = base64.b64encode(contents).decode("utf-8")

        layout = vision_analyzer.understand_layout(screenshot_base64)

        return JSONResponse(content=layout)

    except Exception as e:
        logger.error(f"Understand layout error: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})


# ---------------------------------------------------
# TASK MANAGEMENT
# ---------------------------------------------------

@app.post("/api/task/create")
async def create_task(task_id: str, goal: str, user_command: str):

    try:
        task = orchestrator.create_task(task_id, goal, user_command)
        return JSONResponse(content=task.to_dict())

    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.get("/api/task/{task_id}/status")
async def get_task_status(task_id: str):

    try:
        status = orchestrator.get_task_status(task_id)

        if not status:
            return JSONResponse(status_code=404, content={"error": "Task not found"})

        return JSONResponse(content=status)

    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/api/task/{task_id}/cancel")
async def cancel_task(task_id: str):

    try:
        orchestrator.cancel_task(task_id)
        return {"status": "cancelled", "task_id": task_id}

    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


# ---------------------------------------------------
# WEBSOCKET
# ---------------------------------------------------

@app.websocket("/ws/agent")
async def websocket_endpoint(websocket: WebSocket):

    await websocket.accept()
    active_connections.append(websocket)

    logger.info("WebSocket client connected")

    try:
        while True:

            data = await websocket.receive_json()
            message_type = data.get("type")

            if message_type == "screenshot":

                analysis = vision_analyzer.analyze_screen(
                    data.get("screenshot"),
                    data.get("context")
                )

                await websocket.send_json({
                    "type": "action",
                    "analysis": analysis
                })

            elif message_type == "action_result":

                orchestrator.log_step_result(
                    data.get("task_id"),
                    data.get("result"),
                    error=(data.get("status") == "failed")
                )

                next_action = orchestrator.execute_next_action(data.get("task_id"))

                if next_action:
                    await websocket.send_json({
                        "type": "action",
                        "action": next_action.to_dict()
                    })
                else:
                    await websocket.send_json({
                        "type": "task_complete"
                    })

            elif message_type == "ping":
                await websocket.send_json({"type": "pong"})

    except Exception as e:
        logger.error(f"WebSocket error: {str(e)}")

    finally:
        if websocket in active_connections:
            active_connections.remove(websocket)


# ---------------------------------------------------
# RUN
# ---------------------------------------------------

if __name__ == "__main__":
    uvicorn.run(
        app,
        host=settings.host,
        port=settings.port,
        log_level="info"
    )
