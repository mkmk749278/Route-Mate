"""Geocoding service: Nominatim proxy with Redis cache + 1 req/sec throttle.

Nominatim's usage policy (https://operations.osmfoundation.org/policies/nominatim/)
mandates max 1 req/sec, a meaningful User-Agent, and caching. We hit it through
Redis-backed cache keyed by normalised query so identical requests across users
collapse, and we acquire a Redis-based mutex token before each upstream call to
enforce the rate limit even across multiple workers.
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import Any

import httpx
import redis.asyncio as redis
from pydantic import BaseModel

from app.core.config import settings

log = logging.getLogger(__name__)


class GeocodeHit(BaseModel):
    label: str
    lat: float
    lng: float


_RATE_LIMIT_KEY = "geocode:rate"
_CACHE_PREFIX = "geocode:q:"
_REVERSE_CACHE_PREFIX = "geocode:r:"


def _cache_key(q: str) -> str:
    return f"{_CACHE_PREFIX}{q.strip().lower()}"


def _reverse_cache_key(lat: float, lng: float) -> str:
    # Round to ~10m resolution so close requests collapse.
    return f"{_REVERSE_CACHE_PREFIX}{round(lat, 4)}:{round(lng, 4)}"


async def _acquire_rate_slot(r: redis.Redis) -> None:
    """Block until we're allowed to make the next upstream Nominatim call."""
    interval_ms = settings.geocode_min_interval_ms
    while True:
        ok = await r.set(_RATE_LIMIT_KEY, "1", nx=True, px=interval_ms)
        if ok:
            return
        ttl = await r.pttl(_RATE_LIMIT_KEY)
        await asyncio.sleep(max(ttl, 50) / 1000.0)


async def geocode(q: str, limit: int = 5) -> list[GeocodeHit]:
    q = q.strip()
    if len(q) < 3:
        return []

    r: redis.Redis = redis.from_url(settings.redis_url, decode_responses=True)
    try:
        cached = await r.get(_cache_key(q))
        if cached is not None:
            return [GeocodeHit(**h) for h in json.loads(cached)[:limit]]

        await _acquire_rate_slot(r)

        params: dict[str, Any] = {
            "q": q,
            "format": "jsonv2",
            "addressdetails": 0,
            "limit": min(max(limit, 1), 10),
        }
        headers = {"User-Agent": settings.nominatim_user_agent, "Accept-Language": "en"}
        url = f"{settings.nominatim_base_url.rstrip('/')}/search"

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(url, params=params, headers=headers)
                resp.raise_for_status()
                payload = resp.json()
        except httpx.HTTPError as exc:
            log.warning("nominatim error: %s", exc)
            return []

        hits = [
            GeocodeHit(
                label=item.get("display_name", ""),
                lat=float(item["lat"]),
                lng=float(item["lon"]),
            )
            for item in payload
            if item.get("lat") and item.get("lon")
        ]

        await r.set(
            _cache_key(q),
            json.dumps([h.model_dump() for h in hits]),
            ex=settings.geocode_cache_ttl_seconds,
        )
        return hits[:limit]
    finally:
        await r.aclose()


async def reverse(lat: float, lng: float) -> GeocodeHit | None:
    """Lookup a human label for a coordinate. Returns None on miss/error."""
    if not (-90 <= lat <= 90 and -180 <= lng <= 180):
        return None

    r: redis.Redis = redis.from_url(settings.redis_url, decode_responses=True)
    try:
        cached = await r.get(_reverse_cache_key(lat, lng))
        if cached is not None:
            data = json.loads(cached)
            return GeocodeHit(**data) if data else None

        await _acquire_rate_slot(r)

        params: dict[str, Any] = {
            "lat": lat,
            "lon": lng,
            "format": "jsonv2",
            "addressdetails": 0,
            "zoom": 18,
        }
        headers = {"User-Agent": settings.nominatim_user_agent, "Accept-Language": "en"}
        url = f"{settings.nominatim_base_url.rstrip('/')}/reverse"

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(url, params=params, headers=headers)
                resp.raise_for_status()
                payload = resp.json()
        except httpx.HTTPError as exc:
            log.warning("nominatim reverse error: %s", exc)
            return None

        label = payload.get("display_name")
        plat = payload.get("lat")
        plng = payload.get("lon")
        hit = (
            GeocodeHit(label=label, lat=float(plat), lng=float(plng))
            if label and plat and plng
            else None
        )
        await r.set(
            _reverse_cache_key(lat, lng),
            json.dumps(hit.model_dump() if hit else {}),
            ex=settings.geocode_cache_ttl_seconds,
        )
        return hit
    finally:
        await r.aclose()
