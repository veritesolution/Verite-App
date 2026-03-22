from datetime import datetime, timedelta
from .task_manager import TaskManager

class BedtimeRoutine:
    def __init__(self, task_manager):
        self.tm = task_manager

    def run_daily_checkin(self):
        """Interactive check-in at bedtime."""
        print("\n[Bedtime Check-in]")
        print("Did you complete your tasks today? (checking...)")
        
        today_tasks = self.tm.get_todays_tasks()
        incomplete_tasks = [t for t in today_tasks if not t.get("is_completed")]
        
        if not incomplete_tasks:
            print("[SUCCESS] Amazing! All tasks completed. Have a great rest!")
        else:
            print(f"[WARNING] You have {len(incomplete_tasks)} incomplete tasks.")
            self.auto_migrate_tasks(incomplete_tasks)

    def auto_migrate_tasks(self, incomplete_tasks):
        """Moves incomplete tasks to tomorrow as Priority, if allowed."""
        tomorrow = datetime.now() + timedelta(days=1)
        tomorrow_str = tomorrow.strftime("%Y-%m-%d")
        
        print("\n[INFO] Processing incomplete tasks...")
        
        migrated_count = 0
        abandoned_count = 0

        for task in incomplete_tasks:
            if not task.get("auto_migrate", True):
                print(f"[SKIPPED] '{task['title']}' is not set to auto-migrate.")
                abandoned_count += 1
                continue

            # Create new task for tomorrow
            # Keep same time via parsing or default to morning?
            # For simplicity, let's keep the same time but tomorrow.
            original_time = datetime.fromisoformat(task["scheduled_time"])
            new_time = original_time + timedelta(days=1)
            new_time_str = new_time.strftime("%Y-%m-%d %H:%M")
            
            # Add as priority
            self.tm.add_task(
                title=f"[Migrated] {task['title']}",
                scheduled_time_str=new_time_str,
                category=task.get("category", "General"),
                is_priority=True,
                auto_migrate=True # Keep migrating unless specified otherwise
            )
            
            print(f"[MIGRATED] Moved '{task['title']}' to {tomorrow_str} (Priority Flagged)")
            migrated_count += 1
        
        if abandoned_count > 0:
            print(f"\n[SUMMARY] {migrated_count} tasks migrated, {abandoned_count} tasks left behind.")
