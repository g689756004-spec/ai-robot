"""Claude Vision API integration for screen analysis."""

import base64
import json
import logging
from typing import Optional
import anthropic

try:
    from config import settings
except ImportError:
    import os
    class Settings:
        claude_api_key = os.getenv("CLAUDE_API_KEY", "")
        claude_model = "claude-3-5-sonnet-20241022"
    settings = Settings()

logger = logging.getLogger(__name__)


class ClaudeVisionAnalyzer:
    """Handles screen analysis using Claude's vision capabilities."""
    
    def __init__(self):
        self.client = anthropic.Anthropic(api_key=settings.claude_api_key)
        self.model = settings.claude_model
    
    def analyze_screen(self, screenshot_base64: str, context: Optional[str] = None) -> dict:
        """Analyze a screenshot and return analysis with next action."""
        try:
            system_prompt = """You are an AI assistant controlling an Android tablet. 
            You analyze screenshots and decide what actions to take next.
            
            Respond in JSON format with:
            {
                "analysis": "Description of what you see on screen",
                "ui_elements": [
                    {"type": "button", "label": "Play", "position": "center"}
                ],
                "next_action": {
                    "type": "click|scroll|type|wait|open_app|back|home",
                    "target": "description or coordinates",
                    "params": {}
                },
                "confidence": 0.0-1.0,
                "reasoning": "Why are you taking this action?"
            }"""
            
            user_prompt = f"""Please analyze this tablet screenshot and decide what to do next.
            {f'Context: {context}' if context else ''}"""
            
            message = self.client.messages.create(
                model=self.model,
                max_tokens=1024,
                system=system_prompt,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image",
                                "source": {
                                    "type": "base64",
                                    "media_type": "image/png",
                                    "data": screenshot_base64,
                                },
                            },
                            {"type": "text", "text": user_prompt}
                        ],
                    }
                ],
            )
            
            response_text = message.content[0].text
            
            try:
                if "```json" in response_text:
                    json_str = response_text.split("```json")[1].split("```")[0].strip()
                elif "```" in response_text:
                    json_str = response_text.split("```")[1].split("```")[0].strip()
                else:
                    json_str = response_text
                
                result = json.loads(json_str)
            except json.JSONDecodeError:
                logger.warning(f"Failed to parse JSON response")
                result = {
                    "analysis": response_text,
                    "next_action": {"type": "wait"},
                    "confidence": 0.0
                }
            
            return result
            
        except Exception as e:
            logger.error(f"Error analyzing screen: {str(e)}")
            return {
                "analysis": f"Error: {str(e)}",
                "next_action": {"type": "wait"},
                "confidence": 0.0,
                "error": True
            }
    
    def extract_text_from_screen(self, screenshot_base64: str) -> str:
        """Extract all visible text from a screenshot."""
        try:
            message = self.client.messages.create(
                model=self.model,
                max_tokens=2048,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image",
                                "source": {
                                    "type": "base64",
                                    "media_type": "image/png",
                                    "data": screenshot_base64,
                                },
                            },
                            {
                                "type": "text",
                                "text": "Extract all visible text. List each element on a new line."
                            }
                        ],
                    }
                ],
            )
            
            return message.content[0].text
            
        except Exception as e:
            logger.error(f"Error extracting text: {str(e)}")
            return f"Error: {str(e)}"
    
    def understand_page_layout(self, screenshot_base64: str) -> dict:
        """Understand the overall structure and layout of a page."""
        try:
            message = self.client.messages.create(
                model=self.model,
                max_tokens=1024,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image",
                                "source": {
                                    "type": "base64",
                                    "media_type": "image/png",
                                    "data": screenshot_base64,
                                },
                            },
                            {
                                "type": "text",
                                "text": "Analyze layout. Return JSON with: page_type, title, main_content, interactive_elements, structure"
                            }
                        ],
                    }
                ],
            )
            
            response_text = message.content[0].text
            
            try:
                if "```json" in response_text:
                    json_str = response_text.split("```json")[1].split("```")[0].strip()
                elif "```" in response_text:
                    json_str = response_text.split("```")[1].split("```")[0].strip()
                else:
                    json_str = response_text
                
                return json.loads(json_str)
            except json.JSONDecodeError:
                return {"raw_analysis": response_text}
            
        except Exception as e:
            logger.error(f"Error understanding layout: {str(e)}")
            return {"error": str(e)}
