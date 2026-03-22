import os
import time
import json
import urllib.request
import urllib.error
import tempfile
import threading

API_KEY = "sk_b41f00750128df1ce440a96c1bbe42b236c3019b29a425dc"
CHUNK_SIZE = 1024

def speak(text, voice_id="CwhRBWXzGAHq8TQ4Fs17"): # Default to Roger
    """Generates audio for given text and plays it"""
    print(f"\nAI Agent: {text}")
    
    tts_url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}"
    data = {"text": text, "model_id": "eleven_multilingual_v2", "voice_settings": {"stability": 0.5, "similarity_boost": 0.75}}
    req = urllib.request.Request(tts_url, data=json.dumps(data).encode('utf-8'), headers={"Accept": "audio/mpeg", "Content-Type": "application/json", "xi-api-key": API_KEY}, method='POST')
    
    try:
        with urllib.request.urlopen(req) as response:
            if response.status == 200:
                with tempfile.NamedTemporaryFile(delete=False, suffix=".mp3") as temp_audio:
                    temp_filename = temp_audio.name
                    while True:
                        chunk = response.read(CHUNK_SIZE)
                        if not chunk: break
                        temp_audio.write(chunk)
                
                # Play the generated audio file on Windows
                def play_audio():
                    try:
                        os.startfile(temp_filename)
                        time.sleep(10) # Let audio finish before deleting
                        try: os.remove(temp_filename)
                        except OSError: pass
                    except Exception as e:
                        print(f"Could not play audio: {e}")
                
                threading.Thread(target=play_audio, daemon=True).start()
                
                # Estimate dialogue time to wait before allowing user to type (rough approximation based on text length)
                estimated_duration = len(text) / 15.0 # ~15 characters per second spoken
                time.sleep(estimated_duration)
                return True
    except Exception as e:
        print(f"Error speaking: {e}")
    return False

def run_agent_workflow():
    print("="*50)
    print("End-of-Day Task Manager Agent")
    print("="*50)
    print("Initializing Agent Voice Module...")
    time.sleep(1)
    
    # In a real app, these responses would come from an LLM. Here we script them for a demo.
    
    # Step 1: Greeting
    speak("Hello! I'm your task management assistant. It's time for our end-of-day review.")
    
    # Step 2: Completed tasks
    speak("What tasks did you completely finish today?")
    completed = input("You: ")
    
    if completed.strip():
        speak("Great job on finishing those up! I'll mark them as completed in the system.")
    else:
        speak("That's alright, some days are focused on planning rather than execution. I'll note nothing was marked complete today.")
        
    # Step 3: Pushed tasks
    speak("Are there any tasks that you couldn't get to, that we should push to tomorrow?")
    pushed = input("You: ")
    
    if pushed.strip():
        speak("Got it. I've rolled those tasks over to tomorrow's schedule so you won't forget them.")
    else:
        speak("Perfect. It sounds like you are completely caught up.")
        
    # Step 4: New tasks
    speak("Finally, is there anything new that popped up today that you need to add to your list?")
    new_tasks = input("You: ")
    
    if new_tasks.strip():
        speak("I've added those new items to your backlog. We'll organize them tomorrow morning.")
    else:
        speak("Okay, no new additions.")
        
    # Conclusion
    speak("Alright, that concludes our review. You did great today. Have a good evening and see you tomorrow!")
    
    print("\n[System: Task data has been persisted.]")

if __name__ == "__main__":
    run_agent_workflow()
