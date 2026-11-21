# Bird Theatre — Consolidated Hardware Prototype Proposal (Revision G)

**Date:** 21 November 2026  
**Status:** Preliminary industrial design and engineering concept; **not physically tested or fabrication-ready**  
**Scope:** Original Bird Theatre smart birdhouse, circular seed feeder, nesting box, owl house, and modular observation post, all using the proposed interchangeable BT-CAM platform.

> This is the hardware proposal and manufacturer briefing. It **does not** indicate that BT-CAM hardware, live camera provisioning, solar systems, production AI infrastructure, gateway operation, or any physical wildlife installation is deployed. For the current software starter and release status see [README.md](../README.md).

## 1. Product vision and design baseline

Preserve the **five original Bird Theatre designs** and their identities: natural-wood front panels; charcoal weather-resistant housings; integrated or optional roof-top solar assist; restrained camera modules; and accessible, serviceable construction. Do not substitute the later experimental infographics as approved production shapes.

| Product | Original appearance to preserve | Practical camera change |
|---|---|---|
| **BH-01 Smart Birdhouse** | Peaked roof, wood face, rounded charcoal enclosure, original vertical camera under round entrance | A single **removable straight-out dowel** immediately below the exterior camera for observation-only use; adjustable lens aimed at bird; optional interior nest camera |
| **FD-02 Smart Seed Feeder** | Circular solar canopy, clear cylindrical hopper, circular feeding ring | Reposition underside camera radially outside opaque seed column toward a defined front feeding sector |
| **NB-03 Nesting Box Pro** | Simple timber box with sloped roof and cleaning access | Protected upper-corner interior camera focused on nest floor; no entrance perch |
| **OW-04 Owl House Pro** | Rustic, enclosed, deep owl nest cavity | Species-specific dimensions; hidden upper internal camera and optional second approach camera |
| **PO-05 Observation Post** | Slim dark post and round solar canopy with modular accessories | Independently targeted upper-perch and lower-tray cameras; adjustable stations |

**Preservation rule:** Avoid visual redesign except where a mechanical, ecological, service or optical requirement requires an explicitly reviewed change.

## 2. Species welfare and use modes

The **birdhouse's forward-projecting dowel is an optional observation accessory**, not a standard feature of a nesting box. Remove it before placing the structure into active cavity-nesting use. Ordinary cavity nesters do not need an entrance perch, and predator access can increase if it is retained. Never inspect or open an occupied nest merely to adjust a camera; position hardware before nesting season.

Nest-box cavity, hole, ventilation, drainage, predator protection, mounting height and sun exposure must be selected **for the actual target species and locale**. The proposed owl reference is Eastern Screech-Owl, subject to species-specific wildlife review; a generic small-owl enclosure is not a final design.

## 3. Camera placement and initial optical targets

All aiming values are **starting adjustments for physical tests**. The decisive inputs are lens position, intended bird head/body position, vertical and horizontal fields of view, and real minimum-focus distance.

| Product / camera | Where it mounts | Target position | Trial optical target |
|---|---|---|---|
| BH-01 exterior | Original vertical faceplate below entrance | Bird on removable 90–120 mm straight-out perch | Approx. 0.10–0.25 m subject distance; aim adjustable ~10–25° downward, then centre on head/body |
| BH-01 interior | Protected upper inside corner | Entire usable nest floor including raised material | Approx. 0.12–0.30 m; wide close-focus view |
| FD-02 feed | At front edge under solar canopy, clear of hopper silhouette | Forward feeding tray / rim, bird bill and head | Approx. 0.15–0.35 m; ~20–45° trial downward |
| NB-03 interior | Protected corner near roof | Full floor, adults, eggs and chicks | Approx. 0.12–0.30 m; wide lens; IR variant |
| OW-04 interior | Upper rear or side corner | Species-sized floor, adult and young | Approx. 0.25–0.60 m (geometry dependent) |
| OW-04 approach (optional) | Under eave/side weather hood | Arrival / departure route | Approx. 0.4–1.5 m |
| PO-05 upper | Under circular canopy | Chosen upper perch | Approx. 0.25–0.50 m |
| PO-05 lower | Mid-post shielded pod | Lower tray or landing bar | Approx. 0.20–0.45 m |

### 3.1 Straight-out birdhouse perch — crucial geometry

- Trial dowel: **90–120 mm projection**, roughly **12–18 mm diameter**, securely removable; final size to match appropriate target birds.
- Keep the camera just above it and shielded against rain, but do not assume a nominal camera tilt alone gives useful footage.
- Define head, body and feet target positions. For vertical offset `Δh` and outward distance `x`, the downward viewing angle is **atan(Δh / x)**. For example, a target 70 mm below the lens and 115 mm outward corresponds to about **31° downward**. A head elevated above the perch requires a shallower angle.
- A previous 2D geometric study illustrated a nominal 14° axis and 65° vertical field; it **was not a measurement on a real lens**. These are not final settings, and focus/glare/occlusion must be tested.
- A camera mounted beneath the entrance may see a bird's back or underside; test lens pitch and modest yaw, and measure real bird-head framing.
- An observation perch **must not be retained on an occupied cavity-nesting box**.

### 3.2 Circular feeder visibility

A single camera under the canopy cannot provide close-up, unobstructed views of the entire 360° ring. Prioritize a **forward target sector** (approximately 90–130°), outside the seed-cylinder obstruction. Test left, centre and right natural feeding positions, including backlighting and reflection off the clear hopper. Use a removable washable tray and drainage.

### 3.3 Nest and owl views

A protected top-corner camera should cover the **full actual usable floor**, including expected variation as bedding rises. Avoid projecting fixtures inside the cavity, exposed hot electronics, visible lamps and opening an occupied nest. For the owl product choose a target species before setting nest cavity and entrance dimensions.

### 3.4 Modular post views

Prefer an **upper perch camera** and a second **lower tray camera** rather than overpromising 360° species-recognition detail from one lens. Make perch/tray arms adjustable and removable for cleaning, with wiring enclosed in the post and a stable wind-rated base.

## 4. BT-CAM universal camera system

One **replaceable common control/electronics platform** with optional lens heads and mounting adapters; do not force an identical lens and focus distance onto all five products.

| Subsystem | Proposed development requirement |
|---|---|
| Main processor | **ESP32-P4** plus Wi-Fi/BLE companion module for custom OEM feasibility; not a finalized part |
| Evaluation alternative | Raspberry Pi Zero 2 W + Camera Module 3 / NoIR for bench optical and firmware experiments |
| Video | 1080p H.264 target; event-mode low-power profiles; higher resolution only after temperature/power validation |
| Camera heads | Nest close-focus / IR; visitor daylight/close focus; wider owl/observation variant as required |
| Wireless | 2.4 GHz Wi-Fi for video; BLE optional for initial provisioning, **not** the primary live-video path |
| Streaming | Owner-controlled local RTSP or secure equivalent; HTTPS HLS gateway for existing Bird Theatre clients |
| Night vision | Low-output 940 nm IR candidate, verified for glare, heating and bird disturbance |
| Local storage | Optional microSD with encrypted or appropriately protected clips |
| Firmware | Unique device identity, signed update/rollback, secure pairing, credential rotation, watchdog/reconnect |
| Environmental | Exterior housing target IP65+, condensate, UV, freeze, heat and mechanical tests |
| Manufacturing outputs | Editable schematics/PCB CAD, Gerbers, BOM/AVL, enclosure STEP, source firmware, SDK/API, fixture and QA plans |

**Important:** An ESP32-P4 platform and imaging sensor still require actual board, firmware, lens, RF and thermal validation; video capability of a chipset does not prove final product performance. In a two-camera house, budget for two sensor heads with suitable controller support (or two complete camera units) rather than assuming a single-CSI connection can handle both.

## 5. Solar, battery and mechanical power design

Solar is **assist**, not a 24/7 guarantee. Measure the actual video, standby, Wi-Fi reconnect and IR loads before selecting panel or cell capacity.

Illustrative only:

| Operating case | Energy per day / yield |
|---|---|
| 1 W average camera | **24 Wh/day** |
| 3 W average streaming camera | **72 Wh/day** |
| 10 W roof panel × 2 equivalent sun hours × 70% net | **~14 Wh/day** |
| 20 W external panel × 2 equivalent sun hours × 70% net | **~28 Wh/day** |
| 150 Wh usable battery at 3 W average | **~2 days without charging** |

Use a separated and replaceable battery bay; charger/BMS protection; **low-temperature charge cut-off**, especially for LiFePO4; strain relief, outdoor cable glands, sealed camera window, and a remote-panel accessory for shade. Do not embed batteries where they heat the nesting cavity or require access through an occupied nest.

## 6. Bird Theatre application integration

The repository currently describes Spring Boot account/camera records, an **authorized HTTPS HLS viewer URL**, and a pretrained Torchvision/BioCLIP 2 edge-agent reference. The portal **does not currently proxy live video or upload raw frames**; the owner supplies a streaming URL. Preserve that boundary when designing hardware and gateway features.

**Proposed pathway:**

```text
BT-CAM ---- local Wi-Fi / secure RTSP ---- owner-controlled media gateway
   |                                           |
   |                                           +--> authorized HLS URL --> web / mobile / desktop
   |
   +--> local vision edge agent --> species/visit events --> authenticated Spring Boot API --> database
   |
   +--> device telemetry, provisioning and OTA [new work, not implemented]
```

Proposed, not deployed: QR/BLE onboarding; per-device cryptographic identity; secure camera discovery; remote camera credentials; stream gateway setup; battery/solar telemetry; firmware update management; consent-controlled publishing; retention and moderation policies; mobile push alerts.

## 7. Camera-build alternatives

| Path | Recommendation | Main trade-off |
|---|---|---|
| **A. ESP32-P4 custom BT-CAM** | Preferred manufacturing development track | Firmware/RF/ISP/thermal/OTA engineering effort |
| **B. Pi Zero 2 W + Camera 3 / NoIR** | Fastest bench proof and lens experiment | Bulk and continuous power |
| **C. Arducam/OEM sensor head** | Parallel optical procurement/RFQ | Ensure lens support and IP/source rights |
| **D. Open RTSP/ONVIF OEM camera module** | Backup to test Bird Theatre video gateway early | Supplier cloud/firmware lock-in or higher power |
| **E. USB UVC module + host** | Laboratory image-quality reference only | Requires separate computer/host |

Potential quote recipients, subject to direct qualification: [Arducam](https://www.arducam.com/blog/custom-design/), [Seeed Studio](https://www.seeed.cc/customization), [e-con Systems](https://www.e-consystems.com/oem-custom-cameras.asp) and [NeuronicWorks (Toronto)](https://neuronicworks.com/engineering-design-services/). **No vendor has yet quoted or accepted the design.**

## 8. Staged prototype plan

| Phase | Build | Exit evidence |
|---|---|---|
| P0 — Optical bench | Camera boards, lenses, IR, charts and bird-sized targets | Focus/FOV/exposure at 0.10, 0.20, 0.35 and 0.60 m |
| P1 — Birdhouse | 1:1 original shell with removable straight-out perch, external and internal fixtures | In-focus head/body/feet on perch; full nest floor at variable bedding height |
| P2 — Feeder | 1:1 original circular hopper, forward camera and washable tray | Natural feeding capture at left/centre/right, no critical hopper obstruction |
| P3 — Owl and post | Species-reviewed owl mock-up; two viewing zones on post | Usable low-light floor view and independent upper/lower station coverage |
| P4 — Outdoor system | Battery/solar/gateway and enclosure trials | Multi-day measurement, weather and safe low-temperature charging, reconnect |
| P5 — OEM pilot | 5–10 integrated units before tooling | Signed-off optics, firmware, power, serviceability, safety and QA |

**Minimum acceptance artifacts:** unedited day/night test clips, FOV/focus measurement chart, IR-reflection and glare shots, power logs, moisture and temperature findings, full-size construction photos, a demonstrated camera-to-app event path, and a written wildlife-safety review. **Computer-generated renders and 2D cones are not acceptance evidence.**

## 9. Manufacturer RFQ checklist

Request **separate line items** for camera electronics/optics NRE, firmware/security NRE, industrial/mechanical design, prototype assemblies, tooling, compliance/certification and production unit cost at **5 / 20 / 50 / 100 / 500 / 1,000** units where practical. Include lead times, warranty, MOQ, part longevity, interchangeable lens calibration, secure provisioning, cloud independence, test reports, tooling ownership and firmware source-license terms.

Expected deliverables: signed-off original-style industrial drawings, editable mechanical STEP/CAD, PCB source files, assembly Gerbers/BOM/AVL, bootloader/firmware source, API and flashing docs, test fixtures, prototype manufacturing report and ownership/license schedule. Consider Canadian **ISED/ICES**, radio/EMC, battery and product-safety obligations before sale.

Budget is **unquoted**: benches may cost hundreds to low thousands in parts; contracted custom electronic prototypes can enter the five figures; a production-intent certified family may cost tens of thousands more. Request bids rather than treating estimates as firm costs.

## 10. Release gates / change control

1. **G1 — Original appearance:** product owner approves unchanged original silhouettes and any documented physical compromises.
2. **G2 — Optical proof:** actual footage covers target bird zones at measured focus and FOV by day/night.
3. **G3 — Wildlife safety:** species-appropriate entrance, cavity, drainage, ventilation, predator resistance, no active-nest observation perch.
4. **G4 — Power/weather:** full mode consumption, low-temperature protection, condensation/UV and field durability.
5. **G5 — Software:** secure provisioning, video authorization, reconnect, recognition event path and privacy/retention.
6. **G6 — Manufacturing:** ownership, BOM, CAD, source, sample QA, supply chain and certificate plan.

### Recommended initial procurement decision

**Build the shared BT-CAM evaluation platform and two full-size mock-ups first: BH-01 Smart Birdhouse and FD-02 Circular Feeder.** Lock final lens focus and camera mount angles only after the actual footage tests; add nesting box, owl house and post on the proven same platform.

## 11. Related resources

- [Bird Theatre software architecture and status](../README.md)
- [ESP32-P4](https://www.espressif.com/en/products/socs/esp32-p4)
- [Raspberry Pi Camera Module 3](https://www.raspberrypi.com/documentation/accessories/camera-module-3.html)
- [Cornell NestWatch nest box guidance](https://nestwatch.org/learn/all-about-birdhouses/)
- Source study lineage (created 21 November 2026): original product Option 1–5 boards; Bird Theatre camera manufacturing brief Rev A; product proposal Revisions B–F; Rev E camera placement and alternatives matrices; camera geometry feasibility study and four-page easy-view annex.

**Document-control note:** This Markdown is the version-controlled product/hardware brief. The accompanying illustrated 16-page Revision G PDF/DOCX was produced separately; imagery and simulation are not fabrication drawings.
