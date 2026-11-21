# Bird Theatre: pre-production requirements

Bird Theatre is a functional starter and **not yet approved for hosting as a public user community**.

## Blocking security items

- Add per-IP and per-account rate limits to signup, login, camera creation and ingestion.
- Verify emails, add password resets, account deletion, consent and recovery workflows.
- Replace development `hibernate.ddl-auto=update` with version-controlled schema migrations and backups.
- Use an observability stack and structured logs scrubbed of secrets, camera stream credentials and raw RFID tag values.
- Strict upload/streaming provider URL safety: protect private stream origins, add URL allowlists or gateway-managed streams, do not expose signed private manifests in public listings.
- Add user/device token revocation audit and sessions cleanup; allow account owners to delete their records and exports.
- Add production-grade Content Security Policy and penetration/security testing, including third-party hls.js supply-chain strategy.
- Add role-scoped moderation tools, abusive camera reporting, copyright/takedown and child-safety rules, user content and privacy policies.
- Run an authorization matrix test for every authenticated endpoint and private/public resource.
- Build image recognition event dedup at the **individual event** level, using edge-generated IDs and database uniqueness; add retry queues, signed timestamps, replay protection and clock-skew handling.

## Media and inference

- The Spring Boot app does not ingest or retransmit RTSP, WebRTC or RTMP; it only stores HLS viewing URLs. Operate a separate authorized gateway, e.g. MediaMTX, and optionally CDN distribution.
- Measure bandwidth cost per concurrent viewer, latency and origin authentication. TLS HLS availability and CORS must be validated per provider.
- Supply a bird-specific, licensed, validated model with regional taxonomy and appropriate non-bird/low-confidence outputs. Accuracy depends on training and real environmental conditions.
- The reference vision script is **single-dominant-species classification**. Multi-bird counting and individual tracking require an actual detector/tracker, not only classifier thresholds.
- Include retries/offline queues and durable edge auth storage, hardware watchdogs and per-camera frame rate limits.
- Browser notifications work only while a tab is open and the browser permits them. Implement real Android FCM / Apple APNs and privacy-respecting preference settings for background delivery.

## Wildlife and community privacy

- Obscure exact physical locations (especially rare species and nest boxes). Use coarse public location labels.
- Avoid feeding/stressing wildlife, exposing nests to predators, illegal capture or tagging, or publishing people on camera without consent.
- RFID use is for the identifier of a **pre-existing authorized tag**; it is not an authorization to fit a tag. Regional and species-specific requirements vary.
- Raw tag identifiers must remain owner-scoped; public history presently shows derived species and time only.
- Clearly label confidence and estimated visit counts; never claim the camera can identify a particular untagged bird without verified multi-frame recognition.
