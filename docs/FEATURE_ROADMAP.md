# Bird Theatre product plan

## Visitor experience

Explore → choose a public feeder/nest camera → watch HLS broadcast → read recent species appearances → optionally follow a station and receive species-specific alerts (follow/favourites not implemented).

## Camera owner experience

Sign in → add station and general region → provide owned/publicly shareable HTTPS HLS URL → save one-time device key securely → configure edge inference/RFID reader → create alert rule → review sightings and 30-day counts.

## Suggested future screens

- Bird species encyclopedia: maps, field notes, seasonal trends, identification confidence.
- Community: photo clips (with rights and moderation), station profiles, favourites, comments, guest activity feeds.
- Station management: live-health, privacy, HLS quality, per-viewer latency, schedule, owner moderation, quick snapshots.
- Wildlife research: export anonymized CSV, migration charts and optional data partnerships.
- RFID: tag event timeline and known individual history scoped to the station owner; reader health and scans with signal strength, when the device supports it.
- Alerts: species filters, quiet hours, digest and push notifications on Android/iOS, all with easy controls.

## Platform strategy

Keep shared domain model and server-side APIs. The initial native clients wrap web workflows with native navigation; as the product matures, implement platform-specific camera preview, push alerts, local station controllers, notifications permissions and offline queues. Do not promise full native parity at this stage.
