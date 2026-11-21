"""Deliberately simple, HTTPS-only device ingestion client. Never log device secrets."""
import json
import os
from urllib.request import Request, urlopen

PORTAL_URL = os.environ.get("PORTAL_URL", "https://bird-theatre.onrender.com").rstrip("/")
CAMERA_ID = os.environ.get("CAMERA_ID", "")
DEVICE_TOKEN = os.environ.get("DEVICE_TOKEN", "")


def send(path: str, payload: dict) -> None:
    if not PORTAL_URL.startswith("https://") or not CAMERA_ID or not DEVICE_TOKEN:
        raise RuntimeError("Set HTTPS PORTAL_URL, CAMERA_ID and DEVICE_TOKEN")
    url = f"{PORTAL_URL}/api/v1/cameras/{CAMERA_ID}/{path}"
    request = Request(url, data=json.dumps(payload).encode("utf-8"), method="POST", headers={
        "Content-Type": "application/json", "X-Device-Token": DEVICE_TOKEN})
    with urlopen(request, timeout=15) as response:
        if response.status not in (200, 201, 202):
            raise RuntimeError(f"Ingestion rejected: HTTP {response.status}")
