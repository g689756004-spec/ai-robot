"""Configuration module for the AI Agent backend."""

import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""
    
    # Groq API Configuration (FREE)
    groq_api_key: str = os.getenv("GROQ_API_KEY", "your-groq-api-key-here")
    groq_model: str = "mixtral-8x7b-32768"
    
    # Server Configuration
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = True
    
    # WebSocket Configuration
    websocket_timeout: int = 300
    max_screenshot_size_mb: int = 5
    
    # Task Configuration
    max_retries: int = 3
    action_timeout: int = 30
    screenshot_check_interval: float = 0.5
    
    class Config:
        env_file = ".env"


settings = Settings()
