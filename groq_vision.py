"""
Groq Vision AI analyzer for Android Robot Agent.

Receives screenshots from Android,
analyzes the UI,
and returns the next action.
"""

import json
import logging
import re
from typing import Optional

from groq import Groq

from config import settings


logger = logging.getLogger(__name__)


class GroqVisionAnalyzer:

    def __init__(self):

        self.client = Groq(
            api_key=settings.groq_api_key
        )

        self.model = settings.groq_vision_model


    def analyze_screen(
        self,
        image_base64: str,
        context: Optional[str] = None
    ) -> dict:
        """
        Analyze Android screenshot and decide next action.
        """

        try:

            logger.info(
                "Sending screenshot to Groq Vision"
            )


            prompt = f"""
You are an autonomous Android AI agent.

You control a tablet by looking at screenshots.

Analyze the screen and decide the next action.

Context:
{context if context else "No context provided"}

Return ONLY valid JSON.

Format:

{{
 "analysis":"what is visible on screen",

 "ui_elements":[
    {{
      "type":"button",
      "label":"example",
      "location":"x,y"
    }}
 ],

 "next_action":{{
    "type":
    "click|type|scroll|open_app|back|home|wait",

    "target":"what to interact with",

    "params":{{
    }}
 }},

 "confidence":0.0,

 "reasoning":"why this action is correct"
}}

Rules:

- If unsure use wait.
- Never invent buttons.
- Use coordinates when possible.
- Be concise.
"""


            response = self.client.chat.completions.create(

                model=self.model,

                messages=[

                    {
                        "role":"system",
                        "content":prompt
                    },

                    {
                        "role":"user",

                        "content":[

                            {
                                "type":"image_url",

                                "image_url":{

                                    "url":
                                    f"data:image/jpeg;base64,{image_base64}"

                                }

                            }

                        ]

                    }

                ],

                temperature=0.2,

                max_tokens=1000

            )



            text = (
                response
                .choices[0]
                .message
                .content
            )


            logger.info(
                f"Vision response: {text[:300]}"
            )


            return self.parse_json_response(text)



        except Exception as e:

            logger.error(
                f"Vision analysis error: {e}"
            )

            return {

                "analysis":
                f"Vision error: {str(e)}",

                "next_action":{

                    "type":"wait",

                    "target":None,

                    "params":{}

                },

                "confidence":0,

                "error":True

            }





    def parse_json_response(
        self,
        text:str
    ) -> dict:

        """
        Extract JSON from model response.
        """

        try:


            text = text.strip()



            if "```json" in text:

                text = (
                    text
                    .split("```json")[1]
                    .split("```")[0]
                )


            elif "```" in text:

                text = (
                    text
                    .split("```")[1]
                    .split("```")[0]
                )



            return json.loads(
                text.strip()
            )



        except Exception:


            logger.warning(
                "Could not parse JSON"
            )


            match = re.search(
                r"\{.*\}",
                text,
                re.DOTALL
            )


            if match:

                try:

                    return json.loads(
                        match.group()
                    )

                except:
                    pass



            return {

                "analysis":text,

                "next_action":{

                    "type":"wait",

                    "target":None,

                    "params":{}

                },

                "confidence":0

            }





    def extract_text(
        self,
        image_base64:str
    ) -> str:


        try:


            response = self.client.chat.completions.create(

                model=self.model,

                messages=[

                    {

                    "role":"user",

                    "content":[

                        {

                        "type":"image_url",

                        "image_url":{

                            "url":
                            f"data:image/jpeg;base64,{image_base64}"

                        }

                        },

                        {

                        "type":"text",

                        "text":
                        "Extract all visible text."

                        }

                    ]

                    }

                ],

                max_tokens=1000

            )



            return (
                response
                .choices[0]
                .message
                .content
            )



        except Exception as e:


            logger.error(
                f"OCR error: {e}"
            )

            return ""





    def understand_layout(
        self,
        image_base64:str
    ) -> dict:


        result = self.analyze_screen(
            image_base64,
            "Understand the page layout only."
        )


        return {

            "page_analysis":
            result.get(
                "analysis",
                ""
            ),

            "elements":
            result.get(
                "ui_elements",
                []
            )

        }
