"""
config.py — Configuration centralisée du pipeline ETL IMF.
Toutes les variables sont lues depuis l'environnement (ou .env en dev).
"""

from __future__ import annotations

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from exceptions import ConfigurationError


class DatabaseSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="POSTGRES_", extra="ignore")

    host: str = Field(default="localhost", alias="POSTGRES_HOST")
    port: int = Field(default=5432, alias="POSTGRES_PORT")
    db: str = Field(default="imf_db", alias="POSTGRES_DB")
    user: str = Field(default="imf_user", alias="POSTGRES_USER")
    password: str = Field(default="changeme", alias="POSTGRES_PASSWORD")
    ssl_mode: str = Field(default="disable", alias="POSTGRES_SSL_MODE")

    # Schémas PostgreSQL
    staging_schema: str = Field(default="staging", alias="STAGING_SCHEMA")
    dw_schema: str = Field(default="dw", alias="DW_SCHEMA")
    app_schema: str = Field(default="app", alias="APP_SCHEMA")

    @property
    def dsn(self) -> str:
        base = (
            f"host={self.host} port={self.port} dbname={self.db} "
            f"user={self.user} password={self.password}"
        )
        if self.ssl_mode and self.ssl_mode != "disable":
            base += f" sslmode={self.ssl_mode}"
        return base

    @property
    def jdbc_url(self) -> str:
        return f"jdbc:postgresql://{self.host}:{self.port}/{self.db}"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )


class APISettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    spring_base_url: str = Field(
        default="http://localhost:8080", alias="SPRING_BASE_URL"
    )
    api_key: str = Field(default="changeme_internal_api_key", alias="SPRING_API_KEY")

    # Timeouts en secondes
    connect_timeout: int = Field(default=5, alias="API_CONNECT_TIMEOUT")
    read_timeout: int = Field(default=30, alias="API_READ_TIMEOUT")

    # Retry
    max_retries: int = Field(default=3, alias="API_MAX_RETRIES")
    retry_wait_seconds: float = Field(default=2.0, alias="API_RETRY_WAIT_SECONDS")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )


class PipelineSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="PIPELINE_", extra="ignore")

    # Seuils métier
    par30_threshold_days: int = Field(default=30, alias="PAR30_DAYS")
    par90_threshold_days: int = Field(default=90, alias="PAR90_DAYS")

    # Taille des batches d'insertion DW
    batch_size: int = Field(default=500, alias="PIPELINE_BATCH_SIZE")

    # Nombre de jours minimal de retard pour générer une alerte
    alerte_min_jours_retard: int = Field(default=30, alias="ALERTE_MIN_JOURS_RETARD")

    # Environnement courant
    env: str = Field(default="dev", alias="APP_ENV")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )


class MLSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MCRS_", extra="ignore")

    # Répertoires modèle
    model_dir: str = Field(default="/ml/models/mcrs", alias="MCRS_MODEL_DIR")
    champion_subdir: str = Field(default="champion", alias="MCRS_CHAMPION_SUBDIR")
    challenger_subdir: str = Field(default="challenger", alias="MCRS_CHALLENGER_SUBDIR")

    # Scoring batch
    batch_size: int = Field(default=500, alias="MCRS_BATCH_SIZE")

    # Poids composantes MCRS (doivent sommer à 1.0)
    poids_crs: float = Field(default=0.35, alias="MCRS_POIDS_CRS")
    poids_rps: float = Field(default=0.45, alias="MCRS_POIDS_RPS")
    poids_csi: float = Field(default=0.20, alias="MCRS_POIDS_CSI")

    # Seuils de risque
    seuil_risque_modere: float = Field(default=0.30, alias="MCRS_SEUIL_MODERE")
    seuil_risque_eleve: float = Field(default=0.55, alias="MCRS_SEUIL_ELEVE")
    seuil_risque_critique: float = Field(default=0.75, alias="MCRS_SEUIL_CRITIQUE")

    # Détection drift PSI
    psi_threshold: float = Field(default=0.20, alias="MCRS_PSI_THRESHOLD")
    psi_fenetre_reference_jours: int = Field(default=90, alias="MCRS_PSI_FENETRE_REF")
    psi_fenetre_courante_jours: int = Field(default=7, alias="MCRS_PSI_FENETRE_CUR")

    # Entraînement
    fenetre_historique_jours: int = Field(default=730, alias="MCRS_HISTORIQUE_JOURS")
    n_folds_cv: int = Field(default=5, alias="MCRS_N_FOLDS")
    seuil_promo_auc: float = Field(default=0.005, alias="MCRS_SEUIL_PROMO_AUC")

    # Intervalles de confiance (bootstrap)
    ic_n_bootstrap: int = Field(default=200, alias="MCRS_IC_BOOTSTRAP")
    ic_niveau: float = Field(default=0.90, alias="MCRS_IC_NIVEAU")

    # URL du service FastAPI ML (appelé par Airflow et Spring Boot)
    api_url: str = Field(default="http://ml-api:8090", alias="MCRS_API_URL")
    api_timeout: int = Field(default=60, alias="MCRS_API_TIMEOUT")

    @property
    def champion_dir(self) -> str:
        import os

        return os.path.join(self.model_dir, self.champion_subdir)

    @property
    def challenger_dir(self) -> str:
        import os

        return os.path.join(self.model_dir, self.challenger_subdir)

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )


class Settings:
    """Point d'entrée unique pour toute la configuration."""

    def __init__(self) -> None:
        try:
            self.db = DatabaseSettings()
            self.api = APISettings()
            self.pipeline = PipelineSettings()
            self.ml = MLSettings()
        except Exception as exc:
            raise ConfigurationError(
                "Erreur de chargement de la configuration",
                details=str(exc),
            ) from exc

    @property
    def is_production(self) -> bool:
        return self.pipeline.env.lower() == "prod"


# Singleton accessible par tous les modules
settings = Settings()
