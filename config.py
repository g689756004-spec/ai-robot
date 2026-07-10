"""
Configuration settings for AI Robot Agent backend.
"""

import os

from pydantic_settings import BaseSettings, SettingsConfigDict



class Settings(BaseSettings):


    model_config = SettingsConfigDict(
        env_file=".env",
        case_sensitive=False,
        extra="ignore"
    )



    # =========================
    # API KEYS
    # =========================


    groq_api_key: str = os.getenv(
        "GROQ_API_KEY",
        ""
    )


    claude_api_key: str = os.getenv(
        "CLAUDE_API_KEY",
        ""
    )



    # =========================
    # AI MODELS
    # =========================


    chat_model: str = (
        "llama-3.1-8b-instant"
    )


    vision_model: str = (
        "llama-3.2-11b-vision-preview"
    )


    claude_model: str = (
        "claude-3-5-sonnet-20241022"
    )



    # =========================
    # SERVER
    # =========================


    host: str = "0.0.0.0"


    port: int = 8000


    debug: bool = False



    # =========================
    # WEBSOCKET
    # =========================


    websocket_path: str = (
        "/ws/agent"
    )


    websocket_timeout: int = 300



    # =========================
    # ANDROID AGENT
    # =========================


    android_agent_name: str = (
        "AI Robot Tablet Agent"
    )


    reconnect_delay: int = 5



    # =========================
    # SCREEN ANALYSIS
    # =========================


    max_screenshot_size_mb: int = 5


    screenshot_interval: float = 0.5


    screenshot_quality: int = 70


    screenshot_scale: float = 0.7



    # =========================
    # TASK SYSTEM
    # =========================


    max_retries: int = 3


    action_timeout: int = 30



    # =========================
    # VOICE SYSTEM
    # =========================


    enable_voice: bool = True


    voice_language: str = (
        "en-US"
    )


    wake_word: str = (
        "robot"
    )



    # =========================
    # MEMORY SYSTEM
    # =========================


    enable_memory: bool = False


    memory_database: str = (
        "memory.db"
    )


    max_memory_items: int = 1000




settings = Settings()
