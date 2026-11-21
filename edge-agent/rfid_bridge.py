"""Serial RFID adapter for readers emitting lines: TAG_UID[,ARRIVAL|DEPARTURE].

Hardware-specific reader decoders must translate their protocol to this format.
Do not alter animals or fit tags without the applicable authorizations.
"""
import os
import re
import time
import serial
from shared import send


def main():
    port = os.environ.get("RFID_SERIAL_PORT", "/dev/ttyUSB0")
    baud = int(os.environ.get("RFID_BAUD", "9600"))
    last_sent = {}
    with serial.Serial(port, baudrate=baud, timeout=2) as reader:
        while True:
            raw = reader.readline().decode("ascii", errors="ignore").strip()
            if not raw:
                continue
            parts = [part.strip() for part in raw.split(",")]
            tag = parts[0]
            direction = parts[1].upper() if len(parts) > 1 else "ARRIVAL"
            if not re.fullmatch(r"[A-Za-z0-9_-]{4,100}", tag) or direction not in ("ARRIVAL", "DEPARTURE"):
                print("Skipped unrecognized reader message")
                continue
            key = (tag, direction)
            now = time.monotonic()
            if now - last_sent.get(key, float('-inf')) < 60:
                continue
            send("rfid-scans", {"tagUid": tag, "eventType": direction})
            last_sent[key] = now
            print(f"RFID scan relayed: ...{tag[-4:]} ({direction})")

if __name__ == "__main__":
    main()
