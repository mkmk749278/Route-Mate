from datetime import UTC, datetime

from app.api.rides import _next_recurring_depart


def _dt(year: int, month: int, day: int, hour: int = 9) -> datetime:
    return datetime(year, month, day, hour, tzinfo=UTC)


def test_no_recurrence_returns_none() -> None:
    assert _next_recurring_depart(_dt(2026, 4, 28), 0) is None


def test_daily_recurrence_picks_next_day() -> None:
    # Tuesday + Mon-Fri (1+2+4+8+16 = 31)
    tuesday = _dt(2026, 4, 28)
    assert tuesday.weekday() == 1
    nxt = _next_recurring_depart(tuesday, 0b0011111)
    assert nxt is not None and nxt.weekday() == 2


def test_weekly_skips_to_matching_weekday() -> None:
    # Friday + only Mondays (bit 0)
    friday = _dt(2026, 5, 1)
    assert friday.weekday() == 4
    nxt = _next_recurring_depart(friday, 0b0000001)
    assert nxt is not None
    assert nxt.weekday() == 0  # Monday
    assert (nxt - friday).days == 3
