"""Configuration module for the AI Agent backend (Render-safe)."""

from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # -------------------------
    # REQUIRED ENV VARIABLES
    # -------------------------
    groq_api_key: str = Field(..., alias="GROQ_API_KEY")

    # -------------------------
    # MODEL CONFIG
    # -------------------------
    groq_model: str = "llama-3.3-70b-versatile"

    # -------------------------
    # SERVER CONFIG
    # -------------------------
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = False

    # -------------------------
    # WEBSOCKET CONFIG
    # -------------------------
    websocket_timeout: int = 300
    max_screenshot_size_mb: int = 5

    # -------------------------
    # TASK CONFIG
    # -------------------------
    max_retries: int = 3
    action_timeout: int = 30
    screenshot_check_interval: float = 0.5

    class Config:
        env_file = ".env"
        case_sensitive = False


# This loads environment variables ONCE at startup
settings = Settings()
