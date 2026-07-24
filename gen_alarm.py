import math
import wave
import struct

def generate_siren(filename):
    sample_rate = 44100
    duration = 10.0
    
    with wave.open(filename, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        
        phase = 0.0
        for i in range(int(sample_rate * duration)):
            t = i / sample_rate
            
            # Simple Hoch-Tief-Hoch-Tief (Ta-Tü-Ta-Tü)
            # 0.5 Sekunden hoch (800 Hz), 0.5 Sekunden tief (600 Hz)
            is_high = (t % 1.0) < 0.5
            freq = 800.0 if is_high else 600.0
            
            phase += 2.0 * math.pi * freq / sample_rate
            
            # Halbe Lautstärke für klaren Ton ohne Verzerrung
            value = int(16384.0 * math.sin(phase))
            w.writeframesraw(struct.pack('<h', value))
            
if __name__ == "__main__":
    generate_siren("app/src/main/res/raw/custom_alarm.wav")
