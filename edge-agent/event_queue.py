"""Durable, bounded SQLite event outbox. Network failures don't lose sightings."""
import json
import os
import sqlite3
import time
from pathlib import Path
from uuid import uuid4


class EventQueue:
    def __init__(self, file: str, sender, max_events: int = 10000):
        location = Path(file)
        location.parent.mkdir(parents=True, exist_ok=True)
        if os.name == 'posix' and not location.exists():
            location.touch(mode=0o600)
        self.db = sqlite3.connect(location)
        self.db.execute("CREATE TABLE IF NOT EXISTS pending (id TEXT PRIMARY KEY, path TEXT NOT NULL, payload TEXT NOT NULL, created REAL NOT NULL)")
        self.db.commit()
        self.sender = sender
        self.max_events = max_events

    def add(self, path: str, payload: dict):
        event = dict(payload)
        event.setdefault("eventId", str(uuid4()))
        count, = self.db.execute("SELECT count(*) FROM pending").fetchone()
        if count >= self.max_events:
            raise RuntimeError("Offline event queue full; check connectivity and storage")
        self.db.execute("INSERT OR IGNORE INTO pending VALUES (?,?,?,?)",
                        (event["eventId"], path, json.dumps(event), time.time()))
        self.db.commit()

    def flush(self, batch_size: int = 25):
        sent = 0
        for event_id, path, raw in self.db.execute("SELECT id,path,payload FROM pending ORDER BY created,id LIMIT ?", (batch_size,)).fetchall():
            # Raises on failure, preserving this and all later events for retry.
            self.sender(path, json.loads(raw))
            self.db.execute("DELETE FROM pending WHERE id=?", (event_id,))
            self.db.commit()
            sent += 1
        return sent

    def pending_count(self):
        return self.db.execute("SELECT count(*) FROM pending").fetchone()[0]

    def close(self):
        self.db.close()
