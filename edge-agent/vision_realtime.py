"""Bird Theatre realtime camera inference, multi-bird visits and offline queue.

Requires a live RTSP/webcam/video source; no raw frames go to the server.
Run on a camera-side computer, NOT the shared Render portal instance.
"""
import logging
import os
import time
from pathlib import Path

from event_queue import EventQueue
from recognition import TorchvisionBirdDetector, BioClipSpeciesClassifier, crop_bird, load_species
from shared import send
from visit_tracker import Observation, VisitTracker

LOG = logging.getLogger("bird_theatre.vision")


def env_float(name, default, minimum=0.0):
    value = float(os.getenv(name, default))
    if value < minimum:
        raise ValueError(f"{name} must be >= {minimum}")
    return value


def main():
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    try:
        import cv2
        import torch
        from PIL import Image
    except ImportError as exc:
        raise SystemExit("Install edge-agent/requirements-vision.txt and matching torch/torchvision") from exc

    source = os.getenv("CAMERA_SOURCE", "")
    if not source:
        raise SystemExit("Set CAMERA_SOURCE to an RTSP URL, a video file or webcam index")
    if not os.getenv("CAMERA_ID") or not os.getenv("DEVICE_TOKEN"):
        raise SystemExit("Set CAMERA_ID and DEVICE_TOKEN from Bird Theatre camera registration")
    if not os.getenv("PORTAL_URL", "").startswith("https://"):
        raise SystemExit("Set PORTAL_URL to the HTTPS address of your deployed Bird Theatre portal")
    file_source = Path(source).is_file()
    capture_source = int(source) if source.isdecimal() else source
    device = os.getenv("VISION_DEVICE", "auto")
    if device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    sample_seconds = env_float("SAMPLE_SECONDS", "1.5", 0.05)
    detector_confidence = env_float("DETECTOR_MIN_SCORE", "0.65", 0.01)
    absence_seconds = env_float("ABSENCE_SECONDS", "10", 0.2)
    min_hits = int(os.getenv("MIN_CONFIRM_FRAMES", "2"))
    species_csv = os.getenv("SPECIES_CSV", str(Path(__file__).with_name("species_ontario.csv")))
    species = load_species(species_csv)

    LOG.info("Loading bird detector and BioCLIP 2 on %s; %d candidate species", device, len(species))
    detector = TorchvisionBirdDetector(device=device, threshold=detector_confidence)
    classifier = BioClipSpeciesClassifier(species, device=device,
        min_similarity=env_float("SPECIES_MIN_SIMILARITY", "0.18"),
        min_margin=env_float("SPECIES_MIN_MARGIN", "0.025"))
    tracker = VisitTracker(min_hits=min_hits, absence_seconds=absence_seconds)
    queue = EventQueue(os.getenv("EVENT_QUEUE_FILE", str(Path(__file__).parent / ".spool" / "events.sqlite3")), send)
    stream = None
    last_sample = 0.0
    last_flush = 0.0

    try:
        while True:
            if stream is None or not stream.isOpened():
                if stream is not None:
                    stream.release()
                stream = cv2.VideoCapture(capture_source)
                if not stream.isOpened():
                    LOG.warning("Camera unavailable; retrying (URL and credentials are not logged)")
                    time.sleep(5)
                    continue
                LOG.info("Camera connected")
            ok, frame = stream.read()
            if not ok:
                if file_source:
                    break
                stream.release()
                stream = None
                LOG.warning("Camera disconnected; reconnecting")
                time.sleep(3)
                continue
            now = time.monotonic()
            if now - last_sample < sample_seconds:
                continue  # keep consuming the live camera, avoiding growing RTSP buffer latency
            last_sample = now
            image = Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
            detected = detector.detect(image)
            observations = []
            for box, detection_score in detected:
                label, species_score = classifier.classify(crop_bird(image, box))
                observations.append(Observation(box, label, min(detection_score, species_score) if label != "Unidentified" else 0.0))
            for event in tracker.update(observations, now):
                queue.add("detections", event)
                LOG.info("Visit %s: %s", event["eventType"], event["species"])
            if now - last_flush >= 5:
                last_flush = now
                try:
                    queue.flush()
                except Exception as exc:
                    LOG.warning("Portal unavailable; %d events held for retry (%s)", queue.pending_count(), type(exc).__name__)
    except KeyboardInterrupt:
        LOG.info("Stopping bird camera monitor")
    finally:
        if stream is not None:
            stream.release()
        for event in tracker.close():
            queue.add("detections", event)
        try:
            queue.flush(batch_size=100)
        except Exception:
            LOG.warning("Some events remain queued for next start")
        queue.close()


if __name__ == "__main__":
    main()
