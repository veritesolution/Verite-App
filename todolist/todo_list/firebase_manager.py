import firebase_admin
from firebase_admin import credentials, firestore, messaging
import os
import json
from datetime import datetime

# Initialize Firebase (Mock or Real)
CRED_PATH = "serviceAccountKey.json"
MOCK_MODE = not os.path.exists(CRED_PATH)

if not MOCK_MODE:
    try:
        cred = credentials.Certificate(CRED_PATH)
        firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("[SUCCESS] Firebase Connected Successfully")
    except Exception as e:
        print(f"[ERROR] Firebase Initialization Error: {e}")
        MOCK_MODE = True
else:
    print("[WARNING] No serviceAccountKey.json found. Running in MOCK MODE.")
    db = None

class FirebaseManager:
    def __init__(self):
        self.mock_db = {}  # In-memory storage for mock mode

    def add_task(self, task_dict):
        """Adds a task to Firestore or Mock DB"""
        if MOCK_MODE:
            self.mock_db[task_dict["id"]] = task_dict
            print(f"[MOCK DB] Task Added: {task_dict['title']}")
        else:
            try:
                db.collection("tasks").document(task_dict["id"]).set(task_dict)
                print(f"[SUCCESS] Task Synced to Firebase: {task_dict['title']}")
            except Exception as e:
                print(f"[ERROR] Error syncing task: {e}")

    def get_tasks_for_date(self, date_str):
        """Retrieves tasks for a specific date (YYYY-MM-DD)"""
        if MOCK_MODE:
            # Filter mock db for tasks matching the date
            tasks = [t for t in self.mock_db.values() if t["scheduled_time"].startswith(date_str)]
            return tasks
        else:
            try:
                # Query Firestore for tasks where scheduled_time starts with date_str
                # Note: In a real app, you'd store date as a separate field for easier querying
                # For now, we'll fetch all and filter in python for simplicity in this demo
                docs = db.collection("tasks").stream()
                tasks = []
                for doc in docs:
                    task = doc.to_dict()
                    if task.get("scheduled_time", "").startswith(date_str):
                        tasks.append(task)
                return tasks
            except Exception as e:
                print(f"[ERROR] Error fetching tasks: {e}")
                return []

    def update_task_status(self, task_id, is_completed):
        """Updates task completion status"""
        if MOCK_MODE:
            if task_id in self.mock_db:
                self.mock_db[task_id]["is_completed"] = is_completed
                print(f"[MOCK DB] Task {task_id} updated: Completed={is_completed}")
        else:
            try:
                db.collection("tasks").document(task_id).update({"is_completed": is_completed})
                print(f"[SUCCESS] Task Status Updated in Firebase: {task_id}")
            except Exception as e:
                print(f"[ERROR] Error updating task: {e}")

    def send_notification(self, title, body):
        """Sends a push notification (Mock or Real)"""
        if MOCK_MODE:
            print(f"\n[NOTIFICATION] {title}: {body}\n")
        else:
            # In a real app, you would target specific device tokens or topics
            print(f"[FIREBASE MSG] Sending: {title} - {body}")
            # message = messaging.Message(
            #     notification=messaging.Notification(title=title, body=body),
            #     topic="all_users"
            # )
            # response = messaging.send(message)
            # print('Successfully sent message:', response)
