"""Configuration for AI Robot Agent backend."""

import os
from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):

    # =========================
    # API KEYS
    # =========================

    groq_api_key: str = Field(
        default="",
        alias="GROQ_API_KEY"
    )

    claude_api_key: str = Field(
        default="",
        alias="CLAUDE_API_KEY"
    )


    # =========================
    # AI MODELS
    # =========================

    groq_chat_model: str = "llama-3.1-8b-instant"

    groq_vision_model: str = "llama-3.2-11b-vision-preview"

    claude_model: str = "claude-3-5-sonnet-20241022"


    # =========================
    # SERVER
    # =========================

    host: str = "0.0.0.0"

    port: int = 8000

    debug: bool = False


    # =========================
    # WEBSOCKET
    # =========================

    websocket_timeout: int = 300

    websocket_path: str = "/ws/agent"


    # =========================
    # SCREEN ANALYSIS
    # =========================

    max_screenshot_size_mb: int = 5

    screenshot_check_interval: float = 0.5


    # =========================
    # TASK SYSTEM
    # =========================

    max_retries: int = 3

    action_timeout: int = 30


    # =========================
    # MEMORY (future)
    # =========================

    enable_memory: bool = False

    memory_database: str = "memory.db"

    max_memory_items: int = 1000


    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()
