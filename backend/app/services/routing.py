"""OSRM client.

Phase 4-lite uses OSRM's `/route` endpoint to fetch a driving polyline +
duration between two coordinates. The function is intentionally narrow:
it returns just what /v1/rides/search needs to populate polyline_geom
and to compute detour cost for candidate matches.

A failure here is non-fatal — ride creation continues with a NULL
polyline_geom, and search degrades gracefully to the endpoint-radius
behaviour that existed before Phase 4.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass

import httpx

from app.core.config import settings

log = logging.getLogger(__name__)


@dataclass
class Route:
    duration_s: float
    distance_m: float
    coords: list[tuple[float, float]]  # [(lng, lat), ...] in OSRM order

    def as_wkt_linestring(self) -> str:
        """Return a SRID-tagged WKT LINESTRING suitable for ST_GeogFromText."""
        pts = ", ".join(f"{lng} {lat}" for lng, lat in self.coords)
        return f"SRID=4326;LINESTRING({pts})"


async def fetch_route(
    origin_lat: float, origin_lng: float, dest_lat: float, dest_lng: float
) -> Route | None:
    """Fetch the fastest driving route from OSRM. Returns None on any
    transport / parse failure; caller treats that as 'no polyline this
    time' and the ride is still created."""
    coords = f"{origin_lng},{origin_lat};{dest_lng},{dest_lat}"
    url = (
        f"{settings.osrm_base_url.rstrip('/')}/route/v1/driving/{coords}"
        "?overview=full&geometries=geojson&alternatives=false&steps=false"
    )
    try:
        async with httpx.AsyncClient(timeout=settings.osrm_timeout_seconds) as c:
            resp = await c.get(url)
            resp.raise_for_status()
            payload = resp.json()
    except (httpx.HTTPError, ValueError) as exc:
        log.warning("OSRM route fetch failed: %s", exc)
        return None

    routes = payload.get("routes") or []
    if not routes:
        return None
    r = routes[0]
    geom = r.get("geometry") or {}
    pts: list[tuple[float, float]] = []
    for c in geom.get("coordinates", []):
        # GeoJSON: [lng, lat]
        if isinstance(c, list) and len(c) >= 2:
            pts.append((float(c[0]), float(c[1])))
    if len(pts) < 2:
        return None
    return Route(
        duration_s=float(r.get("duration", 0.0)),
        distance_m=float(r.get("distance", 0.0)),
        coords=pts,
    )
