import time
import threading
from datetime import datetime, timedelta
import schedule
from .task_manager import TaskManager

class Scheduler:
    def __init__(self, task_manager):
        self.tm = task_manager
        self._stop_event = threading.Event()

    def start(self):
        """Starts the scheduler in a background thread."""
        thread = threading.Thread(target=self._run_loop)
        thread.daemon = True
        thread.start()
        print("[INFO] Scheduler Started (Morning Boost & Gentle Nudge)")

    def _run_loop(self):
        # Schedule daily morning boost
        schedule.every().day.at("07:00").do(self.morning_boost)

        while not self._stop_event.is_set():
            schedule.run_pending()
            self.check_upcoming_tasks()
            time.sleep(60)  # Check every minute

    def morning_boost(self):
        """Sends a summary of today's tasks at 7:00 AM."""
        tasks = self.tm.get_todays_tasks()
        if tasks:
            count = len(tasks)
            msg = f"Good morning! You have {count} tasks today. Let's crush them!"
            self.tm.firebase.send_notification("Morning Boost", msg)
        else:
            self.tm.firebase.send_notification("Morning Boost", "Good morning! No tasks scheduled for today yet. Enjoy your day!")

    def check_upcoming_tasks(self):
        """Checks for tasks starting in 15-20 minutes."""
        now = datetime.now()
        upcoming_window = now + timedelta(minutes=20)
        
        # In a real app, query DB efficiently. Here, iteration is fine for small scale.
        tasks = self.tm.get_todays_tasks()
        for t in tasks:
            if t.get("is_completed"):
                continue
            
            task_time = datetime.fromisoformat(t["scheduled_time"])
            
            # Check if task is within 15-20 mins (and hasn't been notified yet - simplified logic)
            # Simplification: We just check if it's strictly between 15 and 20 mins from now
            # to avoid spamming every minute.
            time_diff = (task_time - now).total_seconds() / 60
            
            if 15 <= time_diff <= 20:
                self.tm.firebase.send_notification(
                    "Gentle Nudge", 
                    f"Heads up! '{t['title']}' starts in about {int(time_diff)} minutes."
                )
