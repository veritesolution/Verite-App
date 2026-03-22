import os
import time
import json
import urllib.request
import urllib.error
import tempfile
import threading

API_KEY = "sk_b41f00750128df1ce440a96c1bbe42b236c3019b29a425dc"
CHUNK_SIZE = 1024

def get_voices():
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

def speak(text, voice_id):
    tts_url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}"
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
                # Create a temporary file to store the audio
                with tempfile.NamedTemporaryFile(delete=False, suffix=".mp3") as temp_audio:
                    temp_filename = temp_audio.name
                    while True:
                        chunk = response.read(CHUNK_SIZE)
                        if not chunk:
                            break
                        temp_audio.write(chunk)
                
                # Play the audio file
                # On Windows, we can use os.startfile. We need a short delay to ensure it starts 
                # before we potentially process the next input
                def play_audio():
                    try:
                        os.startfile(temp_filename)
                        # Wait a bit before deleting (this is tricky without knowing audio length)
                        # A robust solution would use pygame or similar, but keeping it dependency-free
                        time.sleep(10) 
                        try:
                            os.remove(temp_filename)
                        except OSError:
                            pass
                    except Exception as e:
                        print(f"Could not play audio: {e}")
                
                # Run playback in a thread so terminal interaction isn't blocked
                threading.Thread(target=play_audio, daemon=True).start()
                return True
            else:
                print(f"Failed to generate audio. Status code: {response.status}")
                return False
    except urllib.error.HTTPError as e:
        print(f"API Error {e.code}: {e.read().decode('utf-8')}")
    except Exception as e:
        print(f"Error generating audio: {e}")
    return False

def main():
    print("="*50)
    print("ElevenLabs Conversational Terminal")
    print("="*50)
    
    print("Fetching available voices...")
    voices = get_voices()
    
    if not voices:
        print("Failed to fetch voices. Please check your API key.")
        return
        
    print("\nAvailable Voices:")
    for i, voice in enumerate(voices[:10]):  # Show first 10
        print(f"{i+1}. {voice['name']}")
        
    try:
        choice = int(input("\nSelect a voice (1-10) [Default: 1]: ") or 1)
        if 1 <= choice <= min(10, len(voices)):
            selected_voice = voices[choice-1]
        else:
            selected_voice = voices[0]
    except ValueError:
        selected_voice = voices[0]
        
    voice_id = selected_voice['voice_id']
    print(f"\nUsing voice: {selected_voice['name']}")
    print("\n--- Conversation Started ---")
    print("Type your message and press Enter to hear it spoken.")
    print("Type 'quit' or 'exit' to end the conversation.\n")
    
    while True:
        try:
            text = input("You: ")
            
            if text.lower() in ['quit', 'exit']:
                print("Ending conversation. Goodbye!")
                break
                
            if not text.strip():
                continue
                
            speak(text, voice_id)
            
            # Small delay to prevent API rate limiting on rapid inputs
            time.sleep(0.5)
            
        except KeyboardInterrupt:
            print("\nEnding conversation. Goodbye!")
            break

if __name__ == "__main__":
    main()
