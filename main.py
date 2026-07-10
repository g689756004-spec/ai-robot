import logging
import base64
from typing import List

from fastapi import FastAPI, WebSocket, UploadFile, File
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

import uvicorn

from config import settings
from groq import Groq
from groq_vision import GroqVisionAnalyzer
from task_orchestrator import orchestrator


logging.basicConfig(
    level=logging.INFO
)

logger = logging.getLogger(__name__)


app = FastAPI(
    title="AI Robot Agent Backend",
    version="1.0"
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


active_connections:List[WebSocket] = []


CHAT_MODEL = "llama-3.1-8b-instant"



class ChatRequest(BaseModel):

    message:str



@app.get("/")
async def root():

    return {
        "status":"running",
        "service":"AI Robot Agent"
    }



@app.get("/health")
async def health():

    return {
        "status":"healthy"
    }




@app.post("/chat")
async def chat(
    request:ChatRequest
):

    try:

        response = groq_client.chat.completions.create(

            model=CHAT_MODEL,

            messages=[

                {
                    "role":"system",
                    "content":
                    "You are an AI robot assistant. Keep answers short."
                },

                {
                    "role":"user",
                    "content":request.message
                }

            ],

            max_tokens=300

        )


        return {
            "reply":
            response
            .choices[0]
            .message
            .content
        }


    except Exception as e:

        return {
            "error":str(e)
        }





@app.post("/api/analyze-screen")
async def analyze_screen(
    file:UploadFile = File(...),
    context:str=None
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


        return result


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
async def task_status(
    task_id:str
):

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


    active_connections.append(
        websocket
    )


    logger.info(
        "Android agent connected"
    )


    try:

        while True:


            data = await websocket.receive_json()


            msg_type = data.get(
                "type"
            )


            logger.info(
                f"Message: {msg_type}"
            )



            #
            # SCREENSHOT FROM ANDROID
            #
            if msg_type == "screenshot":


                image = data.get(
                    "screenshot"
                )


                context = data.get(
                    "context",
                    ""
                )


                analysis = vision_analyzer.analyze_screen(

                    image,

                    context

                )


                action = analysis.get(

                    "next_action",

                    {
                        "type":"wait"
                    }

                )


                await websocket.send_json(

                    {

                        "type":"action",

                        "action":action,

                        "analysis":analysis

                    }

                )





            #
            # VOICE COMMAND
            #
            elif msg_type == "voice_command":


                command = data.get(
                    "message",
                    ""
                )


                task_id = data.get(
                    "task_id"
                )


                logger.info(
                    f"Voice command: {command}"
                )


                if task_id is None:

                    task_id="voice_task"



                orchestrator.create_task(

                    task_id,

                    command,

                    command

                )


                await websocket.send_json(

                    {

                        "type":"task_created",

                        "task_id":task_id

                    }

                )





            #
            # ACTION RESULT FROM DEVICE
            #
            elif msg_type == "action_result":


                task_id=data.get(
                    "task_id"
                )


                success=data.get(
                    "success",
                    False
                )


                result=data.get(
                    "message",
                    ""
                )


                orchestrator.log_step_result(

                    task_id,

                    result,

                    not success

                )


                await websocket.send_json(

                    {

                        "type":"action_ack",

                        "success":success

                    }

                )






            #
            # PING
            #
            elif msg_type=="ping":


                await websocket.send_json(

                    {

                        "type":"pong",

                        "timestamp":
                        data.get(
                            "timestamp"
                        )

                    }

                )





            #
            # UNKNOWN
            #
            else:


                await websocket.send_json(

                    {

                        "type":"error",

                        "message":
                        f"Unknown type {msg_type}"

                    }

                )



    except Exception as e:


        logger.error(
            f"Websocket error {e}"
        )



    finally:


        if websocket in active_connections:

            active_connections.remove(
                websocket
            )


        logger.info(
            "Android disconnected"
        )





if __name__=="__main__":


    uvicorn.run(

        app,

        host=settings.host,

        port=settings.port

    )
