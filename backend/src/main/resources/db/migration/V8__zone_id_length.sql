-- V8 : Élargir zone_id pour stocker les noms d'agences réels (jusqu'à 100 chars)
ALTER TABLE app.utilisateurs
    ALTER COLUMN zone_id TYPE VARCHAR(100);
