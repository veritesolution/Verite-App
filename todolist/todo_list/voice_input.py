import speech_recognition as sr
import sys

class VoiceInputHandler:
    def __init__(self):
        self.recognizer = sr.Recognizer()
        # Adjust for ambient noise if needed
        self.recognizer.dynamic_energy_threshold = True

    def listen(self, prompt="Listening..."):
        """
        Listens for audio input and returns the recognized text.
        Returns None if input could not be understood.
        """
        print(prompt)
        try:
            with sr.Microphone() as source:
                # Short adjustment for ambient noise
                self.recognizer.adjust_for_ambient_noise(source, duration=0.5)
                audio = self.recognizer.listen(source, timeout=10, phrase_time_limit=10)
            
            print("Processing voice...")
            text = self.recognizer.recognize_google(audio)
            print(f"You said: {text}")
            return text.lower()
            
        except sr.WaitTimeoutError:
            print("No speech detected.")
            return None
        except sr.UnknownValueError:
            print("Could not understand audio.")
            return None
        except sr.RequestError as e:
            print(f"Could not request results; {e}")
            return None
        except Exception as e:
            print(f"Error accessing microphone: {e}. Make sure PyAudio is installed correctly.")
            return None
