import os
import time
import json
import urllib.request
import urllib.error
import tempfile
import threading

try:
    import speech_recognition as sr
    SPEECH_REC_AVAILABLE = True
except ImportError:
    SPEECH_REC_AVAILABLE = False
    print("\n[WARNING] 'SpeechRecognition' module not found. Voice input is disabled.")
    print("To enable voice input, please install the required libraries:")
    print("Run: pip install SpeechRecognition pyaudio\n")

try:
    import pygame
    PYGAME_AVAILABLE = True
    pygame.mixer.init()
except ImportError:
    PYGAME_AVAILABLE = False
    print("\n[WARNING] 'pygame' module not found. Audio playback may be unreliable.")

try:
    from playsound import playsound
    PLAYSOUND_AVAILABLE = True
except ImportError:
    PLAYSOUND_AVAILABLE = False

API_KEY = "sk_b41f00750128df1ce440a96c1bbe42b236c3019b29a425dc"
VOICE_ID = "CwhRBWXzGAHq8TQ4Fs17" # Default to Roger
CHUNK_SIZE = 1024

def get_voices():
    """Fetches available voices dynamically from the ElevenLabs API."""
    voices_url = "https://api.elevenlabs.io/v1/voices"
    req = urllib.request.Request(voices_url, headers={
        "Accept": "application/json",
        "xi-api-key": API_KEY
    })
    
    try:
        with urllib.request.urlopen(req) as response:
            if response.status == 200:
                voices_data = json.loads(response.read().decode('utf-8'))
                return voices_data.get('voices', [])
    except Exception as e:
        print(f"Error fetching voices: {e}")
    return []

def build_ai_response(user_input, conversation_history):
    """
    Mock LLM logic to generate a contextual AI response.
    """
    input_lower = user_input.lower()
    
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
    elif "quit" in input_lower or "exit" in input_lower or "bye" in input_lower:
        return "Alright, talk to you later!"
    else:
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
                # Use a unique temporary file for every single audio generation 
                # to prevent file permission collisions or overlapping read heads
                temp_audio = tempfile.NamedTemporaryFile(delete=False, suffix=".mp3", prefix="eleven_")
                temp_filename = temp_audio.name
                
                while True:
                    chunk = response.read(CHUNK_SIZE)
                    if not chunk:
                        break
                    temp_audio.write(chunk)
                temp_audio.close() # Crucial: close the file before trying to play it
                
                # Play the generated audio file synchronously
                try:
                    if PLAYSOUND_AVAILABLE:
                        # playsound is the most robust synchronous player for Windows
                        playsound(temp_filename)
                    elif PYGAME_AVAILABLE:
                        pygame.mixer.music.load(temp_filename)
                        pygame.mixer.music.play()
                        while pygame.mixer.music.get_busy():
                            time.sleep(0.1)
                        pygame.mixer.music.unload()
                    else:
                        # Absolute last resort Windows native call (synchronous)
                        os.system(f'powershell -c (New-Object Media.SoundPlayer "{temp_filename}").PlaySync()')
                except Exception as e:
                    print(f"Could not play audio: {e}")
                finally:
                    # Clean up the file
                    try:
                        os.remove(temp_filename)
                    except OSError:
                        pass
                
                return True
    except Exception as e:
        print(f"Error speaking: {e}")
    return False

def listen_for_input():
    """
    Listens to the microphone and converts speech to text.
    Falls back to text input if speech recognition fails or isn't installed.
    """
    if not SPEECH_REC_AVAILABLE:
        return input("\nYou: ")
        
    recognizer = sr.Recognizer()
    try:
        with sr.Microphone() as source:
            print("\n[Listening... Please speak now]")
            # Adjust for ambient noise briefly
            recognizer.adjust_for_ambient_noise(source, duration=0.5)
            # Listen to the user's voice
            audio = recognizer.listen(source, timeout=10, phrase_time_limit=15)
            
        print("[Processing speech...]")
        # Use Google's free Web Speech API to convert audio to text
        text = recognizer.recognize_google(audio)
        print(f"You (Voice): {text}")
        return text
        
    except sr.WaitTimeoutError:
        print("[No speech detected within timeout.]")
        return ""
    except sr.UnknownValueError:
        print("[Could not understand audio. Please try again.]")
        return ""
    except sr.RequestError as e:
        print(f"[Speech recognition service error: {e}]")
        print("Falling back to text input.")
        return input("\nYou: ")
    except Exception as e:
        print(f"[Microphone error: {e}]")
        print("Falling back to text input.")
        return input("\nYou: ")

def main():
    global VOICE_ID
    print("="*50)
    print("Voice-Enabled AI Conversation Prototype")
    print("="*50)
    
    print("Fetching available voices...")
    available_voices = get_voices()
    
    if available_voices:
        print("\nAvailable Voices:")
        for i, voice in enumerate(available_voices[:5]): # Show up to 5 voices
            print(f"{i+1}. {voice['name']}")
            
        choice = input("\nSelect a voice (1-5) [Default: 1]: ").strip()
        try:
            choice_idx = int(choice) - 1
            if 0 <= choice_idx < min(5, len(available_voices)):
                selected_voice = available_voices[choice_idx]
                VOICE_ID = selected_voice['voice_id']
                print(f"Selected: {selected_voice['name']} (ID: {VOICE_ID})")
            else:
                raise ValueError()
        except ValueError:
            selected_voice = available_voices[0]
            VOICE_ID = selected_voice['voice_id']
            print(f"Invalid choice. Defaulting to: {selected_voice['name']} (ID: {VOICE_ID})")
    else:
        print("Could not fetch voices. Using default voice settings.")
    
    if SPEECH_REC_AVAILABLE:
        print("\nSay 'quit', 'exit', or 'bye' to end the chat.\n")
    else:
        print("\nType 'quit', 'exit', or 'bye' to end the chat.\n")
    
    conversation_history = []
    
    initial_greeting = "Hi there. Ready to review your day?"
    speak(initial_greeting)
    conversation_history.append({"role": "ai", "content": initial_greeting})
    
    while True:
        try:
            # Get input from microphone (or text fallback)
            user_input = listen_for_input()
            
            if not user_input.strip():
                continue
                
            # Check for exit commands
            if user_input.lower() in ['quit', 'exit', 'bye', 'stop']:
                speak("Alright, talk to you later!")
                break
                
            conversation_history.append({"role": "user", "content": user_input})
            
            ai_response = build_ai_response(user_input, conversation_history)
            conversation_history.append({"role": "ai", "content": ai_response})
            speak(ai_response)
            
        except KeyboardInterrupt:
            print("\nConversation ended.")
            break

if __name__ == "__main__":
    main()
