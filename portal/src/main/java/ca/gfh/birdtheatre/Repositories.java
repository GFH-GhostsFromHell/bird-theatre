package ca.gfh.birdtheatre;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepo extends JpaRepository<Account, UUID> { Optional<Account> findByHandle(String handle); }
interface SessionRepo extends JpaRepository<AccountSession, UUID> { Optional<AccountSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now); }
interface CameraRepo extends JpaRepository<Camera, UUID> {
    List<Camera> findByPublicListingTrueOrderByCreatedAtDesc();
    List<Camera> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
interface SightingRepo extends JpaRepository<Sighting, UUID> {
    List<Sighting> findTop100ByCameraIdOrderByObservedAtDesc(UUID cameraId);
    Optional<Sighting> findByDeviceEventId(UUID eventId);
    Optional<Sighting> findFirstByCameraIdAndTrackIdAndEventTypeOrderByObservedAtDesc(UUID cameraId,String trackId,String eventType);
    List<Sighting> findByCameraIdAndObservedAtAfter(UUID cameraId, Instant since);
    Optional<Sighting> findFirstByCameraIdAndSpeciesIgnoreCaseAndEventTypeAndObservedAtAfterOrderByObservedAtDesc(UUID cameraId,String species,String eventType,Instant since);
    List<Sighting> findTop100ByCameraOwnerIdAndSourceOrderByObservedAtDesc(UUID ownerId,String source);
}
interface RfidRepo extends JpaRepository<RfidTag, UUID> {
    List<RfidTag> findByOwnerIdOrderByNicknameAsc(UUID ownerId);
    Optional<RfidTag> findByOwnerIdAndTagUid(UUID ownerId,String tagUid);
}
interface AlertRepo extends JpaRepository<AlertRule,UUID> { List<AlertRule> findByCameraIdAndEnabledTrue(UUID cameraId); List<AlertRule> findByOwnerId(UUID ownerId); }
interface NotificationRepo extends JpaRepository<BirdNotification, UUID> { List<BirdNotification> findTop50ByOwnerIdOrderByCreatedAtDesc(UUID ownerId); }
