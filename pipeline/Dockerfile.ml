FROM python:3.11-slim

WORKDIR /app

# Dépendances système (compilateur pour xgboost/shap)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc g++ libpq-dev curl \
    && rm -rf /var/lib/apt/lists/*

# Dépendances Python (subset ML + FastAPI uniquement)
COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir \
    psycopg2-binary==2.9.9 \
    pandas==2.2.2 \
    numpy==1.26.4 \
    scikit-learn==1.4.2 \
    xgboost==2.0.3 \
    shap==0.45.1 \
    scipy==1.13.0 \
    pyarrow==16.1.0 \
    lifelines==0.29.0 \
    fastapi==0.111.0 \
    "uvicorn[standard]==0.30.1" \
    pydantic==2.7.1 \
    pydantic-settings==2.3.1 \
    httpx==0.27.0

COPY src/ /app/pipeline/src/

EXPOSE 8090

# Répertoire modèle (monté en volume en prod)
RUN mkdir -p /ml/models/mcrs

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8090/model/health || exit 1

CMD ["uvicorn", "pipeline.src.ml.api_service:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "2"]
