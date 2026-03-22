from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional, List
import uuid

@dataclass
class Task:
    title: str
    scheduled_time: datetime
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    is_completed: bool = False
    is_priority: bool = False
    energy_level: str = "Medium"  # High, Medium, Low
    category: str = "General"
    description: Optional[str] = None
    auto_migrate: bool = True

    def to_dict(self):
        return {
            "id": self.id,
            "title": self.title,
            "scheduled_time": self.scheduled_time.isoformat(),
            "is_completed": self.is_completed,
            "is_priority": self.is_priority,
            "energy_level": self.energy_level,
            "category": self.category,
            "description": self.description,
            "auto_migrate": self.auto_migrate
        }

    @classmethod
    def from_dict(cls, data):
        return cls(
            id=data.get("id"),
            title=data.get("title"),
            scheduled_time=datetime.fromisoformat(data.get("scheduled_time")),
            is_completed=data.get("is_completed", False),
            is_priority=data.get("is_priority", False),
            energy_level=data.get("energy_level", "Medium"),
            category=data.get("category", "General"),
            description=data.get("description"),
            auto_migrate=data.get("auto_migrate", True)
        )

@dataclass
class DailySummary:
    date: str  # YYYY-MM-DD
    total_tasks: int = 0
    completed_tasks: int = 0
    completion_percentage: float = 0.0
    tasks: List[Task] = field(default_factory=list)

    def calculate_progress(self):
        if self.total_tasks == 0:
            self.completion_percentage = 0.0
        else:
            self.completion_percentage = (self.completed_tasks / self.total_tasks) * 100
        return self.completion_percentage
