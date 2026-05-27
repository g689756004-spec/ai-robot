# AI Robot Backend Setup

This is the backend server for the AI Robot assistant running on Android tablet.

## Installation

1. **Create a Python virtual environment**:

```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

2. **Install dependencies**:

```bash
pip install -r requirements.txt
```

3. **Set up environment variables**:
   Create a `.env` file in this directory:

```
CLAUDE_API_KEY=your_claude_api_key_here
```

Get your Claude API key from: https://console.anthropic.com/

4. **Run the backend**:

```bash
python main.py
```

The server will start on `http://localhost:8000`

## API Endpoints

### Health Check

- `GET /health` - Server health check
- `GET /` - Status and info

### Screen Analysis

- `POST /api/analyze-screen` - Send screenshot for AI analysis
- `POST /api/extract-text` - Extract text from screenshot
- `POST /api/understand-layout` - Analyze page layout

### Task Management

- `POST /api/task/create` - Create a new task
- `GET /api/task/{task_id}/status` - Get task status
- `POST /api/task/{task_id}/cancel` - Cancel a task

### WebSocket

- `WS /ws/agent` - Real-time communication with Android client

## Architecture

- **config.py** - Configuration settings
- **claude_vision.py** - Claude API vision analysis
- **task_orchestrator.py** - Task management and planning
- **main.py** - FastAPI application and endpoints

## Testing

Once running, visit: `http://localhost:8000/docs` for interactive API documentation.
