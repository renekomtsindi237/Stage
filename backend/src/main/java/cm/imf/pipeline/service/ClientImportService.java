package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.ClientImportRow;
import cm.imf.pipeline.dto.response.ImportResultResponse;
import cm.imf.pipeline.entity.Agence;
import cm.imf.pipeline.entity.ClientInformel;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.ClientInformelRepository;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientImportService implements IClientImportService {

    private final JdbcTemplate           jdbc;
    private final ClientInformelRepository clientRepo;
    private final UserRepository          userRepo;
    private final ImfRepository           imfRepo;
    private final AgenceRepository        agenceRepo;

    @Value("${imf.pipeline.staging-schema:staging}")
    private String stagingSchema;

    public static final String SEPARATEUR = ";";

    public static final String[] EN_TETES = {
        "client_id_externe", "nom_complet", "telephone_principal", "telephone_secondaire",
        "sexe", "date_naissance", "secteur_principal", "sous_secteur",
        "revenu_mensuel_estime", "annees_experience", "niveau_education", "situation_familiale",
        "nombre_personnes_charge", "zone_id", "agence_code", "adresse_activite",
        "marche_principal", "frequence_marche", "latitude_activite", "longitude_activite",
        "agent_email",
        "nb_collectes_12m", "montant_total_collectes_12m", "regularite_collecte_pct",
        "taux_remboursement_pct", "jours_retard_moyen", "jours_retard_max",
        "nb_incidents_paiement", "montant_impaye_courant", "date_premier_pret",
        "montant_pret_actif", "encours_restant", "nb_prets_total"
    };

    // ── Import ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ImportResultResponse importerDepuisCsv(MultipartFile fichier, String imfCode) {
        List<String> lignesErreur = new ArrayList<>();
        int importe = 0, miseAJour = 0, total = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fichier.getInputStream(), StandardCharsets.UTF_8))) {

            String ligne;
            boolean premiereLigne = true;

            while ((ligne = reader.readLine()) != null) {
                // Ignorer BOM UTF-8 et lignes vides
                ligne = ligne.replace("﻿", "").trim();
                if (ligne.isEmpty() || ligne.startsWith("#")) continue;
                if (premiereLigne) { premiereLigne = false; continue; } // sauter l'en-tête

                total++;
                try {
                    String[] cols = ligne.split(SEPARATEUR, -1);
                    if (cols.length < EN_TETES.length) {
                        lignesErreur.add("Ligne " + total + " : seulement " + cols.length
                                + " colonnes, attendu " + EN_TETES.length);
                        continue;
                    }

                    ClientImportRow row = parseLigne(cols, total, lignesErreur);
                    if (row == null) continue;

                    // Résoudre l'IMF
                    Imf imf = resolveImf(row.agentEmail(), imfCode, total, lignesErreur);
                    if (imf == null) continue;

                    // Résoudre l'agence (optionnel)
                    Agence agence = resolveAgence(row.agenceCode(), imf.getId());

                    boolean existait = clientRepo.existsByImfIdAndClientIdExterne(imf.getId(), row.clientIdExterne());
                    upsertClientInformel(row, imf, agence);
                    upsertStgClient(row, imf.getCode());

                    if (existait) miseAJour++; else importe++;

                } catch (Exception e) {
                    log.warn("Erreur import ligne {}: {}", total, e.getMessage());
                    lignesErreur.add("Ligne " + total + " : " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Erreur lecture fichier CSV", e);
            lignesErreur.add("Erreur lecture fichier : " + e.getMessage());
        }

        return new ImportResultResponse(total, importe, miseAJour, lignesErreur.size(), lignesErreur);
    }

    // ── Template CSV ─────────────────────────────────────────────────────────────

    @Override
    public String genererTemplateCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(SEPARATEUR, EN_TETES)).append("\n");
        // Ligne exemple 1 — bon payeur
        sb.append("CLT-EX-001;Jean DUPONT;+237 699 00 00 01;;M;1985-06-15;COMMERCE;Vente de vivres frais;150000;10;SECONDAIRE;MARIE;4;YDE-CENTRE;YDE;Marché central Yaoundé;Marché Mvog-Mbi;QUOTIDIEN;3.866667;11.516667;agent@imf.cm;48;7200000;0.92;0.95;2.5;8;1;0;2024-03-01;1500000;750000;2\n");
        // Ligne exemple 2 — client à risque
        sb.append("CLT-EX-002;Marie FOUDA;+237 677 00 00 02;;F;1990-11-20;AGRICOLE;Maraîchage;95000;5;PRIMAIRE;CELIBATAIRE;2;YDE-SUD;YDE;Nkol-Bisson;Marché Melen;HEBDOMADAIRE;3.840000;11.500000;agent@imf.cm;24;1800000;0.52;0.72;18.3;45;3;120000;2024-06-01;750000;580000;1\n");
        return sb.toString();
    }

    // ── Export par agent ─────────────────────────────────────────────────────────

    @Override
    public String exporterClientsAgent(String agentEmail, String imfCode) {
        String sql = """
                SELECT
                    ci.client_id_externe,
                    ci.nom_complet,
                    ci.telephone_principal,
                    COALESCE(ci.telephone_secondaire, '')          AS telephone_secondaire,
                    COALESCE(ci.sexe, '')                          AS sexe,
                    COALESCE(ci.date_naissance::TEXT, '')          AS date_naissance,
                    ci.secteur_principal,
                    COALESCE(ci.sous_secteur, '')                  AS sous_secteur,
                    COALESCE(ci.revenu_mensuel_estime::TEXT, '')   AS revenu_mensuel_estime,
                    COALESCE(ci.annees_experience::TEXT, '')       AS annees_experience,
                    COALESCE(ci.niveau_education, '')              AS niveau_education,
                    COALESCE(ci.situation_familiale, '')           AS situation_familiale,
                    COALESCE(ci.nombre_personnes_charge::TEXT, '') AS nombre_personnes_charge,
                    COALESCE(ci.zone_id, '')                       AS zone_id,
                    COALESCE(a.nom, '')                            AS agence_code,
                    COALESCE(ci.adresse_activite, '')              AS adresse_activite,
                    COALESCE(ci.marche_principal, '')              AS marche_principal,
                    COALESCE(ci.frequence_marche, '')              AS frequence_marche,
                    COALESCE(ci.latitude_activite::TEXT, '')       AS latitude_activite,
                    COALESCE(ci.longitude_activite::TEXT, '')      AS longitude_activite,
                    u.email                                        AS agent_email,
                    COALESCE(sc.nb_collectes_total::TEXT, '0')     AS nb_collectes_12m,
                    COALESCE(sc.montant_total_collectes::TEXT,'0') AS montant_total_collectes_12m,
                    ''                                             AS regularite_collecte_pct,
                    COALESCE(sc.taux_remboursement_historique::TEXT,'') AS taux_remboursement_pct,
                    ''                                             AS jours_retard_moyen,
                    ''                                             AS jours_retard_max,
                    ''                                             AS nb_incidents_paiement,
                    ''                                             AS montant_impaye_courant,
                    COALESCE(sc.date_premier_pret::TEXT, '')       AS date_premier_pret,
                    ''                                             AS montant_pret_actif,
                    ''                                             AS encours_restant,
                    COALESCE(sc.nb_prets_total::TEXT, '0')         AS nb_prets_total
                FROM app.clients_informels ci
                JOIN app.imf i ON i.id = ci.imf_id
                JOIN app.utilisateurs u ON u.email = ?
                LEFT JOIN app.agences a ON a.id = ci.agence_id
                LEFT JOIN %s.stg_clients sc
                       ON sc.imf_code = i.code AND sc.client_id_externe = ci.client_id_externe
                WHERE ci.imf_id = u.imf_id
                  AND i.code = UPPER(?)
                  AND ci.actif = TRUE
                ORDER BY ci.nom_complet
                """.formatted(stagingSchema);

        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, agentEmail, imfCode);
        } catch (Exception e) {
            log.warn("Export agent {} / imf {} échoué: {}", agentEmail, imfCode, e.getMessage());
            rows = List.of();
        }

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(SEPARATEUR, EN_TETES)).append("\n");
        for (Map<String, Object> row : rows) {
            csv.append(safe(row, "client_id_externe")).append(SEPARATEUR)
               .append(safe(row, "nom_complet")).append(SEPARATEUR)
               .append(safe(row, "telephone_principal")).append(SEPARATEUR)
               .append(safe(row, "telephone_secondaire")).append(SEPARATEUR)
               .append(safe(row, "sexe")).append(SEPARATEUR)
               .append(safe(row, "date_naissance")).append(SEPARATEUR)
               .append(safe(row, "secteur_principal")).append(SEPARATEUR)
               .append(safe(row, "sous_secteur")).append(SEPARATEUR)
               .append(safe(row, "revenu_mensuel_estime")).append(SEPARATEUR)
               .append(safe(row, "annees_experience")).append(SEPARATEUR)
               .append(safe(row, "niveau_education")).append(SEPARATEUR)
               .append(safe(row, "situation_familiale")).append(SEPARATEUR)
               .append(safe(row, "nombre_personnes_charge")).append(SEPARATEUR)
               .append(safe(row, "zone_id")).append(SEPARATEUR)
               .append(safe(row, "agence_code")).append(SEPARATEUR)
               .append(safe(row, "adresse_activite")).append(SEPARATEUR)
               .append(safe(row, "marche_principal")).append(SEPARATEUR)
               .append(safe(row, "frequence_marche")).append(SEPARATEUR)
               .append(safe(row, "latitude_activite")).append(SEPARATEUR)
               .append(safe(row, "longitude_activite")).append(SEPARATEUR)
               .append(safe(row, "agent_email")).append(SEPARATEUR)
               .append(safe(row, "nb_collectes_12m")).append(SEPARATEUR)
               .append(safe(row, "montant_total_collectes_12m")).append(SEPARATEUR)
               .append(safe(row, "regularite_collecte_pct")).append(SEPARATEUR)
               .append(safe(row, "taux_remboursement_pct")).append(SEPARATEUR)
               .append(safe(row, "jours_retard_moyen")).append(SEPARATEUR)
               .append(safe(row, "jours_retard_max")).append(SEPARATEUR)
               .append(safe(row, "nb_incidents_paiement")).append(SEPARATEUR)
               .append(safe(row, "montant_impaye_courant")).append(SEPARATEUR)
               .append(safe(row, "date_premier_pret")).append(SEPARATEUR)
               .append(safe(row, "montant_pret_actif")).append(SEPARATEUR)
               .append(safe(row, "encours_restant")).append(SEPARATEUR)
               .append(safe(row, "nb_prets_total")).append("\n");
        }

        // Si aucun client en DB, retourner le template
        if (rows.isEmpty()) return genererTemplateCsv();

        return csv.toString();
    }

    // ── Helpers privés ───────────────────────────────────────────────────────────

    private ClientImportRow parseLigne(String[] cols, int numLigne, List<String> erreurs) {
        try {
            return new ClientImportRow(
                    col(cols, 0),
                    col(cols, 1),
                    col(cols, 2),
                    col(cols, 3),
                    col(cols, 4),
                    parseDate(col(cols, 5)),
                    col(cols, 6).isEmpty() ? "COMMERCE" : col(cols, 6),
                    col(cols, 7),
                    parseBigDecimal(col(cols, 8)),
                    parseShort(col(cols, 9)),
                    col(cols, 10),
                    col(cols, 11),
                    parseShort(col(cols, 12)),
                    col(cols, 13),
                    col(cols, 14),
                    col(cols, 15),
                    col(cols, 16),
                    col(cols, 17),
                    parseBigDecimal(col(cols, 18)),
                    parseBigDecimal(col(cols, 19)),
                    col(cols, 20),
                    parseInt(col(cols, 21)),
                    parseBigDecimal(col(cols, 22)),
                    parseDouble(col(cols, 23)),
                    parseDouble(col(cols, 24)),
                    parseDouble(col(cols, 25)),
                    parseDouble(col(cols, 26)),
                    parseInt(col(cols, 27)),
                    parseBigDecimal(col(cols, 28)),
                    parseDate(col(cols, 29)),
                    parseBigDecimal(col(cols, 30)),
                    parseBigDecimal(col(cols, 31)),
                    parseInt(col(cols, 32))
            );
        } catch (Exception e) {
            erreurs.add("Ligne " + numLigne + " : parsing échoué — " + e.getMessage());
            return null;
        }
    }

    private Imf resolveImf(String agentEmail, String imfCode, int numLigne, List<String> erreurs) {
        // Priorité : résoudre via agent_email
        if (agentEmail != null && !agentEmail.isBlank()) {
            Optional<User> agent = userRepo.findByEmail(agentEmail);
            if (agent.isPresent() && agent.get().getImf() != null) {
                return agent.get().getImf();
            }
        }
        // Fallback : imfCode paramètre
        if (imfCode != null && !imfCode.isBlank()) {
            Optional<Imf> imf = imfRepo.findByCode(imfCode.toUpperCase());
            if (imf.isPresent()) return imf.get();
        }
        erreurs.add("Ligne " + numLigne + " : IMF introuvable pour agent_email=" + agentEmail + " / imfCode=" + imfCode);
        return null;
    }

    private Agence resolveAgence(String agenceCode, Long imfId) {
        if (agenceCode == null || agenceCode.isBlank()) return null;
        return agenceRepo.findByImfIdAndNomIgnoreCase(imfId, agenceCode).orElse(null);
    }

    private void upsertClientInformel(ClientImportRow row, Imf imf, Agence agence) {
        Optional<ClientInformel> existant =
                clientRepo.findByImfIdAndClientIdExterne(imf.getId(), row.clientIdExterne());

        ClientInformel client = existant.orElse(ClientInformel.builder()
                .imf(imf)
                .clientIdExterne(row.clientIdExterne())
                .build());

        client.setNomComplet(row.nomComplet());
        client.setTelephonePrincipal(row.telephonePrincipal());
        client.setTelephoneSecondaire(row.telephoneSecondaire());
        client.setSexe(row.sexe());
        client.setDateNaissance(row.dateNaissance());
        client.setSecteurPrincipal(row.secteurPrincipal() != null ? row.secteurPrincipal() : "COMMERCE");
        client.setSousSecteur(row.sousSecteur());
        client.setRevenuMensuelEstime(row.revenuMensuelEstime());
        client.setAnneesExperience(row.anneesExperience());
        client.setNiveauEducation(row.niveauEducation());
        client.setSituationFamiliale(row.situationFamiliale());
        client.setNombrePersonnesCharge(row.nombrePersonnesCharge());
        client.setZoneId(row.zoneId());
        client.setAgence(agence);
        client.setAdresseActivite(row.adresseActivite());
        client.setMarchePrincipal(row.marchePrincipal());
        client.setFrequenceMarche(row.frequenceMarche());
        client.setLatitudeActivite(row.latitudeActivite());
        client.setLongitudeActivite(row.longitudeActivite());
        client.setActif(true);

        clientRepo.save(client);
    }

    private void upsertStgClient(ClientImportRow row, String imfCode) {
        String sql = """
                INSERT INTO %s.stg_clients (
                    imf_code, client_id_externe, nom_complet, telephone_principal,
                    zone_id, agence_code, secteur_principal, revenu_mensuel_estime,
                    latitude_activite, longitude_activite,
                    date_premier_pret, anciennete_jours,
                    nb_collectes_total, montant_total_collectes,
                    nb_prets_total, taux_remboursement_historique
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (imf_code, client_id_externe) DO UPDATE SET
                    nom_complet                   = EXCLUDED.nom_complet,
                    telephone_principal           = EXCLUDED.telephone_principal,
                    zone_id                       = EXCLUDED.zone_id,
                    agence_code                   = EXCLUDED.agence_code,
                    secteur_principal             = EXCLUDED.secteur_principal,
                    revenu_mensuel_estime         = EXCLUDED.revenu_mensuel_estime,
                    latitude_activite             = EXCLUDED.latitude_activite,
                    longitude_activite            = EXCLUDED.longitude_activite,
                    date_premier_pret             = EXCLUDED.date_premier_pret,
                    anciennete_jours              = EXCLUDED.anciennete_jours,
                    nb_collectes_total            = EXCLUDED.nb_collectes_total,
                    montant_total_collectes       = EXCLUDED.montant_total_collectes,
                    nb_prets_total                = EXCLUDED.nb_prets_total,
                    taux_remboursement_historique = EXCLUDED.taux_remboursement_historique,
                    _dbt_updated_at               = NOW()
                """.formatted(stagingSchema);

        int ancienneteJours = row.datePremierPret() != null
                ? (int) (LocalDate.now().toEpochDay() - row.datePremierPret().toEpochDay())
                : 0;

        jdbc.update(sql,
                imfCode.toUpperCase(),
                row.clientIdExterne(),
                row.nomComplet(),
                row.telephonePrincipal(),
                row.zoneId(),
                row.agenceCode(),
                row.secteurPrincipal() != null ? row.secteurPrincipal() : "COMMERCE",
                row.revenuMensuelEstime(),
                row.latitudeActivite(),
                row.longitudeActivite(),
                row.datePremierPret(),
                ancienneteJours,
                row.nbCollectes12m() != null ? row.nbCollectes12m() : 0,
                row.montantTotalCollectes12m() != null ? row.montantTotalCollectes12m() : BigDecimal.ZERO,
                row.nbPretsTotal() != null ? row.nbPretsTotal() : 0,
                row.tauxRemboursementPct() != null ? BigDecimal.valueOf(row.tauxRemboursementPct()) : null
        );
    }

    private String safe(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString().replace(SEPARATEUR, ",");
    }

    private String col(String[] cols, int i) {
        return (i < cols.length) ? cols[i].trim() : "";
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { return null; }
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s.replace(",", ".")); } catch (NumberFormatException e) { return null; }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.replace(",", ".")); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private Short parseShort(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Short.parseShort(s); } catch (NumberFormatException e) { return null; }
    }
}
