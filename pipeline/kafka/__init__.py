"""IMF Pipeline — Kafka clients (producers + consumers)."""
from .producers import CollecteProducer, ScoringRequestProducer, AlerteProducer, CreanceProducer
from .consumers import ScoringResultConsumer, CollecteConsumer

__all__ = [
    "CollecteProducer", "ScoringRequestProducer", "AlerteProducer", "CreanceProducer",
    "ScoringResultConsumer", "CollecteConsumer",
]
