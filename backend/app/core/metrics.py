"""Prometheus metrics + a thin middleware that records the standard
golden-signals trio (request rate, error rate, latency) per route.

`/metrics` exposes the standard text format; scrape it from a Prometheus
instance (or push to Grafana Cloud Free's hosted Prometheus).
"""
from __future__ import annotations

import time

from prometheus_client import (
    CONTENT_TYPE_LATEST,
    CollectorRegistry,
    Counter,
    Histogram,
    generate_latest,
)
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

REGISTRY = CollectorRegistry()

REQ_COUNT = Counter(
    "routemate_http_requests_total",
    "HTTP requests by route and status",
    ["method", "route", "status"],
    registry=REGISTRY,
)

REQ_LATENCY = Histogram(
    "routemate_http_request_seconds",
    "HTTP request latency by route",
    ["method", "route"],
    buckets=(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0),
    registry=REGISTRY,
)


class PrometheusMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        # Use the route template (e.g. /v1/rides/{ride_id}) rather than
        # the concrete path to keep cardinality bounded.
        start = time.perf_counter()
        status = 500
        try:
            response: Response = await call_next(request)
            status = response.status_code
            return response
        finally:
            route = request.scope.get("route")
            route_path = getattr(route, "path", request.url.path)
            elapsed = time.perf_counter() - start
            REQ_COUNT.labels(request.method, route_path, str(status)).inc()
            REQ_LATENCY.labels(request.method, route_path).observe(elapsed)


def metrics_response() -> Response:
    return Response(generate_latest(REGISTRY), media_type=CONTENT_TYPE_LATEST)
