"""
Task orchestration engine for AI Robot Agent.

Handles:
- Creating tasks
- Managing steps
- Executing actions
- Tracking progress
- Handling failures
"""

import logging

from typing import Optional, Dict, List, Any

from enum import Enum

from dataclasses import dataclass, field

from datetime import datetime



logger = logging.getLogger(__name__)




class TaskStatus(str, Enum):

    PENDING = "pending"

    RUNNING = "running"

    COMPLETED = "completed"

    FAILED = "failed"

    CANCELLED = "cancelled"





class ActionType(str, Enum):

    CLICK = "click"

    TYPE = "type"

    SCROLL = "scroll"

    SWIPE = "swipe"

    WAIT = "wait"

    OPEN_APP = "open_app"

    BACK = "back"

    HOME = "home"

    LONG_PRESS = "long_press"

    ASK_USER = "ask_user"





@dataclass
class Action:


    type: ActionType

    target: Optional[str] = None

    params: Dict[str,Any] = field(
        default_factory=dict
    )


    def to_dict(self):

        return {

            "type": self.type.value,

            "target": self.target,

            "params": self.params

        }





@dataclass
class TaskStep:


    number:int

    description:str

    action:Action

    status:TaskStatus = TaskStatus.PENDING

    result:Optional[str]=None

    created_at:str = field(
        default_factory=lambda:
        datetime.now().isoformat()
    )





class Task:


    def __init__(
        self,
        task_id:str,
        goal:str,
        user_command:str
    ):


        self.task_id = task_id

        self.goal = goal

        self.user_command = user_command

        self.status = TaskStatus.PENDING

        self.steps:List[TaskStep]=[]

        self.current_index = 0

        self.created_at = (
            datetime.now()
            .isoformat()
        )



    def add_step(
        self,
        description:str,
        action:Action
    ):


        self.steps.append(

            TaskStep(

                number=len(self.steps)+1,

                description=description,

                action=action

            )

        )




    def current_step(self):

        if self.current_index < len(self.steps):

            return self.steps[
                self.current_index
            ]

        return None




    def complete_current(
        self,
        result=""
    ):


        step=self.current_step()


        if step:


            step.status = TaskStatus.COMPLETED

            step.result=result

            self.current_index +=1



            if self.current_index >= len(self.steps):

                self.status = TaskStatus.COMPLETED

            else:

                self.status = TaskStatus.RUNNING





    def fail_current(
        self,
        error:str
    ):


        step=self.current_step()


        if step:

            step.status = TaskStatus.FAILED

            step.result = error

            self.status = TaskStatus.FAILED






    def to_dict(self):


        return {

            "task_id":self.task_id,

            "goal":self.goal,

            "command":self.user_command,

            "status":self.status.value,

            "current_step":self.current_index,

            "total_steps":len(self.steps),

            "steps":[

                {

                    "number":s.number,

                    "description":s.description,

                    "action":s.action.to_dict(),

                    "status":s.status.value,

                    "result":s.result

                }

                for s in self.steps

            ]

        }







class TaskOrchestrator:



    def __init__(self):

        self.tasks={}

        self.current_task=None




    def create_task(
        self,
        task_id:str,
        goal:str,
        user_command:str
    ):


        task=Task(

            task_id,

            goal,

            user_command

        )


        self.tasks[task_id]=task

        self.current_task=task


        logger.info(
            f"Task created {task_id}"
        )


        return task





    def add_action(
        self,
        task_id:str,
        description:str,
        action_type:str,
        target=None,
        params=None
    ):


        task=self.tasks.get(
            task_id
        )


        if not task:

            return False



        try:

            action=Action(

                type=ActionType(action_type),

                target=target,

                params=params or {}

            )


        except Exception:


            action=Action(

                type=ActionType.WAIT

            )



        task.add_step(

            description,

            action

        )


        return True






    def get_next_action(
        self,
        task_id:str
    ):


        task=self.tasks.get(
            task_id
        )


        if not task:

            return None



        step=task.current_step()



        if step:

            task.status=TaskStatus.RUNNING

            return step.action



        return None






    def execute_next_action(
        self,
        task_id:str
    ):


        return self.get_next_action(
            task_id
        )






    def log_step_result(
        self,
        task_id:str,
        result:str,
        failed=False
    ):


        task=self.tasks.get(
            task_id
        )


        if not task:

            return



        if failed:

            task.fail_current(
                result
            )

        else:

            task.complete_current(
                result
            )







    def get_task(
        self,
        task_id:str
    ):


        return self.tasks.get(
            task_id
        )







    def get_task_status(
        self,
        task_id:str
    ):


        task=self.tasks.get(
            task_id
        )


        if task:

            return task.to_dict()


        return None






    def cancel_task(
        self,
        task_id:str
    ):


        task=self.tasks.get(
            task_id
        )


        if task:

            task.status=TaskStatus.CANCELLED



        if self.current_task==task:

            self.current_task=None






orchestrator = TaskOrchestrator()
