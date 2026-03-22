import sys
import time
from datetime import datetime, timedelta
from todo_list.task_manager import TaskManager
from todo_list.scheduler import Scheduler
from todo_list.bedtime import BedtimeRoutine
from todo_list.voice_input import VoiceInputHandler


def resolve_command(text):
    text = text.lower()
    if any(w in text for w in ["add", "create", "new", "make"]):
        return "add"
    if any(w in text for w in ["list", "show", "display", "tasks", "what to do", "what are my tasks", "what do i have to do"]):
        return "list"
    if any(w in text for w in ["complete", "finish", "done", "check", "mark"]):
        return "complete"
    if any(w in text for w in ["progress", "status", "how am i doing"]):
        return "progress"
    if any(w in text for w in ["bedtime", "sleep", "goodnight", "routine"]):
        return "bedtime"
    if any(w in text for w in ["remove", "delete", "discard", "trash", "clear", "cancel task"]):
        return "remove"
    if any(w in text for w in ["exit", "stop", "quit", "bye", "leave"]):
        return "exit"
    return None

def parse_voice_time(text):
    """
    Parses simple time expressions from voice input.
    Returns a formatted string "YYYY-MM-DD HH:MM" or None.
    """
    now = datetime.now()
    text = text.lower()
    
    target_date = now.date()
    target_time = (now + timedelta(hours=1)).time() # Default: +1 hour from now

    if "tomorrow" in text:
        target_date = now.date() + timedelta(days=1)
        target_time = datetime.strptime("09:00", "%H:%M").time() # Default 9 AM for tomorrow
    elif "today" in text:
        target_date = now.date()
    else:
        # Check for day names (any day within a week)
        weekdays = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
        for i, day in enumerate(weekdays):
            if day in text:
                days_ahead = i - now.weekday()
                if days_ahead <= 0: # Target day already happened this week or is today
                    days_ahead += 7
                target_date = now.date() + timedelta(days=days_ahead)
                target_time = datetime.strptime("09:00", "%H:%M").time()
                break
        else:
            # Check for "in X days"
            if "day" in text:
                try:
                    words = text.split()
                    for i, w in enumerate(words):
                        if w.isdigit() and "day" in words[i+1]:
                            days = int(w)
                            target_date = now.date() + timedelta(days=days)
                            target_time = datetime.strptime("09:00", "%H:%M").time()
                            break
                except:
                    pass
    
    # Existing minute logic
    if "minute" in text:
        try:
            # simple extraction of number like "in 10 minutes"
            words = text.split()
            for i, w in enumerate(words):
                if w.isdigit() and "minute" in words[i+1]:
                    minutes = int(w)
                    return (now + timedelta(minutes=minutes)).strftime("%Y-%m-%d %H:%M")
        except:
            pass

    return datetime.combine(target_date, target_time).strftime("%Y-%m-%d %H:%M")

def parse_yes_no(text):
    text = text.lower()
    return any(w in text for w in ["yes", "yeah", "yep", "sure", "ok", "please", "do it"])

def get_voice_input_with_retry(handler, prompt, error_msg="I didn't catch that.", validator=None):
    """
    Loops until valid input is received or user cancels.
    """
    while True:
        print(f"[BOT]: {prompt}")
        text = handler.listen(f"Speak: {prompt}")
        
        if not text:
            print(f"[!] {error_msg} Please speak again.")
            continue
            
        if any(w in text.lower() for w in ["cancel", "stop", "abort"]):
            print("[-] Operation cancelled.")
            return None
            
        if validator:
            is_valid, value = validator(text)
            if is_valid:
                return value
            else:
                print(f"[!] Could not understand answer for: {prompt}")
                continue
        
        return text

def main():
    print("AI-Driven Habit & Task Engine Starting...")
    
    tm = TaskManager()
    scheduler = Scheduler(tm)
    bedtime = BedtimeRoutine(tm)
    voice_handler = VoiceInputHandler()

    
    # Start Scheduler
    scheduler.start()
    
    print("\ncommands: 'add', 'list', 'complete', 'remove', 'progress', 'bedtime', 'voice', 'exit'")
    
    while True:
        try:
            cmd = input("\n> ").strip().lower()
            
            if cmd == "exit":
                print("Goodbye!")
                sys.exit(0)
                
            elif cmd == "add":
                title = input("Task Title: ")
                time_str = input("Time (YYYY-MM-DD HH:MM): ")
                
                auto_migrate_input = input("Auto-migrate if incomplete? (Y/n): ").strip().lower()
                auto_migrate = auto_migrate_input != 'n'

                # Defaults for quick testing
                if not time_str:
                    time_str = (datetime.now() + timedelta(minutes=60)).strftime("%Y-%m-%d %H:%M")
                    print(f"Using default time: {time_str}")
                    
                tm.add_task(title, time_str, auto_migrate=auto_migrate)
                
            elif cmd == "list":
                tasks = tm.get_todays_tasks()
                if not tasks:
                    print("No tasks for today.")
                for t in tasks:
                    status = "[DONE]" if t['is_completed'] else "[TODO]"
                    prio = "[PRIORITY]" if t.get('is_priority') else ""
                    print(f"{status} {t['scheduled_time']} - {t['title']} {prio} (ID: {t['id']})")

            elif cmd == "complete":
                tid = input("Task ID to complete: ")
                tm.mark_complete(tid)

            elif cmd == "remove":
                tid = input("Task ID to remove: ")
                tm.remove_task(tid)
                
            elif cmd == "progress":
                date_str = datetime.now().strftime("%Y-%m-%d")
                prog = tm.get_progress(date_str)
                print(f"[PROGRESS] Today's Progress: {prog}%")
                
            elif cmd == "bedtime":
                bedtime.run_daily_checkin()

            elif cmd == "voice":
                print("Voice Mode Activated. Say a command or 'exit' to return.")
                while True:
                    v_cmd = voice_handler.listen("Listening... (Commands: add, list, complete, remove, progress, bedtime, exit)")
                    if not v_cmd:
                        continue
                    
                    cmd_type = resolve_command(v_cmd)

                    if cmd_type == "exit":
                        break
                    
                    elif cmd_type == "add":
                        # Interactive Add Flow with Retry
                        
                        # 1. Get Title
                        print("[BOT]: What is the task?")
                        title = get_voice_input_with_retry(voice_handler, "What is the task?")
                        if not title: continue # Cancelled
                        
                        # 2. Get Time
                        def time_validator(text):
                            t_str = parse_voice_time(text)
                            return (True, t_str) if t_str else (False, None)
                            
                        print("[BOT]: When is this for? (e.g., 'Today', 'Monday', 'in 2 days')")
                        time_str = get_voice_input_with_retry(
                            voice_handler, 
                            "When is this for?", 
                            validator=time_validator
                        )
                        if not time_str: continue # Cancelled
                        print(f"   -> Set for: {time_str}")

                        # 3. Get Auto-migrate
                        def yes_no_validator(text):
                            text = text.lower()
                            if any(w in text for w in ["yes", "yeah", "yep", "sure", "ok", "do it"]):
                                return (True, True)
                            if any(w in text for w in ["no", "nope", "nah", "don't"]):
                                return (True, False)
                            return (False, None)

                        print("[BOT]: Auto-migrate if incomplete? (Yes/No)")
                        auto_migrate = get_voice_input_with_retry(
                            voice_handler, 
                            "Auto-migrate if incomplete? (Yes/No)", 
                            validator=yes_no_validator
                        )
                        if auto_migrate is None: continue # Cancelled
                        
                        tm.add_task(title, time_str, auto_migrate=auto_migrate)
                        print(f"[OK] Task '{title}' added!")

                    elif cmd_type == "list":
                        # Logic for listing tasks
                        tasks = tm.get_todays_tasks()
                        if not tasks:
                            print("No tasks for today.")
                        for t in tasks:
                            status = "[DONE]" if t['is_completed'] else "[TODO]"
                            print(f"{status} {t['scheduled_time']} - {t['title']} (ID: {t['id']})")

                    elif cmd_type == "complete":
                        # Try to complete by title from command first
                        todays_tasks = tm.get_todays_tasks()
                        found_task = None
                        for t in todays_tasks:
                             if t['title'].lower() in v_cmd:
                                 found_task = t
                                 break
                        
                        if found_task:
                            tm.mark_complete(found_task['id'])
                            print(f"[OK] Task '{found_task['title']}' marked as complete.")
                        else:
                            # Interactive fallback with retry
                            print("[BOT]: Which task do you want to complete?")
                            title_input = get_voice_input_with_retry(voice_handler, "Which task to complete?")
                            if title_input:
                                completed_task = tm.complete_task_by_title(title_input)
                                if completed_task:
                                    print(f"[OK] Task '{completed_task['title']}' marked as complete.")
                                else:
                                    print(f"[!] Could not find task '{title_input}'.")
                    
                    elif cmd_type == "remove":
                        # Try to remove by title from command first
                        todays_tasks = tm.get_todays_tasks()
                        found_task = None
                        for t in todays_tasks:
                             if t['title'].lower() in v_cmd:
                                 found_task = t
                                 break
                        
                        if found_task:
                            tm.remove_task(found_task['id'])
                            print(f"[trash] Task '{found_task['title']}' removed.")
                        else:
                            # Interactive fallback with retry
                            print("[BOT]: Which task do you want to remove?")
                            title_input = get_voice_input_with_retry(voice_handler, "Which task to remove?")
                            if title_input:
                                removed_task = tm.remove_task_by_title(title_input)
                                if removed_task:
                                    print(f"[trash] Task '{removed_task['title']}' removed.")
                                else:
                                    print(f"[!] Could not find task '{title_input}'.")
                                    
                    elif cmd_type == "progress":
                        date_str = datetime.now().strftime("%Y-%m-%d")
                        prog = tm.get_progress(date_str)
                        print(f"[PROGRESS] Today's Progress: {prog}%")

                    elif cmd_type == "bedtime":
                        print("Running Bedtime Routine...")
                        bedtime.run_daily_checkin()
                    
                    else:
                        print(f"[?] Command not recognized. You said: '{v_cmd}'")
                
            else:
                print("Unknown command.")
                
        except KeyboardInterrupt:
            print("\nExiting...")
            sys.exit(0)
        except Exception as e:
            print(f"Error: {e}")

if __name__ == "__main__":
    main()
