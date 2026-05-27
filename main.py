import logging
import base64
from fastapi import FastAPI, WebSocket, UploadFile, File
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from config import settings
from groq_vision import GroqVisionAnalyzer   # ✅ FIXED HERE
from task_orchestrator import orchestrator

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="AI Robot Agent Backend",
    description="Backend for autonomous AI agent on Android tablet",
    version="0.1.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

vision_analyzer = GroqVisionAnalyzer()
active_connections = []


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


@app.post("/api/analyze-screen")
async def analyze_screen(file: UploadFile = File(...), context: str = None):
    """Analyze a screenshot and return next action recommendation."""
    try:
        contents = await file.read()
        screenshot_base64 = base64.b64encode(contents).decode('utf-8')
        analysis = vision_analyzer.analyze_screen(screenshot_base64, context)
        return JSONResponse(content=analysis)
    except Exception as e:
        logger.error(f"Error analyzing screen: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/api/extract-text")
async def extract_text(file: UploadFile = File(...)):
    """Extract visible text from a screenshot."""
    try:
        contents = await file.read()
        screenshot_base64 = base64.b64encode(contents).decode('utf-8')
        text = vision_analyzer.extract_text_from_screen(screenshot_base64)
        return JSONResponse(content={"text": text})
    except Exception as e:
        logger.error(f"Error extracting text: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/api/understand-layout")
async def understand_layout(file: UploadFile = File(...)):
    """Understand page layout from a screenshot."""
    try:
        contents = await file.read()
        screenshot_base64 = base64.b64encode(contents).decode('utf-8')
        layout = vision_analyzer.understand_page_layout(screenshot_base64)
        return JSONResponse(content=layout)
    except Exception as e:
        logger.error(f"Error understanding layout: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/api/task/create")
async def create_task(task_id: str, goal: str, user_command: str):
    """Create a new task."""
    try:
        task = orchestrator.create_task(task_id, goal, user_command)
        return JSONResponse(content=task.to_dict())
    except Exception as e:
        logger.error(f"Error creating task: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.get("/api/task/{task_id}/status")
async def get_task_status(task_id: str):
    """Get the status of a task."""
    status = orchestrator.get_task_status(task_id)
    if not status:
        return JSONResponse(status_code=404, content={"error": "Task not found"})
    return JSONResponse(content=status)


@app.post("/api/task/{task_id}/cancel")
async def cancel_task(task_id: str):
    """Cancel a running task."""
    orchestrator.cancel_task(task_id)
    return JSONResponse(content={"status": "cancelled", "task_id": task_id})


@app.websocket("/ws/agent")
async def websocket_endpoint(websocket: WebSocket):
    """WebSocket endpoint for real-time Android client communication."""
    await websocket.accept()
    active_connections.append(websocket)
    logger.info("WebSocket client connected")
    
    try:
        while True:
            data = await websocket.receive_json()
            message_type = data.get("type")
            logger.info(f"Received: {message_type}")
            
            if message_type == "screenshot":
                screenshot_base64 = data.get("screenshot")
                task_id = data.get("task_id")
                context = data.get("context")
                
                analysis = vision_analyzer.analyze_screen(screenshot_base64, context)
                
                await websocket.send_json({
                    "type": "action",
                    "task_id": task_id,
                    "analysis": analysis
                })
            
            elif message_type == "action_result":
                task_id = data.get("task_id")
                action_status = data.get("status")
                result = data.get("result")
                
                orchestrator.log_step_result(
                    task_id, result, error=(action_status == "failed")
                )
                
                next_action = orchestrator.execute_next_action(task_id)
                if next_action:
                    await websocket.send_json({
                        "type": "action",
                        "task_id": task_id,
                        "action": next_action.to_dict()
                    })
                else:
                    await websocket.send_json({
                        "type": "task_complete",
                        "task_id": task_id
                    })
            
            elif message_type == "ping":
                await websocket.send_json({"type": "pong"})
    
    except Exception as e:
        logger.error(f"WebSocket error: {str(e)}")
    
    finally:
        if websocket in active_connections:
            active_connections.remove(websocket)
        logger.info("WebSocket client disconnected")


if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port, log_level="info")
