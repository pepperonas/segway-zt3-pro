"""Host-Tests fuer can_analysis und die Analyse-Modi von can_parser.

Nur Standardbibliothek:
    cd can-bus/parser && python3 -m unittest -v

(c) 2026 Martin Pfeffer | celox.io
"""

from __future__ import annotations

import io
import random
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import can_analysis as ca
import can_parser as cp
from can_parser import Frame

DATA = Path(__file__).resolve().parents[2] / "can-data"


def frames_from(datas, can_id=0x100, period=0.02, t0=0.0):
    return [Frame(t0 + i * period, can_id, len(d), list(d), None, True) for i, d in enumerate(datas)]


class Crc8Presets(unittest.TestCase):
    # Pruefwerte ueber b"123456789" laut reveng-CRC-Katalog
    CHECK = {
        "crc8": 0xF4,
        "crc8-itu": 0xA1,
        "crc8-sae-j1850": 0x4B,
        "crc8-gsm-a": 0x37,
        "crc8-autosar": 0xDF,
        "crc8-maxim": 0xA1,
        "crc8-cdma2000": 0xDA,
    }

    def test_catalogue_check_values(self):
        self.assertEqual(set(self.CHECK), set(ca.CRC8_PRESETS))
        for name, expected in self.CHECK.items():
            with self.subTest(name=name):
                self.assertEqual(ca.crc8(b"123456789", **ca.CRC8_PRESETS[name]), expected)


class PeriodStatsTest(unittest.TestCase):
    def test_regular_period(self):
        ps = ca.period_stats([i * 0.02 for i in range(100)])
        self.assertAlmostEqual(ps.mean_ms, 20.0, places=6)
        self.assertEqual(ps.gaps, 0)
        self.assertAlmostEqual(ps.stdev_ms, 0.0, places=6)

    def test_missing_frame_counts_as_gap(self):
        times = [i * 0.02 for i in range(50)]
        del times[20]
        ps = ca.period_stats(times)
        self.assertEqual(ps.gaps, 1)
        self.assertAlmostEqual(ps.max_ms, 40.0, places=6)

    def test_too_few(self):
        self.assertIsNone(ca.period_stats([0.0, 0.02]))


class ValidFramesTest(unittest.TestCase):
    def test_drops_nack_and_short(self):
        fs = [
            Frame(0.0, 0x100, 8, [0] * 8, None, True),
            Frame(0.1, 0x100, 8, [0] * 8, None, False),   # kein ACK
            Frame(0.2, 0x100, 8, [0] * 6, None, True),    # zu kurz
            Frame(0.3, 0x100, 8, [0] * 8, None, None),    # Format ohne ACK
            Frame(0.4, 0x200, 8, [0] * 8, None, True),    # andere ID
        ]
        self.assertEqual([f.time_s for f in ca.valid_frames(fs, 0x100)], [0.0, 0.3])


class CounterTest(unittest.TestCase):
    def test_full_byte_counter_with_wrap(self):
        datas = [[0x11, (250 + i) % 256, 0x40] for i in range(40)]
        cs = ca.find_counters(datas)
        self.assertEqual(len(cs), 1)
        c = cs[0]
        self.assertEqual((c.index, c.field, c.step), (1, "byte", 1))
        self.assertEqual(c.wraps, 1)

    def test_nibble_counter_step_two(self):
        # High-Nibble zaehlt in 2er-Schritten, Low-Nibble ist Nutzdaten
        rnd = random.Random(1)
        datas = [[((2 * i) % 16) << 4 | rnd.randrange(16)] for i in range(40)]
        cs = ca.find_counters(datas)
        self.assertEqual([(c.index, c.field, c.step) for c in cs], [(0, "hi", 2)])

    def test_tolerates_single_dropped_frame(self):
        vals = list(range(40))
        del vals[10]
        cs = ca.find_counters([[v] for v in vals])
        self.assertEqual(len(cs), 1)
        self.assertGreaterEqual(cs[0].hit_rate, 0.9)

    def test_constant_and_random_are_not_counters(self):
        rnd = random.Random(2)
        datas = [[0x40, rnd.randrange(256)] for _ in range(60)]
        self.assertEqual(ca.find_counters(datas), [])

    def test_throttle_ramp_is_not_counter(self):
        # Rampe mit Haltephasen: Inkrement 0 dominiert
        ramp = [min(200, i // 3 * 8) for i in range(90)]
        self.assertEqual(ca.find_counters([[v] for v in ramp]), [])


class ChecksumTest(unittest.TestCase):
    def _payloads(self, n=60, seed=3):
        rnd = random.Random(seed)
        return [[rnd.randrange(256) for _ in range(7)] for _ in range(n)]

    def _algos(self, cands, target):
        return {(c.algo, c.with_id) for c in cands if c.target == target}

    def test_xor_over_first_seven(self):
        datas = [p + [ca._xor(p)] for p in self._payloads()]
        self.assertIn(("xor", False), self._algos(ca.find_checksums(datas), 7))

    def test_sum_inverted(self):
        datas = [p + [(~sum(p)) & 0xFF] for p in self._payloads()]
        self.assertIn(("sum8-inv", False), self._algos(ca.find_checksums(datas), 7))

    def test_crc_j1850_with_can_id(self):
        cid = 0x100
        pre = [cid & 0xFF, cid >> 8]
        datas = [p + [ca.crc8(pre + p, **ca.CRC8_PRESETS["crc8-sae-j1850"])] for p in self._payloads()]
        algos = self._algos(ca.find_checksums(datas, can_id=cid), 7)
        self.assertIn(("crc8-sae-j1850", True), algos)
        self.assertNotIn(("crc8-sae-j1850", False), algos)

    def test_checksum_in_byte_zero_over_rest(self):
        datas = [[sum(p) & 0xFF] + p for p in self._payloads()]
        self.assertIn(("sum8", False), self._algos(ca.find_checksums(datas), 0))

    def test_random_data_yields_nothing(self):
        rnd = random.Random(4)
        datas = [[rnd.randrange(256) for _ in range(8)] for _ in range(200)]
        self.assertEqual(ca.find_checksums(datas, can_id=0x100), [])

    def test_constant_target_is_never_a_checksum(self):
        # Genau die 0x100-Situation: Byte 0 variiert, Byte 7 konstant
        datas = [[v, 0, 0, 0x40, 0x23, 0x4F, 0x64, 0x32] for v in range(0, 201, 8)]
        self.assertEqual(ca.find_checksums(datas, can_id=0x100), [])


    def test_near_constant_frames_do_not_fake_a_checksum(self):
        # Ein einziger Ausreisser macht die Eingaenge "variabel"; das konstante
        # Ziel 0x32 ist zufaellig 0x40 ^ 0x72. Ohne Sperre gegen konstante
        # Ziele waere das ein 99-%-Treffer fuer xor, obwohl nichts bewiesen ist.
        datas = [[0x40, 0x72, 0x32] for _ in range(99)] + [[0x41, 0x72, 0x32]]
        self.assertEqual(ca.find_checksums(datas), [])


class PearsonAndBytesTest(unittest.TestCase):
    def test_pearson(self):
        self.assertAlmostEqual(ca.pearson([1, 2, 3], [2, 4, 6]), 1.0)
        self.assertAlmostEqual(ca.pearson([1, 2, 3], [3, 2, 1]), -1.0)
        self.assertIsNone(ca.pearson([1, 1, 1], [1, 2, 3]))

    def test_byte_stats(self):
        bs = ca.byte_stats([[0, 7], [100, 7], [200, 7]])
        self.assertEqual((bs[0].min, bs[0].max, bs[0].distinct), (0, 200, 3))
        self.assertIsNone(bs[0].corr_ref)
        self.assertIsNone(bs[1].corr_ref)


class SignalSpecTest(unittest.TestCase):
    def test_parse_and_decode(self):
        s = ca.SignalSpec.parse("0x211:6:u16le")
        self.assertEqual((s.can_id, s.offset, s.kind), (0x211, 6, "u16le"))
        self.assertEqual(s.decode([0, 0, 0, 0, 0, 0, 0x95, 0x01]), 405)
        self.assertEqual(ca.SignalSpec.parse("0x420:2:s16le").decode([0, 0, 0x8C, 0xFF]), -116)
        self.assertEqual(ca.SignalSpec.parse("0x100:0").decode([0xC8]), 200)
        self.assertIsNone(s.decode([0] * 7))

    def test_bad_spec(self):
        with self.assertRaises(ValueError):
            ca.SignalSpec.parse("0x100")
        with self.assertRaises(ValueError):
            ca.SignalSpec.parse("0x100:0:float")


class StepResponseTest(unittest.TestCase):
    def test_latency_and_resolution(self):
        # Eingang faellt bei t=1.0, Antwort (10 Hz) faellt ab dem Sample bei 1.2
        resp = [(i / 10, 400 if i / 10 < 1.15 else 400 - (i - 11) * 50) for i in range(30)]
        e = ca.Edge(1.0, 200, 0)
        r = ca.step_response(e, resp, "sig")
        self.assertAlmostEqual(r.latency_ms, 200.0, places=6)
        self.assertAlmostEqual(r.resolution_ms, 100.0, places=6)

    def test_no_reaction(self):
        resp = [(i / 10, 400) for i in range(30)]
        r = ca.step_response(ca.Edge(1.0, 200, 0), resp, "sig")
        self.assertIsNone(r.latency_ms)

    def test_explicit_direction(self):
        # Strom steigt (Rekuperation) waehrend das Gas faellt
        resp = [(0.0, -100), (0.5, -100), (1.5, 300)]
        r = ca.step_response(ca.Edge(1.0, 200, 0), resp, "i", direction=+1)
        self.assertAlmostEqual(r.latency_ms, 500.0, places=6)

    def test_find_edges(self):
        s = [(0.0, 0), (0.02, 10), (0.04, 200), (0.06, 190), (0.08, 0)]
        es = ca.find_edges(s, 50)
        self.assertEqual([(e.before, e.after, e.falling) for e in es], [(10, 200, False), (190, 0, True)])


class RealCapture0x100(unittest.TestCase):
    """Regression gegen die eingecheckten Captures: 0x100 ist 50 Hz, DLC 8,
    ohne Counter und ohne inhaltsabhaengige Checksumme."""

    @classmethod
    def setUpClass(cls):
        cls.paths = sorted(DATA.glob("*.csv"))
        if not cls.paths:
            raise unittest.SkipTest("keine Captures in can-data/")
        cls.frames = {p.name: ca.valid_frames(cp.parse_kingst_csv(p), 0x100) for p in cls.paths}

    def test_period_and_dlc(self):
        for name, fs in self.frames.items():
            with self.subTest(capture=name):
                ps = ca.period_stats([f.time_s for f in fs])
                self.assertAlmostEqual(ps.median_ms, 20.0, delta=0.2)
                self.assertTrue(all(f.dlc == 8 for f in fs))

    def test_no_counter_no_checksum(self):
        all_datas = [f.data for fs in self.frames.values() for f in fs]
        self.assertEqual(ca.find_checksums(all_datas, can_id=0x100), [])
        for name, fs in self.frames.items():
            with self.subTest(capture=name):
                self.assertEqual(ca.find_counters([f.data for f in fs]), [])

    def test_analyze_mode_runs(self):
        caps = [(n, cp.parse_kingst_csv(DATA / n)) for n in ("throttle.csv", "mode-switch.csv")]
        buf = io.StringIO()
        with redirect_stdout(buf):
            cp.analyze(caps, 0x100)
        out = buf.getvalue()
        self.assertIn("DLC: [8] (konstant)", out)
        self.assertIn("Rolling-Counter-Kandidaten", out)

    def test_wheel_speed_plateau_matches_limit(self):
        # 0x211[6..7] u16le: Plateau ~10 x Limit in km/h (Hypothese 0,1 km/h/LSB)
        spd = ca.SignalSpec.parse("0x211:6:u16le")
        lim = ca.SignalSpec.parse("0x342:6")
        for name in ("throttle.csv", "driving-40-beep.csv"):
            with self.subTest(capture=name):
                fr = cp.parse_kingst_csv(DATA / name)
                top = max(v for _, v in ca.extract(fr, spd))
                limit = max(v for _, v in ca.extract(fr, lim))
                self.assertLess(abs(top / (limit * 10) - 1.0), 0.05)


if __name__ == "__main__":
    unittest.main()
