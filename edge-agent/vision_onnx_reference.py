"""Reference frame classifier pipeline; requires your own compatible bird-species ONNX model.

Model contract: RGB 224x224 normalized by ImageNet mean/std; NCHW float32 input;
output [1,num_species] logits/probabilities; one label per output class, including
"background" as a non-bird class. Not a bird detector or individual tracker.
"""
import os
import time
import cv2
import numpy as np
import onnxruntime as ort
from shared import send


def main():
    model_path = os.environ.get("VISION_MODEL", "bird-species.onnx")
    labels_path = os.environ.get("VISION_LABELS", "species.txt")
    source = os.environ.get("CAMERA_SOURCE", "")
    if not source:
        raise RuntimeError("CAMERA_SOURCE (RTSP/video-file) is required")
    with open(labels_path, encoding="utf-8") as f:
        labels = [line.strip() for line in f if line.strip()]
    session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    threshold = float(os.environ.get("MIN_CONFIDENCE", "0.75"))
    sample_seconds = max(1, float(os.environ.get("SAMPLE_SECONDS", "3")))
    absence_seconds = max(5, float(os.environ.get("ABSENCE_SECONDS", "20")))
    if not (0.0 <= threshold <= 1.0):
        raise ValueError("MIN_CONFIDENCE must be from 0 to 1")
    active_species, last_seen = None, 0.0
    cap = cv2.VideoCapture(source)
    try:
        while True:
            if not cap.isOpened():
                cap.release()
                time.sleep(5)
                cap = cv2.VideoCapture(source)
                continue
            ok, frame = cap.read()
            if not ok:
                cap.release()
                time.sleep(3)
                cap = cv2.VideoCapture(source)
                continue
            frame = cv2.cvtColor(cv2.resize(frame, (224, 224)), cv2.COLOR_BGR2RGB)
            tensor = np.asarray(frame, dtype=np.float32) / 255.0
            tensor = (tensor - [0.485, 0.456, 0.406]) / [0.229, 0.224, 0.225]
            tensor = np.transpose(tensor, (2, 0, 1))[None, :, :, :].astype(np.float32)
            logits = session.run(None, {input_name: tensor})[0].reshape(-1)
            if logits.size != len(labels):
                raise ValueError("Class count and labels file differ")
            probs = np.exp(logits - np.max(logits))
            probs /= probs.sum()
            index = int(np.argmax(probs))
            found = labels[index] if probs[index] >= threshold and labels[index].lower() not in ("background", "empty", "none") else None
            now = time.monotonic()
            if found is not None:
                if active_species != found:
                    if active_species:
                        send("detections", {"species": active_species, "eventType": "DEPARTURE", "confidence": 1.0})
                    send("detections", {"species": found, "eventType": "ARRIVAL", "confidence": float(probs[index])})
                    print(f"Detected: {found} ({float(probs[index]):.2f})")
                active_species = found
                last_seen = now
            elif active_species and now - last_seen >= absence_seconds:
                send("detections", {"species": active_species, "eventType": "DEPARTURE", "confidence": 1.0})
                print(f"Departed: {active_species}")
                active_species = None
            time.sleep(sample_seconds)
    finally:
        cap.release()

if __name__ == "__main__":
    main()
