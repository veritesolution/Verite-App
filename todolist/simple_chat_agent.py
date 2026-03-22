import os
import time
import json
import urllib.request
import urllib.error
import tempfile
import threading

API_KEY = "sk_b41f00750128df1ce440a96c1bbe42b236c3019b29a425dc"
VOICE_ID = "CwhRBWXzGAHq8TQ4Fs17" # Roger - Laid-Back, Casual
CHUNK_SIZE = 1024

def build_ai_response(user_input, conversation_history):
    """
    Mock LLM logic to generate a contextual AI response.
    In a real app, you would pass `conversation_history` and `user_input` 
    to an API like OpenAI (ChatGPT) or Anthropic (Claude) here.
    """
    input_lower = user_input.lower()
    
    # Very simple keyword-based mock interactions
    if "hello" in input_lower or "hi" in input_lower:
        return "Hey there! How's your day going?"
        
    elif "good" in input_lower or "great" in input_lower:
        return "That's awesome to hear. Did you get much done today?"
        
    elif "bad" in input_lower or "tired" in input_lower:
        return "I'm sorry to hear that. Rest is just as important as work."
        
    elif "task" in input_lower or "work" in input_lower:
        return "Handling tasks can be a lot. What's the main thing you're focusing on?"
        
    elif "finish" in input_lower or "done" in input_lower:
        return "Great job finishing that! Want me to cross anything off your list?"
        
    else:
        # Generic fallback
        return "Interesting. Tell me more about that."


def speak(text):
    """
    Generates audio for given text using ElevenLabs and plays it.
    """
    print(f"\nAI Agent: {text}")
    
    tts_url = f"https://api.elevenlabs.io/v1/text-to-speech/{VOICE_ID}"
    data = {
        "text": text,
        "model_id": "eleven_multilingual_v2",
        "voice_settings": {
            "stability": 0.5,
            "similarity_boost": 0.75
        }
    }
    
    req = urllib.request.Request(
        tts_url, 
        data=json.dumps(data).encode('utf-8'),
        headers={
            "Accept": "audio/mpeg",
            "Content-Type": "application/json",
            "xi-api-key": API_KEY
        },
        method='POST'
    )
    
    try:
        with urllib.request.urlopen(req) as response:
            if response.status == 200:
                with tempfile.NamedTemporaryFile(delete=False, suffix=".mp3") as temp_audio:
                    temp_filename = temp_audio.name
                    while True:
                        chunk = response.read(CHUNK_SIZE)
                        if not chunk:
                            break
                        temp_audio.write(chunk)
                
                # Play the generated audio file
                def play_audio():
                    try:
                        os.startfile(temp_filename)
                        # Wait a bit before deleting to ensure it plays
                        time.sleep(10) 
                        try:
                            os.remove(temp_filename)
                        except OSError:
                            pass
                    except Exception as e:
                        print(f"Could not play audio: {e}")
                
                # Run playback in a separate thread so we don't freeze the terminal
                threading.Thread(target=play_audio, daemon=True).start()
                
                # Estimate spoken duration (~15 chars per second) to wait before the next prompt
                estimated_duration = max(1.0, len(text) / 15.0)
                time.sleep(estimated_duration)
                
                return True
    except Exception as e:
        print(f"Error speaking: {e}")
    return False

def main():
    print("="*50)
    print("Simple AI Conversation Prototype")
    print("="*50)
    print("\nType 'quit' or 'exit' at any time to end the chat.\n")
    
    # We can store the history of the conversation to feed into a future LLM
    conversation_history = []
    
    # Initialize the conversation
    initial_greeting = "Hi there. Ready to review your day?"
    speak(initial_greeting)
    conversation_history.append({"role": "ai", "content": initial_greeting})
    
    while True:
        try:
            # 1. Get user input
            user_input = input("You: ")
            
            # Check for exit commands
            if user_input.lower() in ['quit', 'exit', 'bye']:
                speak("Alright, talk to you later!")
                break
                
            # Skip empty inputs
            if not user_input.strip():
                continue
                
            # Log user response
            conversation_history.append({"role": "user", "content": user_input})
            
            # 2. Generate AI response (using our mock LLM logic)
            ai_response = build_ai_response(user_input, conversation_history)
            
            # Log AI response
            conversation_history.append({"role": "ai", "content": ai_response})
            
            # 3. Speak the AI response
            speak(ai_response)
            
        except KeyboardInterrupt:
            print("\nConversation ended unexpectedly.")
            break

if __name__ == "__main__":
    main()
