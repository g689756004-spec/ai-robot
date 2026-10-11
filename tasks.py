"""Multi-step task planning for the Android agent (REST: /api/task/...)."""

import logging
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

import llm

log = logging.getLogger("wizi.tasks")


class ActionType(str, Enum):
    CLICK = "click"
    TYPE = "type"
    SCROLL = "scroll"
    SWIPE = "swipe"
    WAIT = "wait"
    OPEN_APP = "open_app"
    OPEN_URL = "open_url"
    BACK = "back"
    HOME = "home"
    LONG_PRESS = "long_press"
    ASK_USER = "ask_user"


@dataclass
class Step:
    number: int
    description: str
    type: ActionType
    target: Optional[str] = None
    params: Dict[str, Any] = field(default_factory=dict)
    status: str = "pending"
    result: Optional[str] = None

    def action(self) -> dict:
        return {"type": self.type.value, "target": self.target, "params": self.params}


class Task:
    def __init__(self, task_id: str, goal: str, command: str):
        self.task_id = task_id
        self.goal = goal
        self.command = command
        self.status = "pending"
        self.steps: List[Step] = []
        self.index = 0

    def current(self) -> Optional[Step]:
        return self.steps[self.index] if self.index < len(self.steps) else None

    def to_dict(self) -> dict:
        return {
            "task_id": self.task_id,
            "goal": self.goal,
            "command": self.command,
            "status": self.status,
            "current_step": self.index,
            "total_steps": len(self.steps),
            "steps": [
                {
                    "number": s.number,
                    "description": s.description,
                    "action": s.action(),
                    "status": s.status,
                    "result": s.result,
                }
                for s in self.steps
            ],
        }


_tasks: Dict[str, Task] = {}
_MAX_TASKS = 100


def get(task_id: str) -> Optional[Task]:
    return _tasks.get(task_id)


async def create_plan(task_id: str, goal: str, command: str) -> Task:
    task = Task(task_id, goal, command)
    _tasks[task_id] = task
    while len(_tasks) > _MAX_TASKS:                 # keep memory bounded
        _tasks.pop(next(iter(_tasks)))

    prompt = (
        "You are an Android automation planner.\n"
        f"User goal: {goal}\nCommand: {command}\n\n"
        "Create a short step-by-step plan. Allowed action types: "
        + ", ".join(a.value for a in ActionType)
        + '.\nReturn a JSON object: {"steps":[{"description":"","action":{"type":"","target":"","params":{}}}]}'
    )
    try:
        text, _ = await llm.chat(
            [{"role": "user", "content": prompt}], temperature=0.2, json_mode=True, max_tokens=800
        )
        parsed = llm.parse_json(text)
        for raw in parsed.get("steps", []):
            try:
                act = raw["action"]
                task.steps.append(Step(
                    number=len(task.steps) + 1,
                    description=str(raw.get("description", "")),
                    type=ActionType(act["type"]),
                    target=act.get("target"),
                    params=act.get("params") or {},
                ))
            except Exception:
                continue                              # skip malformed steps
    except Exception as e:
        log.warning("Planning failed: %s", e)

    return task


def next_action(task_id: str) -> Optional[dict]:
    task = _tasks.get(task_id)
    step = task.current() if task else None
    if not step:
        return None
    task.status = "running"
    return step.action()


def log_result(task_id: str, result: str, failed: bool = False) -> None:
    task = _tasks.get(task_id)
    step = task.current() if task else None
    if not step:
        return
    step.result = result
    if failed:
        step.status = "failed"
        task.status = "failed"
    else:
        step.status = "completed"
        task.index += 1
        task.status = "completed" if task.index >= len(task.steps) else "running"
