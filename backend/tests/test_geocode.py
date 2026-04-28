"""Geocode service contract test.

Mocks Nominatim with httpx.MockTransport and a fakeredis-style stand-in via
monkeypatching redis.asyncio.from_url. Verifies cache-hit on second call.
"""
from __future__ import annotations

import json
from typing import Any

import httpx
import pytest

from app.services import geocode as geocode_module


class _FakeRedis:
    def __init__(self) -> None:
        self.kv: dict[str, str] = {}

    async def get(self, key: str) -> str | None:
        return self.kv.get(key)

    async def set(self, key: str, value: str, ex: int | None = None, **_: Any) -> bool:
        if "nx" in _:
            if key in self.kv:
                return False
        self.kv[key] = value
        return True

    async def pttl(self, _key: str) -> int:
        return 0

    async def aclose(self) -> None:
        pass


@pytest.mark.asyncio
async def test_geocode_caches_second_call(monkeypatch: pytest.MonkeyPatch) -> None:
    fake_redis = _FakeRedis()
    monkeypatch.setattr(geocode_module.redis, "from_url", lambda *_a, **_k: fake_redis)

    upstream_calls = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        upstream_calls["n"] += 1
        body = [{"display_name": "Bangalore, KA, India", "lat": "12.97", "lon": "77.59"}]
        return httpx.Response(200, json=body)

    transport = httpx.MockTransport(handler)
    real_client = httpx.AsyncClient

    class _PatchedClient(real_client):  # type: ignore[misc]
        def __init__(self, *args: Any, **kwargs: Any) -> None:
            kwargs["transport"] = transport
            super().__init__(*args, **kwargs)

    monkeypatch.setattr(geocode_module.httpx, "AsyncClient", _PatchedClient)

    first = await geocode_module.geocode("Bangalore")
    second = await geocode_module.geocode("Bangalore")

    assert len(first) == 1 and first[0].label.startswith("Bangalore")
    assert json.dumps([h.model_dump() for h in first]) == json.dumps(
        [h.model_dump() for h in second]
    )
    assert upstream_calls["n"] == 1, "second call must be served from cache"
