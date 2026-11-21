package ca.gfh.birdtheatre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
class BirdApi {
    private final AccountRepo accounts;
    private final CameraRepo cameras;
    private final SightingRepo sightings;
    private final RfidRepo tags;
    private final AlertRepo alertRules;
    private final NotificationRepo notices;
    private final Auth auth;

    BirdApi(AccountRepo accounts, CameraRepo cameras, SightingRepo sightings, RfidRepo tags,
            AlertRepo alertRules, NotificationRepo notices, Auth auth) {
        this.accounts=accounts; this.cameras=cameras; this.sightings=sightings;
        this.tags=tags; this.alertRules=alertRules; this.notices=notices; this.auth=auth;
    }

    record Credentials(
        @NotBlank @Pattern(regexp="[a-zA-Z0-9_]{3,32}") String handle,
        @NotBlank @Size(min=2,max=80) String displayName,
        @NotBlank @Size(min=10,max=128) String password) {}
    record Login(@NotBlank String handle,@NotBlank String password) {}
    record CameraInput(@NotBlank @Size(max=90) String name,
                       @NotBlank @Pattern(regexp="FEEDER|HOUSE") String kind,
                       @NotBlank @Size(max=80) String region,
                       @Size(max=300) String description,
                       @NotBlank @Size(max=1000) String streamUrl,
                       boolean publicListing) {}
    record Detection(@NotBlank @Size(max=120) String species,
                     @NotBlank @Pattern(regexp="ARRIVAL|DEPARTURE") String eventType,
                     @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
                     UUID eventId,
                     @Size(max=80) String trackId) {}
    record RfidScan(@NotBlank @Size(max=100) String tagUid,
                    @NotBlank @Pattern(regexp="ARRIVAL|DEPARTURE") String eventType) {}
    record RfidInput(@NotBlank @Size(max=100) String tagUid,
                     @NotBlank @Size(max=80) String nickname,
                     @NotBlank @Size(max=120) String species) {}
    record AlertInput(@NotBlank @Size(max=120) String species,
                      @Min(1) @Max(1440) int cooldownMinutes) {}

    @PostMapping("/auth/signup") @ResponseStatus(HttpStatus.CREATED)
    Object signup(@Valid @RequestBody Credentials input) {
        return auth.signup(input.handle(),input.displayName(),input.password());
    }
    @PostMapping("/auth/login")
    Object login(@Valid @RequestBody Login input) { return auth.login(input.handle(),input.password()); }
    @PostMapping("/auth/logout")
    Object logout(@RequestHeader(value="Authorization",required=false) String authorization) {
        auth.logout(authorization); return Map.of("ok",true);
    }
    @GetMapping("/me")
    Object me(@RequestHeader(value="Authorization",required=false) String authorization) {
        Account a=auth.require(authorization);
        return Map.of("id",a.id,"handle",a.handle,"displayName",a.displayName,"joinedAt",a.joinedAt);
    }
    @GetMapping("/cameras")
    Object list() { return cameras.findByPublicListingTrueOrderByCreatedAtDesc().stream().map(this::camera).toList(); }
    @GetMapping("/me/cameras")
    Object mine(@RequestHeader(value="Authorization",required=false) String authorization) {
        return cameras.findByOwnerIdOrderByCreatedAtDesc(auth.require(authorization).id).stream().map(this::camera).toList();
    }
    @GetMapping("/cameras/{id}")
    Object one(@PathVariable UUID id,@RequestHeader(value="Authorization",required=false) String authorization) {
        return camera(viewable(id,authorization));
    }

    @PostMapping("/cameras") @ResponseStatus(HttpStatus.CREATED)
    Object createCamera(@RequestHeader(value="Authorization",required=false) String authorization,@Valid @RequestBody CameraInput input) {
        Account owner=auth.require(authorization);
        validateHls(input.streamUrl());
        Camera camera=new Camera();
        camera.owner=owner; camera.name=input.name().trim(); camera.kind=input.kind();
        camera.description=input.description()==null ? "" : input.description().trim();
        camera.region=input.region().trim(); camera.streamUrl=input.streamUrl().trim();
        camera.publicListing=input.publicListing();
        String deviceToken=auth.secret(); camera.ingestTokenHash=auth.sha(deviceToken);
        cameras.save(camera);
        // The device token appears exactly once. Store it locally on the camera / edge gateway.
        return Map.of("camera",camera(camera),"deviceToken",deviceToken);
    }
    @PostMapping("/cameras/{id}/rotate-device-token")
    Object rotate(@PathVariable UUID id,@RequestHeader(value="Authorization",required=false) String authorization) {
        Camera camera=owned(id,authorization);
        String token=auth.secret(); camera.ingestTokenHash=auth.sha(token); cameras.save(camera);
        return Map.of("deviceToken",token,"cameraId",id);
    }
    @GetMapping("/cameras/{id}/sightings")
    Object history(@PathVariable UUID id,@RequestHeader(value="Authorization",required=false) String authorization) {
        viewable(id,authorization);
        return sightings.findTop100ByCameraIdOrderByObservedAtDesc(id).stream().map(this::sighting).toList();
    }
    @GetMapping("/cameras/{id}/statistics")
    Object statistics(@PathVariable UUID id,@RequestHeader(value="Authorization",required=false) String authorization) {
        viewable(id,authorization);
        List<Sighting> found=sightings.findByCameraIdAndObservedAtAfter(id,Instant.now().minus(30,ChronoUnit.DAYS));
        Map<String,Long> bySpecies=found.stream().filter(s->s.eventType.equals("ARRIVAL") && !s.species.equals("Unidentified"))
            .collect(Collectors.groupingBy(s->s.species,Collectors.counting()));
        Map<String,Long> byDay=found.stream().filter(s->s.eventType.equals("ARRIVAL"))
            .collect(Collectors.groupingBy(s->s.observedAt.toString().substring(0,10),Collectors.counting()));
        return Map.of("windowDays",30,"totalEstimatedVisits",found.stream().filter(s->s.eventType.equals("ARRIVAL")).count(),
            "speciesCount",bySpecies.size(),"bySpecies",bySpecies,"byDay",byDay,
            "rfidReads",found.stream().filter(s->s.source.equals("RFID")).count(),
            "departures",found.stream().filter(s->s.eventType.equals("DEPARTURE")).count());
    }
    @Transactional
    @PostMapping("/cameras/{id}/detections")
    Object detection(@PathVariable UUID id,@RequestHeader(value="X-Device-Token",required=false) String token,
                     @Valid @RequestBody Detection input) {
        Camera camera=device(id,token);
        String species=input.species().trim().replaceAll("\\s+"," ");
        // Retry-safe delivery (the edge SQLite outbox resends the same eventId on failure).
        // A track ID distinguishes two simultaneous birds of the same species, but is not
        // a persistent animal identity and must not appear in public responses.
        if (input.eventId()!=null && sightings.findByDeviceEventId(input.eventId()).isPresent()) {
            return Map.of("deduplicated",true);
        }
        String trackId=input.trackId()==null || input.trackId().isBlank() ? null : input.trackId().trim();
        if (trackId!=null) {
            if (sightings.findFirstByCameraIdAndTrackIdAndEventTypeOrderByObservedAtDesc(
                    id,trackId,input.eventType()).isPresent()) return Map.of("deduplicated",true);
        } else if (sightings.findFirstByCameraIdAndSpeciesIgnoreCaseAndEventTypeAndObservedAtAfterOrderByObservedAtDesc(
                id,species,input.eventType(),Instant.now().minus(30,ChronoUnit.SECONDS)).isPresent()) {
            // Legacy ONNX single-bird clients do not provide track IDs.
            return Map.of("deduplicated",true);
        }
        camera.lastDeviceSeenAt=Instant.now(); cameras.save(camera);
        Sighting s=new Sighting(); s.camera=camera; s.species=species; s.eventType=input.eventType();
        s.source="VISION"; s.confidence=input.confidence();
        s.trackId=trackId; s.deviceEventId=input.eventId();
        sightings.save(s); dispatch(camera,s); return sighting(s);
    }
    @Transactional
    @PostMapping("/cameras/{id}/rfid-scans")
    Object rfid(@PathVariable UUID id,@RequestHeader(value="X-Device-Token",required=false) String token,
                @Valid @RequestBody RfidScan input) {
        Camera camera=device(id,token);
        RfidTag registered=tags.findByOwnerIdAndTagUid(camera.owner.id,input.tagUid().trim()).orElse(null);
        camera.lastDeviceSeenAt=Instant.now(); cameras.save(camera);
        Sighting s=new Sighting(); s.camera=camera; s.species=registered==null ? "Unidentified" : registered.species;
        s.eventType=input.eventType(); s.source="RFID"; s.confidence=1.0;
        s.tagUid=input.tagUid().trim(); sightings.save(s); dispatch(camera,s);
        return sighting(s);
    }
    @PostMapping("/me/tags") @ResponseStatus(HttpStatus.CREATED)
    Object addTag(@RequestHeader(value="Authorization",required=false) String authorization,
                  @Valid @RequestBody RfidInput input) {
        Account owner=auth.require(authorization);
        if (tags.findByOwnerIdAndTagUid(owner.id,input.tagUid().trim()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Tag already registered");
        RfidTag t=new RfidTag(); t.owner=owner; t.tagUid=input.tagUid().trim();
        t.nickname=input.nickname().trim(); t.species=input.species().trim();
        tags.save(t); return tag(t);
    }
    @GetMapping("/me/tags")
    Object getTags(@RequestHeader(value="Authorization",required=false) String authorization) {
        return tags.findByOwnerIdOrderByNicknameAsc(auth.require(authorization).id).stream().map(this::tag).toList();
    }
    @GetMapping("/me/rfid-events")
    Object rfidEvents(@RequestHeader(value="Authorization",required=false) String authorization) {
        Account viewer=auth.require(authorization);
        return sightings.findTop100ByCameraOwnerIdAndSourceOrderByObservedAtDesc(viewer.id,"RFID").stream().map(s -> {
            RfidTag tag=tags.findByOwnerIdAndTagUid(viewer.id,s.tagUid).orElse(null);
            return Map.of("cameraId",s.camera.id,"camera",s.camera.name,"tagUid",s.tagUid,
                "nickname",tag==null ? "Unknown tag" : tag.nickname,"species",s.species,
                "eventType",s.eventType,"observedAt",s.observedAt);
        }).toList();
    }
    @PostMapping("/cameras/{id}/alert-rules") @ResponseStatus(HttpStatus.CREATED)
    Object addAlert(@PathVariable UUID id,@RequestHeader(value="Authorization",required=false) String authorization,
                    @Valid @RequestBody AlertInput input) {
        Account subscriber=auth.require(authorization);
        Camera camera=viewable(id,authorization);
        AlertRule rule=new AlertRule(); rule.owner=subscriber; rule.camera=camera;
        rule.species=input.species().trim(); rule.cooldownMinutes=input.cooldownMinutes();
        alertRules.save(rule);
        return Map.of("id",rule.id,"cameraId",id,"species",rule.species,"cooldownMinutes",rule.cooldownMinutes);
    }
    @GetMapping("/me/alert-rules")
    Object alertRules(@RequestHeader(value="Authorization",required=false) String authorization) {
        return alertRules.findByOwnerId(auth.require(authorization).id).stream().map(rule->Map.of(
            "id",rule.id,"cameraId",rule.camera.id,"camera",rule.camera.name,"species",rule.species,
            "cooldownMinutes",rule.cooldownMinutes,"enabled",rule.enabled)).toList();
    }
    @GetMapping("/me/notifications")
    Object inbox(@RequestHeader(value="Authorization",required=false) String authorization) {
        return notices.findTop50ByOwnerIdOrderByCreatedAtDesc(auth.require(authorization).id).stream().map(note->Map.of(
            "id",note.id,"cameraId",note.camera.id,"message",note.message,"createdAt",note.createdAt)).toList();
    }

    private Camera device(UUID id,String token) {
        Camera camera=cameras.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Camera missing"));
        auth.checkDevice(camera,token); return camera;
    }
    private Camera viewable(UUID id,String authorization) {
        Camera c=cameras.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Camera missing"));
        if (c.publicListing) return c;
        Account viewer=auth.maybe(authorization);
        if (viewer==null || !viewer.id.equals(c.owner.id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Camera missing");
        return c;
    }
    private Camera owned(UUID id,String authorization) {
        Camera c=cameras.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Camera missing"));
        auth.own(c,auth.require(authorization)); return c;
    }
    private void validateHls(String url) {
        try {
            URI uri=URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getFragment()!=null || uri.getRawPath()==null || !uri.getRawPath().endsWith(".m3u8"))
                throw new IllegalArgumentException();
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a public HTTPS HLS .m3u8 playback URL"); }
    }
    private void dispatch(Camera camera, Sighting sighting) {
        if (!"ARRIVAL".equals(sighting.eventType)) return;
        Instant now=Instant.now();
        for (AlertRule rule:alertRules.findByCameraIdAndEnabledTrue(camera.id)) {
            if (!rule.species.equalsIgnoreCase("ANY") && !rule.species.equalsIgnoreCase(sighting.species)) continue;
            if (rule.lastSentAt!=null && rule.lastSentAt.isAfter(now.minus(rule.cooldownMinutes,ChronoUnit.MINUTES))) continue;
            BirdNotification notification=new BirdNotification(); notification.owner=rule.owner; notification.camera=camera;
            notification.message=(sighting.species.equals("Unidentified") ? "RFID visitor" : sighting.species) + " at " + camera.name;
            notices.save(notification); rule.lastSentAt=now; alertRules.save(rule);
        }
    }
    private Object camera(Camera c) {
        return Map.of("id",c.id,"name",c.name,"kind",c.kind,"region",c.region,
            "description",c.description,"streamUrl",c.streamUrl,"publicListing",c.publicListing,
            "online",c.lastDeviceSeenAt!=null && c.lastDeviceSeenAt.isAfter(Instant.now().minus(120,ChronoUnit.SECONDS)),"owner",c.owner.displayName,"createdAt",c.createdAt);
    }
    private Object sighting(Sighting s) {
        // Raw RFID UID is owner-private. Public sightings expose only derived species.
        return Map.of("id",s.id,"cameraId",s.camera.id,"species",s.species,
            "eventType",s.eventType,"source",s.source,"confidence",s.confidence,"observedAt",s.observedAt);
    }
    private Object tag(RfidTag t) { return Map.of("id",t.id,"tagUid",t.tagUid,"nickname",t.nickname,"species",t.species); }
}
