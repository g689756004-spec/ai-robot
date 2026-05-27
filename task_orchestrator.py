"""Task orchestration for multi-step AI agent operations."""

import logging
from typing import Optional, Dict, List, Any
from enum import Enum
from dataclasses import dataclass
from datetime import datetime

logger = logging.getLogger(__name__)


class TaskStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    WAITING_USER = "waiting_user"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class ActionType(str, Enum):
    CLICK = "click"
    SCROLL = "scroll"
    TYPE = "type"
    WAIT = "wait"
    OPEN_APP = "open_app"
    BACK = "back"
    HOME = "home"
    SWIPE = "swipe"
    LONG_PRESS = "long_press"
    ASK_USER = "ask_user"
    TAKE_SCREENSHOT = "take_screenshot"


@dataclass
class Action:
    type: ActionType
    target: Optional[str] = None
    params: Dict[str, Any] = None
    
    def to_dict(self):
        return {
            "type": self.type.value,
            "target": self.target,
            "params": self.params or {}
        }


@dataclass
class TaskStep:
    step_number: int
    description: str
    action: Action
    status: TaskStatus = TaskStatus.PENDING
    result: Optional[str] = None
    timestamp: Optional[str] = None


class Task:
    def __init__(self, task_id: str, goal: str, user_command: str):
        self.task_id = task_id
        self.goal = goal
        self.user_command = user_command
        self.status = TaskStatus.PENDING
        self.steps: List[TaskStep] = []
        self.current_step = 0
        self.created_at = datetime.now().isoformat()
        self.metadata = {}
    
    def add_step(self, description: str, action: Action):
        step = TaskStep(
            step_number=len(self.steps) + 1,
            description=description,
            action=action
        )
        self.steps.append(step)
    
    def get_current_step(self) -> Optional[TaskStep]:
        if self.current_step < len(self.steps):
            return self.steps[self.current_step]
        return None
    
    def advance_step(self, result: str = None):
        if self.current_step < len(self.steps):
            self.steps[self.current_step].status = TaskStatus.COMPLETED
            self.steps[self.current_step].result = result
            self.steps[self.current_step].timestamp = datetime.now().isoformat()
            self.current_step += 1
            
            if self.current_step >= len(self.steps):
                self.status = TaskStatus.COMPLETED
            elif self.status == TaskStatus.PENDING:
                self.status = TaskStatus.RUNNING
    
    def fail_step(self, error: str):
        if self.current_step < len(self.steps):
            self.steps[self.current_step].status = TaskStatus.FAILED
            self.steps[self.current_step].result = error
            self.steps[self.current_step].timestamp = datetime.now().isoformat()
            self.status = TaskStatus.FAILED
    
    def to_dict(self):
        return {
            "task_id": self.task_id,
            "goal": self.goal,
            "user_command": self.user_command,
            "status": self.status.value,
            "created_at": self.created_at,
            "current_step": self.current_step,
            "total_steps": len(self.steps),
            "steps": [
                {
                    "step_number": s.step_number,
                    "description": s.description,
                    "action": s.action.to_dict(),
                    "status": s.status.value,
                    "result": s.result,
                    "timestamp": s.timestamp
                }
                for s in self.steps
            ]
        }


class TaskOrchestrator:
    def __init__(self):
        self.tasks: Dict[str, Task] = {}
        self.current_task_id: Optional[str] = None
    
    def create_task(self, task_id: str, goal: str, user_command: str) -> Task:
        task = Task(task_id, goal, user_command)
        self.tasks[task_id] = task
        self.current_task_id = task_id
        return task
    
    def get_current_task(self) -> Optional[Task]:
        if self.current_task_id:
            return self.tasks.get(self.current_task_id)
        return None
    
    def get_task(self, task_id: str) -> Optional[Task]:
        return self.tasks.get(task_id)
    
    def execute_next_action(self, task_id: str) -> Optional[Action]:
        task = self.get_task(task_id)
        if not task:
            return None
        
        step = task.get_current_step()
        if step:
            return step.action
        return None
    
    def log_step_result(self, task_id: str, result: str, error: bool = False):
        task = self.get_task(task_id)
        if task:
            if error:
                task.fail_step(result)
            else:
                task.advance_step(result)
    
    def cancel_task(self, task_id: str):
        task = self.get_task(task_id)
        if task:
            task.status = TaskStatus.CANCELLED
            self.current_task_id = None
    
    def get_task_status(self, task_id: str) -> Optional[Dict]:
        task = self.get_task(task_id)
        if task:
            return task.to_dict()
        return None


orchestrator = TaskOrchestrator()
