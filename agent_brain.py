"""
AI Agent Brain
Decision making layer for autonomous Android agent.

Responsibilities:
- Understand user goal
- Combine vision + task state
- Decide next action
- Generate Android compatible commands
"""

import logging
import json
from typing import Dict, Optional, Any

from groq import Groq

from config import settings
from task_orchestrator import (
    orchestrator,
    Action,
    ActionType
)


logger = logging.getLogger(__name__)


class AgentBrain:

    def __init__(self):

        self.client = Groq(
            api_key=settings.groq_api_key
        )

        self.model = settings.groq_chat_model

        self.memory = {}



    def create_plan(
        self,
        task_id: str,
        goal: str,
        user_command: str
    ):

        """
        Convert user command into task plan.
        """

        task = orchestrator.create_task(
            task_id,
            goal,
            user_command
        )


        prompt = f"""
You are an Android automation planner.

User goal:
{goal}

Command:
{user_command}

Create a simple step-by-step plan.

Available actions:
click
type
scroll
open_app
back
home
wait

Return JSON:

[
 {
  "description":"",
  "action":{
    "type":"",
    "target":"",
    "params":{}
  }
 }
]

"""


        try:

            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {
                        "role":"user",
                        "content":prompt
                    }
                ],
                temperature=0.2
            )


            text = response.choices[0].message.content


            steps = json.loads(text)


            for step in steps:

                action = Action(
                    type=ActionType(
                        step["action"]["type"]
                    ),
                    target=step["action"].get(
                        "target"
                    ),
                    params=step["action"].get(
                        "params",
                        {}
                    )
                )


                task.add_step(
                    step["description"],
                    action
                )


            return task



        except Exception as e:

            logger.error(
                f"Planning error: {e}"
            )

            return task





    def analyze_and_decide(
        self,
        screenshot_analysis:dict,
        task_id:str
    ) -> Dict[str,Any]:


        """
        Decide next Android action.
        """


        task = orchestrator.get_task(
            task_id
        )


        context = ""


        if task:

            context = json.dumps(
                task.to_dict()
            )



        prompt = f"""

You control an Android tablet.

Current task:
{context}


Screen analysis:

{screenshot_analysis}


Choose next action.

Rules:
- Prefer simple actions.
- Do not repeat failed actions.
- If unsure ask user.

Return JSON:

{{
"type":"",
"target":"",
"params":{{}},
"reason":""
}}

"""


        try:

            response = self.client.chat.completions.create(

                model=self.model,

                messages=[
                    {
                        "role":"system",
                        "content":
                        "You are an autonomous Android agent."
                    },
                    {
                        "role":"user",
                        "content":prompt
                    }
                ],

                temperature=0.1
            )


            text = response.choices[0].message.content


            action=json.loads(text)


            return self.format_action(
                action
            )



        except Exception as e:

            logger.error(
                f"Decision error: {e}"
            )

            return {
                "type":"wait",
                "target":None,
                "params":{},
                "reason":
                "Brain failure"
            }





    def format_action(
        self,
        action:dict
    ):

        """
        Format output for Android.
        """

        return {

            "type":
            action.get(
                "type",
                "wait"
            ),

            "target":
            action.get(
                "target"
            ),

            "params":
            action.get(
                "params",
                {}
            ),

            "reason":
            action.get(
                "reason",
                ""
            )

        }




    def process_voice_command(
        self,
        text:str
    ):

        """
        Convert voice request into goal.
        """


        prompt=f"""

Convert this voice command into an Android task.

Command:
{text}

Return:

{{
"goal":"",
"steps":[]
}}

"""


        try:

            response=self.client.chat.completions.create(

                model=self.model,

                messages=[
                    {
                        "role":"user",
                        "content":prompt
                    }
                ]

            )


            return json.loads(
                response
                .choices[0]
                .message
                .content
            )


        except Exception as e:

            logger.error(
                f"Voice processing error {e}"
            )

            return {
                "goal":text,
                "steps":[]
            }





    def remember(
        self,
        key,
        value
    ):

        self.memory[key]=value



    def recall(
        self,
        key
    ):

        return self.memory.get(
            key
        )




agent_brain = AgentBrain()
