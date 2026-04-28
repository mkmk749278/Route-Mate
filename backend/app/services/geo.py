from sqlalchemy import func


def point_wkt(lat: float, lng: float) -> str:
    """SRID=4326 WKT for PostGIS geography input."""
    return f"SRID=4326;POINT({lng} {lat})"


def st_point(lat: float, lng: float):
    return func.ST_GeogFromText(point_wkt(lat, lng))


def lonlat_from_wkb(wkb_element) -> tuple[float, float]:
    """Extract (lat, lng) from a GeoAlchemy2 WKBElement."""
    from geoalchemy2.shape import to_shape

    point = to_shape(wkb_element)
    return point.y, point.x
