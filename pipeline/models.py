"""Shared model classes — importable by train_models.py and compute_mcrs.py."""

import numpy as np


class CalibratedModel:
    """XGBoost + isotonic post-hoc calibration. Module-level for pickle compatibility."""

    def __init__(self, base, cal):
        self.base = base
        self.cal  = cal

    def predict_proba(self, X):
        p     = self.base.predict_proba(X)[:, 1]
        p_cal = self.cal.predict(p)
        return np.column_stack([1 - p_cal, p_cal])
