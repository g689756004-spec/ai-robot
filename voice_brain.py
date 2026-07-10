"""
Voice Brain for AI Robot Agent.

Handles:
- User voice commands converted to text
- AI conversation
- Response generation
"""

import logging
from groq import Groq
from config import settings

logger = logging.getLogger(__name__)


class VoiceBrain:

    def __init__(self):
        self.client = Groq(
            api_key=settings.groq_api_key
        )

        self.model = settings.groq_chat_model

        self.system_prompt = """
You are an AI robot assistant.

You communicate through voice with the user.

Rules:
- Be helpful and friendly.
- Keep responses natural for speech.
- Do not use markdown.
- Do not give extremely long answers unless asked.
- Understand that you are running on an Android robot device.
- Answer clearly and conversationally.
"""


    def think(self, user_message: str) -> str:
        """
        Process user speech text and generate AI response.
        """

        try:

            logger.info(
                f"User said: {user_message}"
            )


            response = self.client.chat.completions.create(

                model=self.model,

                messages=[

                    {
                        "role": "system",
                        "content": self.system_prompt
                    },

                    {
                        "role": "user",
                        "content": user_message
                    }

                ],

                temperature=0.7,

                max_tokens=500

            )


            answer = (
                response
                .choices[0]
                .message
                .content
            )


            logger.info(
                f"AI response: {answer}"
            )


            return answer


        except Exception as e:

            logger.error(
                f"Voice brain error: {e}"
            )


            return (
                "Sorry, I am having trouble "
                "connecting to my AI brain right now."
            )



voice_brain = VoiceBrain()
