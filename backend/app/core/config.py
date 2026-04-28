from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = Field(
        default="postgresql+asyncpg://routemate:routemate@db:5432/routemate"
    )
    redis_url: str = Field(default="redis://redis:6379/0")
    jwt_secret: str = Field(default="change-me")
    jwt_ttl_hours: int = Field(default=24)
    firebase_credentials_path: str | None = Field(default=None)
    cors_origins: list[str] = Field(default_factory=lambda: ["*"])
    location_ttl_seconds: int = Field(default=60)
    dev_login_enabled: bool = Field(default=False)
    nominatim_base_url: str = Field(default="https://nominatim.openstreetmap.org")
    nominatim_user_agent: str = Field(default="RouteMates/0.2 (contact@routemate.app)")
    geocode_cache_ttl_seconds: int = Field(default=7 * 24 * 3600)
    geocode_min_interval_ms: int = Field(default=1100)


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
