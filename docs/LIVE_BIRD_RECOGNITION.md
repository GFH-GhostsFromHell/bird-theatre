# Live bird recognition: implementation and setup

Bird Theatre now has an **optional, executable pretrained two-stage vision agent** in `edge-agent/vision_realtime.py`:

- **Bird localization:** Torchvision `fasterrcnn_mobilenet_v3_large_320_fpn` with COCO pretrained weights, recognizing the **generic bird** class and outputting bounding boxes. Multiple birds in a frame can be detected. [PyTorch documentation](https://docs.pytorch.org/vision/stable/models/generated/torchvision.models.detection.fasterrcnn_mobilenet_v3_large_320_fpn.html).
- **Species recognition:** `imageomics/bioclip-2` (MIT-licensed) via OpenCLIP; compares each bird crop with a **user-defined candidate set** of species common/scientific names. [Model card](https://huggingface.co/imageomics/bioclip-2). The supplied `species_ontario.csv` contains a small, non-exhaustive sample of Ontario feeder species. For other regions, **replace the CSV** or extend it with scientifically checked names.
- **Visits:** An approximate bounding-box tracker requires successive bird detections, emits `ARRIVAL` once per track, then `DEPARTURE` after a timeout. It can count two visible birds of the same species as separate estimated visits. It **cannot identify a bird individual**, cannot reliably maintain identity during occlusion, and can double-count birds that leave and return or move erratically.
- **Delivery:** Edge events carry an `eventId` and ephemeral `trackId`; they're written to a local SQLite outbox. The Spring API acknowledges repeat `eventId`s idempotently, so reconnects don't multiply counts. It preserves existing older clients without track IDs. **No raw camera frames are uploaded** by the agent.

## Recommended deployment: local edge device

Run recognition on a PC/mini PC/compatible NVIDIA edge computer **at the camera site**. A machine with an RTX-class NVIDIA GPU and a supported CUDA PyTorch wheel will be noticeably faster than CPU for these two models. Check the PyTorch installation selector for your operating system and driver: https://pytorch.org/get-started/locally/ . The application server hosts the community and stores detection *events*, not video inference.

For an RTSP camera, configure its local stream with the camera maker's instructions. Video viewing is separate: use an HTTPS HLS stream gateway/provider to make the camera available to community viewers, as described in `docs/STREAMING_SETUP.md`. Never expose RTSP usernames/passwords in public links or screenshots.

## Install and start (Windows PowerShell example)

Prerequisites: Python 3.11 or 3.12 (64-bit), camera accessible from the machine, functional Bird Theatre API on HTTPS, and a registered camera with its one-time device token.

```powershell
cd edge-agent
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
# Install a matching torch + torchvision pair from the current PyTorch selector,
# choosing CUDA wheels only when a supported NVIDIA driver is installed.
pip install torch torchvision
pip install -r requirements-vision.txt
$env:CAMERA_SOURCE = 'rtsp://192.168.1.100:554/stream'
$env:CAMERA_ID = 'CAMERA_UUID_FROM_PORTAL'
$env:DEVICE_TOKEN = 'ONE_TIME_CAMERA_DEVICE_TOKEN'
$env:PORTAL_URL = 'https://YOUR_REAL_BIRD_THEATRE_HOST'
$env:VISION_DEVICE = 'auto'
python vision_realtime.py
```

Camera input supports `CAMERA_SOURCE=0` for the first USB webcam or a local video file for testing. Linux/macOS users can set the same environment variables with `export` in a shell. **The script does not auto-load `.env` files**, to avoid accidentally injecting device credentials on shared machines.

The first launch requires internet to download the Torchvision pretrained detector and BioCLIP 2 model weights from their official sources (often large). They are cached locally. Neither model file is bundled with the repository; confirm checkpoint download and runtime performance on your hardware.

## Key settings

| Variable | Default | Purpose |
|---|---|---|
| `SAMPLE_SECONDS` | `1.5` | Seconds between inference attempts; video capture still consumes intervening frames |
| `DETECTOR_MIN_SCORE` | `0.65` | Minimum generic-bird detector score |
| `MIN_CONFIRM_FRAMES` | `2` | Required detections for an arrival; reduce for very brief visits |
| `ABSENCE_SECONDS` | `10` | Time before a missing tracked bird is marked as departed |
| `SPECIES_CSV` | `species_ontario.csv` | Species to consider for classification, **not** a worldwide database |
| `SPECIES_MIN_SIMILARITY` | `0.18` | Minimum BioCLIP image/text similarity; needs field calibration |
| `SPECIES_MIN_MARGIN` | `0.025` | Minimum difference from runner-up; near-ties become Unidentified |
| `EVENT_QUEUE_FILE` | `.spool/events.sqlite3` | Local event storage while offline |

**Do not treat BioCLIP scores as scientifically calibrated probabilities.** `confidence` is an approximate, candidate-relative model score and low confidence or unsupported species must remain *Unidentified*. Test on local daylight/nighttime video and confirm manually; improve taxonomy, lens position, motion blur and crop sizes before relying on automatic records.

When running on CPU, 1.5-second sampling may not be achievable if inference takes longer; actual event latency is higher than the setting. Full continuous frame-rate bounding-box overlays, individual animal re-identification, nocturnal infrared species accuracy, and synchronized RFID/visual identification are not delivered yet.

## Privacy, security and cost

The local agent normally uploads **metadata only**. The HTTPS HLS video stream is independently published through your provider. Secure private cameras at the stream gateway too; a hidden Bird Theatre listing does not revoke a globally accessible stream URL. Keep the camera token in local secret storage. Don't put public nest-site coordinates, vulnerable species locations, faces or neighboring houses into the feed.

Model code licenses: Torchvision is BSD-style; the BioCLIP 2 checkpoint lists MIT. Each dependency/weight and dataset requires its own license review for a future commercial release. In particular, **Ultralytics YOLO is not used by this starter** because its default AGPL-3.0/Enterprise licensing deserves separate review. Running the models at home avoids a per-frame recognition API bill, but GPU electricity, video relay/bandwidth and platform hosting still cost money.

## Testing

```bash
python -m unittest discover -s edge-agent/tests -v
```

These are **unit tests for tracking and queue behavior**, not AI detection/precision benchmarks. End-to-end camera recognition requires downloading actual model weights and having a real video source. No camera, GPU inference, production portal or hardware RFID was tested in this environment.
