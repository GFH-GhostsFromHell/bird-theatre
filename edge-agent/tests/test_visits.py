import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from visit_tracker import Observation, VisitTracker
from event_queue import EventQueue
from recognition import load_species


class VisitTests(unittest.TestCase):
    def sample(self, x=0, species="Black-capped Chickadee"):
        return Observation((x, 0, x + 40, 40), species, 0.85)

    def test_duplicate_frames_not_duplicate_visits(self):
        tracker = VisitTracker(min_hits=2, absence_seconds=5)
        self.assertEqual([], tracker.update([self.sample()], now=0))
        arrivals = tracker.update([self.sample(3)], now=1)
        self.assertEqual(1, len(arrivals))
        self.assertEqual("ARRIVAL", arrivals[0]["eventType"])
        self.assertEqual([], tracker.update([self.sample(2)], now=2))
        departure = tracker.update([], now=8)
        self.assertEqual(1, len(departure))
        self.assertEqual("DEPARTURE", departure[0]["eventType"])
        self.assertEqual(arrivals[0]["trackId"], departure[0]["trackId"])
        self.assertEqual([], tracker.update([], now=20))

    def test_simultaneous_same_species_have_unique_tracks(self):
        tracker = VisitTracker(min_hits=2, absence_seconds=5)
        self.assertEqual([], tracker.update([self.sample(0), self.sample(200)], now=0))
        events = tracker.update([self.sample(3), self.sample(205)], now=1)
        self.assertEqual(2, len(events))
        self.assertEqual(2, len(set(x["trackId"] for x in events)))
        self.assertEqual(2, len(set(x["eventId"] for x in events)))

    def test_short_occlusion_is_not_new_visit(self):
        tracker = VisitTracker(min_hits=1, absence_seconds=5)
        self.assertEqual(1, len(tracker.update([self.sample()], now=0)))
        self.assertEqual([], tracker.update([], now=3))
        self.assertEqual([], tracker.update([self.sample()], now=4))
        self.assertEqual(1, len(tracker.update([], now=10)))

    def test_expired_track_is_not_reused_after_long_capture_pause(self):
        tracker = VisitTracker(min_hits=1, absence_seconds=5)
        first = tracker.update([self.sample()], now=0)
        self.assertEqual(1, len(first))
        next_events = tracker.update([self.sample()], now=20)
        self.assertEqual(2, len(next_events))
        self.assertEqual(["DEPARTURE", "ARRIVAL"], [x["eventType"] for x in next_events])
        self.assertNotEqual(first[0]["trackId"], next_events[1]["trackId"])

    def test_ambiguous_species_remains_unidentified(self):
        tracker = VisitTracker(min_hits=2)
        tracker.update([self.sample(0,"Unidentified")], now=0)
        events = tracker.update([self.sample(0,"Unidentified")], now=1)
        self.assertEqual("Unidentified", events[0]["species"])

    def test_outbox_retries_without_changing_event_id(self):
        with tempfile.TemporaryDirectory() as temp:
            file = str(Path(temp)/"events.db")
            seen = []
            def fail_once(path, payload):
                seen.append((path,payload))
                if len(seen)==1:
                    raise ConnectionError("offline")
            q = EventQueue(file, fail_once)
            q.add("detections", {"species":"Blue Jay","eventType":"ARRIVAL"})
            with self.assertRaises(ConnectionError):
                q.flush()
            self.assertEqual(1,q.pending_count())
            q.close()
            q = EventQueue(file, fail_once)
            self.assertEqual(1, q.flush())
            self.assertEqual(seen[0][1]["eventId"], seen[1][1]["eventId"])
            self.assertEqual(0,q.pending_count())
            q.close()

    def test_candidate_species_csv(self):
        path = str(Path(__file__).resolve().parents[1] / "species_ontario.csv")
        labels=load_species(path)
        self.assertIn(("Blue Jay", "Cyanocitta cristata"), labels)
        self.assertGreaterEqual(len(labels),30)


if __name__ == "__main__":
    unittest.main()
