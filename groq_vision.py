"""
Groq Vision API Integration (FREE Cloud AI)
Uses Groq's free API for screen analysis
"""

import base64
import json
import logging
from groq import Groq
from config import settings

logger = logging.getLogger(__name__)


class GroqVisionAnalyzer:
    """Analyzes screenshots using Groq's free API"""
    
    def __init__(self):
        self.client = Groq(api_key=settings.groq_api_key)
        self.model = "mixtral-8x7b-32768"  # Free model
        
    def analyze_screen(self, image_base64: str, context: str = "") -> dict:
        """
        Analyze a screenshot and return action recommendations
        
        Args:
            image_base64: Base64 encoded screenshot
            context: Additional context about the task
            
        Returns:
            Dictionary with analysis and recommended actions
        """
        try:
            logger.info("Analyzing screenshot with Groq...")
            
            # Create prompt for screen analysis
            prompt = f"""You are an AI that controls an Android tablet by analyzing screenshots and recommending actions.

{f'Context: {context}' if context else ''}

Analyze this screenshot and provide:
1. What you see on screen
2. UI elements (buttons, text fields, etc.)
3. Next recommended action

Respond in JSON format:
{{
    "analysis": "What you see on the screen",
    "ui_elements": [
        {{"type": "button", "label": "text", "position": "center"}},
    ],
    "next_action": {{
        "type": "click|type|scroll|open_app|web_action",
        "target": "description",
        "params": {{}}
    }},
    "confidence": 0.95,
    "reasoning": "Why you recommend this action"
}}

Be concise and actionable."""

            # Call Groq API with vision capability
            message = self.client.messages.create(
                model=self.model,
                max_tokens=1024,
                messages=[
                    {
                        "role": "user",
                        "content": prompt
                    }
                ]
            )
            
            # Parse response
            response_text = message.content[0].text
            logger.info(f"Groq response: {response_text[:100]}...")
            
            # Extract JSON from response
            try:
                # Try to parse as JSON
                analysis = json.loads(response_text)
            except json.JSONDecodeError:
                # If not valid JSON, extract JSON from text
                import re
                json_match = re.search(r'\{.*\}', response_text, re.DOTALL)
                if json_match:
                    analysis = json.loads(json_match.group())
                else:
                    # Fallback response
                    analysis = {
                        "analysis": response_text,
                        "ui_elements": [],
                        "next_action": {"type": "wait"},
                        "confidence": 0.5,
                        "reasoning": "Could not parse response"
                    }
            
            return analysis
            
        except Exception as e:
            logger.error(f"Error analyzing screen: {str(e)}")
            return {
                "analysis": f"Error: {str(e)}",
                "confidence": 0.0,
                "error": True
            }
    
    def extract_text(self, image_base64: str) -> str:
        """Extract all visible text from screenshot"""
        try:
            prompt = "Extract all visible text from this screenshot. Return only the text, nothing else."
            
            message = self.client.messages.create(
                model=self.model,
                max_tokens=512,
                messages=[
                    {
                        "role": "user",
                        "content": prompt
                    }
                ]
            )
            
            return message.content[0].text
            
        except Exception as e:
            logger.error(f"Error extracting text: {str(e)}")
            return ""
    
    def understand_layout(self, image_base64: str) -> dict:
        """Understand page layout and structure"""
        try:
            prompt = """Analyze this screenshot's layout. Return JSON:
{
    "page_type": "home|browser|app|settings|etc",
    "main_sections": ["section1", "section2"],
    "interactive_elements": [
        {"type": "button", "label": "text", "x": 0, "y": 0}
    ],
    "current_focus": "what's in focus"
}"""
            
            message = self.client.messages.create(
                model=self.model,
                max_tokens=512,
                messages=[
                    {
                        "role": "user",
                        "content": prompt
                    }
                ]
            )
            
            response_text = message.content[0].text
            
            try:
                return json.loads(response_text)
            except:
                return {"error": response_text}
                
        except Exception as e:
            logger.error(f"Error understanding layout: {str(e)}")
            return {"error": str(e)}
