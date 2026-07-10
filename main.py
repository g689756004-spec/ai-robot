import logging
import base64

from fastapi import FastAPI, WebSocket, UploadFile, File
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

import uvicorn

from config import settings
from groq import Groq

from groq_vision import GroqVisionAnalyzer
from task_orchestrator import orchestrator


logging.basicConfig(level=logging.INFO)

logger = logging.getLogger(__name__)


app = FastAPI(
    title="AI Robot Agent Backend",
    description="Voice and AI backend for Android Robot Agent",
    version="0.1.0"
)



app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)



groq_client = Groq(
    api_key=settings.groq_api_key
)


vision_analyzer = GroqVisionAnalyzer()



active_connections = set()



CHAT_MODEL = settings.groq_chat_model



class ChatRequest(BaseModel):

    message: str





@app.get("/")
async def root():

    return {
        "status": "running",
        "service": "AI Robot Backend",
        "mode": "voice"
    }





@app.get("/health")
async def health():

    return {
        "status": "healthy"
    }





def ask_ai(message: str):

    try:

        response = groq_client.chat.completions.create(

            model=CHAT_MODEL,

            messages=[

                {
                    "role": "system",
                    "content":
                    """
                    You are an AI robot assistant.
                    Speak naturally.
                    Keep answers concise because they will be spoken aloud.
                    """
                },

                {
                    "role": "user",
                    "content": message
                }

            ],

            temperature=0.7,

            max_tokens=300

        )


        return response.choices[0].message.content



    except Exception as e:

        logger.error(
            f"AI error: {e}"
        )

        return "Sorry, I had a problem processing that."







@app.post("/chat")
async def chat(request: ChatRequest):

    reply = ask_ai(
        request.message
    )


    return {

        "reply": reply

    }







@app.post("/api/analyze-screen")
async def analyze_screen(

        file: UploadFile = File(...),

        context: str = None

):

    try:

        image = await file.read()


        encoded = base64.b64encode(
            image
        ).decode()


        result = vision_analyzer.analyze_screen(

            encoded,

            context

        )


        return JSONResponse(
            content=result
        )


    except Exception as e:


        return JSONResponse(

            status_code=500,

            content={
                "error":str(e)
            }

        )







@app.post("/api/task/create")
async def create_task(

        task_id:str,

        goal:str,

        user_command:str

):

    task = orchestrator.create_task(

        task_id,

        goal,

        user_command

    )


    return task.to_dict()







@app.get("/api/task/{task_id}/status")
async def task_status(task_id:str):


    task = orchestrator.get_task_status(
        task_id
    )


    if task is None:

        return JSONResponse(

            status_code=404,

            content={
                "error":"Task not found"
            }

        )


    return task







@app.websocket("/ws/agent")
async def websocket_agent(

        websocket:WebSocket

):


    await websocket.accept()


    active_connections.add(
        websocket
    )


    logger.info(
        "Android connected"
    )


    try:


        while True:


            data = await websocket.receive_json()


            message_type = data.get(
                "type"
            )


            logger.info(
                f"Received: {message_type}"
            )





            # =========================
            # VOICE INPUT
            # =========================

            if message_type == "voice_command":


                user_text = data.get(
                    "message",
                    ""
                )


                logger.info(
                    f"User said: {user_text}"
                )


                reply = ask_ai(
                    user_text
                )


                await websocket.send_json(

                    {

                        "type":
                        "chat_response",

                        "message":
                        reply,

                        "timestamp":
                        int(
                            __import__("time").time()*1000
                        )

                    }

                )







            # =========================
            # SCREEN ANALYSIS FUTURE
            # =========================

            elif message_type == "screenshot":


                analysis = vision_analyzer.analyze_screen(

                    data.get(
                        "screenshot"
                    ),

                    data.get(
                        "context"
                    )

                )


                await websocket.send_json(

                    {

                        "type":
                        "action",

                        "action":
                        analysis.get(

                            "next_action",

                            {
                                "type":"wait"
                            }

                        ),

                        "analysis":
                        analysis

                    }

                )







            # =========================
            # ACTION RESULT
            # =========================

            elif message_type == "action_result":


                task_id = data.get(
                    "task_id"
                )


                orchestrator.log_step_result(

                    task_id,

                    data.get(
                        "result",
                        ""
                    ),

                    data.get(
                        "status"
                    )=="failed"

                )






            # =========================
            # HEARTBEAT
            # =========================

            elif message_type == "ping":


                await websocket.send_json(

                    {

                        "type":"pong"

                    }

                )





    except Exception as e:


        logger.error(
            f"WebSocket error: {e}"
        )



    finally:


        active_connections.discard(
            websocket
        )


        logger.info(
            "Android disconnected"
        )







if __name__ == "__main__":


    uvicorn.run(

        app,

        host=settings.host,

        port=settings.port

    )
