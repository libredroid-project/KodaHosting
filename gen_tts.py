import pyttsx3

engine = pyttsx3.init()
voices = engine.getProperty('voices')
male_voice = None
for v in voices:
    if 'Stefan' in v.name or 'David' in v.name or 'Sam' in v.name:
        male_voice = v.id
        break
if male_voice:
    engine.setProperty('voice', male_voice)

# Set rate to a normal, commanding speed (default is usually 200, 160 is good)
engine.setProperty('rate', 160)
engine.setProperty('volume', 1.0)

text_de = "Internetverbindung verloren. Bitte sofort neu verbinden."
text_en = "Internet connection lost. Please try to reconnect immediately."

engine.save_to_file(text_de, "app/src/main/res/raw/alarm_voice_de.wav")
engine.runAndWait()

engine.save_to_file(text_en, "app/src/main/res/raw/alarm_voice_en.wav")
engine.runAndWait()

print("Voices generated at normal speed!")
