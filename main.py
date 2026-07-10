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


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


app = FastAPI(
    title="AI Robot Agent Backend",
    description="Backend for autonomous Android AI Agent",
    version="0.1.0"
)


app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)


vision_analyzer = GroqVisionAnalyzer()
groq_client = Groq(
    api_key=settings.groq_api_key
)


active_agents = []


CHAT_MODEL = settings.groq_chat_model


class ChatRequest(BaseModel):
    message: str



@app.get("/")
async def root():
    return {
        "status": "running",
        "service": "AI Robot Agent Backend",
        "version": "0.1.0"
    }



@app.get("/health")
async def health():
    return {
        "status": "healthy"
    }



@app.post("/chat")
async def chat(request: ChatRequest):

    try:

        response = groq_client.chat.completions.create(
            model=CHAT_MODEL,
            messages=[
                {
                    "role": "system",
                    "content":
                    "You are an AI robot assistant. Be concise and helpful."
                },
                {
                    "role": "user",
                    "content": request.message
                }
            ],
            temperature=0.7,
            max_tokens=300
        )


        return {
            "reply":
            response.choices[0].message.content
        }


    except Exception as e:

        logger.error(
            f"Chat error: {e}"
        )

        return {
            "error": str(e)
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
                "error": str(e)
            }
        )






@app.post("/api/extract-text")
async def extract_text(
    file: UploadFile = File(...)
):

    try:

        image = await file.read()

        encoded = base64.b64encode(
            image
        ).decode()


        text = vision_analyzer.extract_text(
            encoded
        )


        return {
            "text": text
        }


    except Exception as e:

        return {
            "error": str(e)
        }







@app.websocket("/ws/agent")
async def websocket_agent(
    websocket: WebSocket
):

    await websocket.accept()

    active_agents.append(
        websocket
    )

    logger.info(
        "Android agent connected"
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



            if message_type == "voice_command":


                user_text = data.get(
                    "message",
                    ""
                )


                response = groq_client.chat.completions.create(

                    model=CHAT_MODEL,

                    messages=[

                        {
                            "role": "system",
                            "content":
                            """
                            You are an AI robot assistant.
                            Answer naturally.
                            If the user asks to perform
                            an action, explain briefly.
                            """
                        },

                        {
                            "role": "user",
                            "content": user_text
                        }

                    ],

                    max_tokens=300

                )


                reply = (
                    response
                    .choices[0]
                    .message
                    .content
                )


                await websocket.send_json({

                    "type": "speak",

                    "text": reply

                })




            elif message_type == "screenshot":


                screenshot = data.get(
                    "screenshot"
                )


                context = data.get(
                    "context",
                    ""
                )


                analysis = vision_analyzer.analyze_screen(

                    screenshot,

                    context

                )


                action = analysis.get(

                    "next_action",

                    {
                        "type":
                        "wait"
                    }

                )


                await websocket.send_json({

                    "type":
                    "action",

                    "action":
                    action,

                    "analysis":
                    analysis

                })





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
                    )
                    == "failed"

                )


                next_action = (
                    orchestrator
                    .execute_next_action(
                        task_id
                    )
                )


                if next_action:


                    await websocket.send_json({

                        "type":
                        "action",

                        "action":
                        next_action.to_dict()

                    })


                else:


                    await websocket.send_json({

                        "type":
                        "task_complete"

                    })






            elif message_type == "ping":


                await websocket.send_json({

                    "type":
                    "pong",

                    "timestamp":
                    0

                })




    except Exception as e:


        logger.error(
            f"WebSocket error: {e}"
        )



    finally:


        if websocket in active_agents:

            active_agents.remove(
                websocket
            )


        logger.info(
            "Android agent disconnected"
        )






if __name__ == "__main__":


    uvicorn.run(

        app,

        host=settings.host,

        port=settings.port,

        log_level="info"

    )
