# From camera to livestream

Bird Theatre accepts **HTTPS HLS .m3u8 playback URLs**. Ordinary IP cameras frequently provide **RTSP**, which browsers do not play directly. A separate service must convert/distribute video.

An example approach is [MediaMTX](https://mediamtx.org/docs/kickoff/introduction), which supports ingest from RTSP-capable cameras or publishers and HLS reading. See the [official publishing and reading guide](https://mediamtx.org/docs/features/publish-and-read) and [configuration reference](https://mediamtx.org/docs/references/configuration-file). Configuration varies with deployment version and security needs.

Recommended setup:

1. Keep the RTSP camera on a protected local network; use its native credentials only inside your local streaming gateway.
2. Publish/remux to an HLS origin. Expose it through HTTPS and configure a controlled cross-origin response for the Bird Theatre viewer (HLS manifest and segments).
3. Confirm it plays on Chrome, Safari, iOS and Android before adding the URL in Bird Theatre.
4. Set a coarse public region; do not expose an IP camera's LAN/private IP address or credentials in the stream URL.
5. Run inference near the camera from the local camera source, not from the public HLS stream, when possible.
6. Protect private streams at the origin; selecting **Private** in Bird Theatre only hides the directory record.

For small trials, a single edge computer can host the camera gateway and inference. A real community with many viewers may need a managed video provider or CDN to avoid excessive upstream bandwidth and latency. MediaMTX is not deployed as part of this repository's Render Blueprint.
