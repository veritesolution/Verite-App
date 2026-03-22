import sys
import os

# Add src to sys.path
sys.path.append(os.path.abspath("src"))

from task_manager import TaskManager

from datetime import datetime, timedelta

def test_completion_by_title():
    tm = TaskManager()
    
    # Create a dummy task
    title = f"Test Task {datetime.now().timestamp()}"
    time_str = (datetime.now() + timedelta(minutes=60)).strftime("%Y-%m-%d %H:%M")
    tm.add_task(title, time_str)
    print(f"Created task: {title}")
    
    # Try to complete it by title (exact)
    res = tm.complete_task_by_title(title)
    if res:
         print("[OK] Exact match completion worked")
    else:
         print("[FAIL] Exact match completion failed")

    # Create another dummy task
    title2 = f"Voice Test {datetime.now().timestamp()}"
    tm.add_task(title2, time_str)
    print(f"Created task: {title2}")
    
    # Try case insensitive and whitespace
    res2 = tm.complete_task_by_title(f"   {title2.upper()}   ")
    if res2:
         print("[OK] Case/Whitespace match completion worked")
    else:
         print("[FAIL] Case/Whitespace match completion failed")

if __name__ == "__main__":
    test_completion_by_title()
