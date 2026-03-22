from datetime import datetime
from .models import Task
from .firebase_manager import FirebaseManager

class TaskManager:
    def __init__(self):
        self.firebase = FirebaseManager()

    def add_task(self, title, scheduled_time_str, category="General", is_priority=False, auto_migrate=True):
        """
        Adds a new task.
        scheduled_time_str format: "YYYY-MM-DD HH:MM"
        """
        try:
            dt = datetime.strptime(scheduled_time_str, "%Y-%m-%d %H:%M")
            new_task = Task(title=title, scheduled_time=dt, category=category, is_priority=is_priority, auto_migrate=auto_migrate)
            self.firebase.add_task(new_task.to_dict())
            return new_task
        except ValueError as e:
            print(f"❌ Invalid date format. Use YYYY-MM-DD HH:MM. Error: {e}")
            return None

    def mark_complete(self, task_id):
        """Marks a task as complete."""
        self.firebase.update_task_status(task_id, True)

    def get_progress(self, date_str):
        """Calculates completion percentage for a given date."""
        tasks = self.firebase.get_tasks_for_date(date_str)
        if not tasks:
            return 0.0
        
        total = len(tasks)
        completed = sum(1 for t in tasks if t["is_completed"])
        percentage = (completed / total) * 100
        return round(percentage, 1)

    def get_todays_tasks(self):
        """Returns list of tasks for today."""
        today_str = datetime.now().strftime("%Y-%m-%d")
        return self.firebase.get_tasks_for_date(today_str)

    def complete_task_by_title(self, title):
        """Marks a task as complete by its title (case-insensitive checking against today's tasks)."""
        tasks = self.get_todays_tasks()
        if not tasks:
            return None
        
        # Find matching task
        target_task = None
        for t in tasks:
            if t['title'].strip().lower() == title.strip().lower() and not t['is_completed']:
                target_task = t
                break
        
        if target_task:
            self.mark_complete(target_task['id'])
            return target_task
        return None

    def remove_task(self, task_id):
        """Removes a task."""
        self.firebase.delete_task(task_id)

    def remove_task_by_title(self, title):
        """Removes a task by its title (case-insensitive checking against today's tasks)."""
        tasks = self.get_todays_tasks()
        if not tasks:
            return None
        
        # Find matching task
        target_task = None
        for t in tasks:
            if t['title'].strip().lower() == title.strip().lower():
                target_task = t
                break
        
        if target_task:
            self.remove_task(target_task['id'])
            return target_task
        return None
