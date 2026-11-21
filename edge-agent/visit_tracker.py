"""Pure-Python, testable multi-bird visit state machine.

This approximates visits using bounding boxes, NOT biometric identification.
Tracking IDs are camera-session-specific; similar birds can swap identities.
"""
from dataclasses import dataclass, field
from typing import Optional
from uuid import uuid4


@dataclass(frozen=True)
class Observation:
    box: tuple[float, float, float, float]
    species: str
    confidence: float


@dataclass
class Track:
    id: str
    box: tuple[float, float, float, float]
    last_seen: float
    hits: int = 0
    votes: dict[str, float] = field(default_factory=dict)
    issued_species: Optional[str] = None
    issued_confidence: float = 0.0

    def observe(self, item: Observation, now: float):
        self.box = item.box
        self.last_seen = now
        self.hits += 1
        self.votes[item.species] = self.votes.get(item.species, 0.0) + max(0.1, item.confidence)

    def consensus(self):
        species = max(self.votes, key=self.votes.get)
        return species, min(1.0, self.votes[species] / max(1, self.hits))


def _iou(a, b):
    xa = max(a[0], b[0]); ya = max(a[1], b[1])
    xb = min(a[2], b[2]); yb = min(a[3], b[3])
    intersection = max(0.0, xb - xa) * max(0.0, yb - ya)
    aa = max(0.0, a[2] - a[0]) * max(0.0, a[3] - a[1])
    bb = max(0.0, b[2] - b[0]) * max(0.0, b[3] - b[1])
    return intersection / max(1e-9, aa + bb - intersection)


def _match_score(a, b):
    overlap = _iou(a, b)
    acx, acy = (a[0] + a[2]) / 2, (a[1] + a[3]) / 2
    bcx, bcy = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
    size = max(1.0, a[2] - a[0], a[3] - a[1], b[2] - b[0], b[3] - b[1])
    relative_distance = ((acx - bcx) ** 2 + (acy - bcy) ** 2) ** 0.5 / size
    # Require meaningful overlap or centres sufficiently close for a moving bird.
    if overlap < 0.15 and relative_distance > 0.55:
        return -1.0
    return overlap + max(0.0, 0.6 - relative_distance)


class VisitTracker:
    def __init__(self, min_hits: int = 2, absence_seconds: float = 10.0):
        if min_hits < 1 or absence_seconds <= 0:
            raise ValueError("min_hits must be >=1; absence_seconds must be positive")
        self.min_hits = min_hits
        self.absence_seconds = absence_seconds
        self.tracks: dict[str, Track] = {}

    @staticmethod
    def _event(track: Track, event_type: str):
        return {"eventId": str(uuid4()), "trackId": track.id,
                "species": track.issued_species,
                "eventType": event_type,
                "confidence": round(track.issued_confidence, 4)}

    def update(self, observations: list[Observation], now: float) -> list[dict]:
        events = []
        # A new bird after a long capture gap must not inherit an expired visit.
        for track_id, track in list(self.tracks.items()):
            if now - track.last_seen >= self.absence_seconds:
                if track.issued_species is not None:
                    events.append(self._event(track, "DEPARTURE"))
                del self.tracks[track_id]
        matches = sorted(
            ((_match_score(track.box, obs.box), track_id, idx)
             for track_id, track in self.tracks.items()
             for idx, obs in enumerate(observations)), reverse=True)
        used_tracks, used_observations = set(), set()
        for score, track_id, idx in matches:
            if score < 0 or track_id in used_tracks or idx in used_observations:
                continue
            self.tracks[track_id].observe(observations[idx], now)
            used_tracks.add(track_id)
            used_observations.add(idx)
        for idx, item in enumerate(observations):
            if idx not in used_observations:
                track_id = str(uuid4())
                track = Track(id=track_id, box=item.box, last_seen=now)
                track.observe(item, now)
                self.tracks[track_id] = track
                used_tracks.add(track_id)
        for track_id in used_tracks:
            track = self.tracks[track_id]
            if track.issued_species is None and track.hits >= self.min_hits:
                track.issued_species, track.issued_confidence = track.consensus()
                events.append(self._event(track, "ARRIVAL"))
        return events

    def close(self) -> list[dict]:
        events = [self._event(t, "DEPARTURE") for t in self.tracks.values() if t.issued_species]
        self.tracks.clear()
        return events
