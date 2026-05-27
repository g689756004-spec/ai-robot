"""Utility functions for the AI Agent backend."""

import base64
import os
import logging
from io import BytesIO
from typing import Tuple

logger = logging.getLogger(__name__)


def encode_image_to_base64(image_path: str) -> str:
    """
    Encode an image file to base64 string.
    
    Args:
        image_path: Path to the image file
    
    Returns:
        Base64 encoded string
    """
    try:
        with open(image_path, 'rb') as image_file:
            return base64.b64encode(image_file.read()).decode('utf-8')
    except Exception as e:
        logger.error(f"Error encoding image: {str(e)}")
        raise


def decode_base64_to_bytes(base64_str: str) -> bytes:
    """
    Decode a base64 string to bytes.
    
    Args:
        base64_str: Base64 encoded string
    
    Returns:
        Bytes object
    """
    try:
        return base64.b64decode(base64_str)
    except Exception as e:
        logger.error(f"Error decoding base64: {str(e)}")
        raise


def get_image_dimensions(image_bytes: bytes) -> Tuple[int, int]:
    """
    Get dimensions of an image from bytes.
    
    Args:
        image_bytes: Image data in bytes
    
    Returns:
        Tuple of (width, height)
    """
    try:
        from PIL import Image
        image = Image.open(BytesIO(image_bytes))
        return image.size
    except Exception as e:
        logger.error(f"Error getting image dimensions: {str(e)}")
        return (0, 0)


def format_action_for_android(action_dict: dict) -> dict:
    """
    Format an action dictionary for sending to Android client.
    
    Args:
        action_dict: Action dictionary from Claude
    
    Returns:
        Formatted action ready for Android
    """
    formatted = {
        "action_id": action_dict.get("action_id", ""),
        "type": action_dict.get("type", "wait"),
        "target": action_dict.get("target"),
        "params": action_dict.get("params", {}),
    }
    return formatted


def log_screen_analysis(task_id: str, analysis: dict):
    """Log screen analysis for debugging."""
    logger.debug(f"[{task_id}] Screen Analysis:")
    logger.debug(f"  - Analysis: {analysis.get('analysis', '')[:100]}")
    logger.debug(f"  - Next Action: {analysis.get('next_action', {}).get('type', 'unknown')}")
    logger.debug(f"  - Confidence: {analysis.get('confidence', 0)}")
