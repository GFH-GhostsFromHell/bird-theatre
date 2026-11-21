package ca.gfh.birdtheatre;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="community_accounts", uniqueConstraints=@UniqueConstraint(columnNames="handle"))
class Account {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @Column(nullable=false, length=32) String handle;
    @Column(nullable=false, length=80) String displayName;
    @Column(nullable=false) String passwordHash;
    @Column(nullable=false) Instant joinedAt=Instant.now();
    protected Account() {}
    Account(String handle, String displayName, String passwordHash) {
        this.handle=handle; this.displayName=displayName; this.passwordHash=passwordHash;
    }
}

@Entity @Table(name="account_sessions")
class AccountSession {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @ManyToOne(optional=false, fetch=FetchType.EAGER) Account owner;
    @Column(nullable=false, unique=true, length=64) String tokenHash;
    @Column(nullable=false) Instant expiresAt;
    protected AccountSession() {}
    AccountSession(Account owner,String tokenHash, Instant expiresAt) {
        this.owner=owner; this.tokenHash=tokenHash; this.expiresAt=expiresAt;
    }
}

@Entity @Table(name="cameras")
class Camera {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) Account owner;
    @Column(nullable=false,length=90) String name;
    @Column(nullable=false,length=15) String kind;
    @Column(nullable=false,length=80) String region;
    @Column(nullable=false,length=300) String description;
    @Column(nullable=false,length=1000) String streamUrl;
    @Column(nullable=false) boolean publicListing=true;
    Instant lastDeviceSeenAt;
    @Column(nullable=false,length=64) String ingestTokenHash;
    @Column(nullable=false) Instant createdAt=Instant.now();
    protected Camera() {}
}

@Entity @Table(name="bird_sightings", indexes={@Index(columnList="camera_id,observed_at")})
class Sighting {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) Camera camera;
    @Column(nullable=false,length=120) String species;
    @Column(nullable=false,length=14) String eventType;
    @Column(nullable=false,length=12) String source;
    @Column(nullable=false) double confidence;
    @Column(length=100) String tagUid;
    // Optional edge-agent idempotency key: prevents a retried delivery from counting twice.
    @Column(unique=true) UUID deviceEventId;
    // Random, ephemeral within one camera session. Not a wildlife biometric identifier.
    @Column(length=80) String trackId;
    @Column(nullable=false) Instant observedAt=Instant.now();
    protected Sighting() {}
}

@Entity @Table(name="rfid_tags", uniqueConstraints=@UniqueConstraint(columnNames={"owner_id","tag_uid"}))
class RfidTag {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) Account owner;
    @Column(name="tag_uid",nullable=false,length=100) String tagUid;
    @Column(nullable=false,length=80) String nickname;
    @Column(nullable=false,length=120) String species;
    protected RfidTag() {}
}

@Entity @Table(name="alert_rules")
class AlertRule {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) Account owner;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) Camera camera;
    @Column(nullable=false,length=120) String species;
    @Column(nullable=false) int cooldownMinutes=15;
    @Column(nullable=false) boolean enabled=true;
    Instant lastSentAt;
    protected AlertRule() {}
}

@Entity @Table(name="notifications")
class BirdNotification {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) Account owner;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) Camera camera;
    @Column(nullable=false,length=255) String message;
    @Column(nullable=false) Instant createdAt=Instant.now();
    protected BirdNotification() {}
}
