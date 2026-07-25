import json
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace


ROOT_DIR = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT_DIR / "shared" / "src" / "commonMain" / "python"))

from fuo_mobile.bridge import (  # noqa: E402
    FuoMobileBridge,
    _NeteaseTimeoutHttp,
    read_model_page,
)


class _RecordingHttp:
    def __init__(self):
        self.calls = []

    def get(self, *args, **kwargs):
        self.calls.append(("GET", args, kwargs))
        return "get"

    def post(self, *args, **kwargs):
        self.calls.append(("POST", args, kwargs))
        return "post"


class _Reader:
    def __init__(self, models):
        self.models = models
        self.count = len(models)
        self.ranges = []

    def read_range(self, start, end):
        self.ranges.append((start, end))
        return self.models[start:end]


class _Library:
    def __init__(self, provider):
        self.provider = provider

    def get(self, provider_id):
        return self.provider if provider_id == self.provider.identifier else None


class _Provider:
    identifier = "netease"
    name = "网易云音乐"

    def __init__(self):
        self.reader_calls = 0
        self.playlist_get_calls = 0
        self.playlist = SimpleNamespace(
            source="netease",
            identifier="playlist1",
            name="Playlist",
            count=2,
        )
        self.songs = [
            SimpleNamespace(
                source="netease",
                identifier="song1",
                title="First",
                artists=[],
                album=None,
            ),
            SimpleNamespace(
                source="netease",
                identifier="song2",
                title="Second",
                artists=[],
                album=None,
            ),
        ]
        self.reader = _Reader(self.songs)

    def playlist_get(self, identifier):
        self.playlist_get_calls += 1
        return self.playlist

    def playlist_create_songs_rd(self, playlist):
        self.reader_calls += 1
        return self.reader


class PlaylistPagesTest(unittest.TestCase):
    def bridge(self, provider):
        bridge = FuoMobileBridge.__new__(FuoMobileBridge)
        bridge.app = SimpleNamespace(library=_Library(provider))
        bridge._tracks = {}
        bridge._playlists = {"playlist:netease:playlist1": provider.playlist}
        bridge._playlist_readers = {}
        bridge._get_provider = lambda provider_id: provider
        return bridge

    def test_reader_with_known_count_does_not_fetch_sentinel_item(self):
        reader = _Reader(list(range(100)))

        page = read_model_page(reader, offset=0, limit=50)

        self.assertEqual([(0, 50)], reader.ranges)
        self.assertEqual(50, len(page["items"]))
        self.assertTrue(page["has_more"])

    def test_playlist_detail_reuses_reader_and_playlist_metadata(self):
        provider = _Provider()
        bridge = self.bridge(provider)

        first = json.loads(bridge.playlist_detail("playlist:netease:playlist1", limit=1))
        second = json.loads(bridge.playlist_detail("playlist:netease:playlist1", offset=1, limit=1))

        self.assertEqual(["First"], [track["title"] for track in first["tracks"]])
        self.assertEqual(["Second"], [track["title"] for track in second["tracks"]])
        self.assertEqual(1, provider.reader_calls)
        self.assertEqual(0, provider.playlist_get_calls)
        self.assertEqual([(0, 1), (1, 2)], provider.reader.ranges)

    def test_netease_http_overrides_short_provider_timeout(self):
        client = _RecordingHttp()
        http = _NeteaseTimeoutHttp(client, timeout=(10, 15))

        http.get("https://example.test", timeout=2)
        http.post("https://example.test", timeout=2)

        self.assertEqual((10, 15), client.calls[0][2]["timeout"])
        self.assertEqual((10, 15), client.calls[1][2]["timeout"])


if __name__ == "__main__":
    unittest.main()
