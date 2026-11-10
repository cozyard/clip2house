from typing import Literal

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="C2H_", env_file=".env", extra="ignore")

    ledger_base_url: str = "http://localhost:8080"
    internal_api_secret: str = "dev-internal-secret"
    port: int = 8100

    provider: Literal["nvidia", "mock"] = "nvidia"
    nvidia_api_key: str = Field(default="", validation_alias=AliasChoices("C2H_NVIDIA_API_KEY", "NVIDIA_API_KEY"))
    nvidia_model: str = "nvidia/nemotron-3.5-lightning-30b-a3b"
    nvidia_base_url: str = "https://integrate.api.nvidia.com/v1"

    quiet_window_min_s: float = 8.0
    quiet_window_max_s: float = 90.0

    retry_limit: int = 2
    offline_minutes: float = 10.0

    persona_count: int = 24
    persona_seed: int = 42

    classifier_llm_enabled: bool = False

    consistency_interval_s: float = 600.0

    gen_version: str = "gen-2026.07"


settings = Settings()
