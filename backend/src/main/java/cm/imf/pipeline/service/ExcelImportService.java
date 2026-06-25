package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateAgenceRequest;
import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.response.ImportResultResponse;
import cm.imf.pipeline.entity.ClientInformel;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.AgenceRepository;
import cm.imf.pipeline.repository.ClientInformelRepository;
import cm.imf.pipeline.repository.ImfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Génère les modèles Excel (.xlsx) et traite les imports pour :
 * clients, agents, agences, utilisateurs.
 *
 * Chaque ligne d'import est sauvegardée dans sa propre transaction
 * (saveAndFlush sans @Transactional sur la méthode parente) afin qu'une
 * contrainte violée n'empoisonne pas les lignes suivantes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final ImfRepository            imfRepo;
    private final ClientInformelRepository clientRepo;
    private final AgenceRepository         agenceRepo;
    private final IAdminService            adminService;

    // ── Valeurs autorisées par les contraintes DB ─────────────────────────────

    private static final String[] SECTEURS_VALIDES = {
        "AGRICOLE", "COMMERCE", "ARTISANAT", "ELEVAGE", "PECHE", "TRANSPORT", "SERVICES", "MIXTE"
    };
    private static final String[] SITUATION_FAM_VALIDES = {
        "CELIBATAIRE", "MARIE", "DIVORCE", "VEUF"
    };
    private static final String[] SEXES = {"H", "F"};
    private static final String[] ROLES_VALIDES = {
        "AGENT", "AGENT_CREDIT", "ANALYSTE", "CAISSIER", "CHEF_AGENCE",
        "AGENT_SAISIE", "RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI"
    };

    // Alias courants → code DB (ex: "Construction" → "ARTISANAT")
    private static final Map<String, String> SECTEUR_ALIASES;
    static {
        SECTEUR_ALIASES = new HashMap<>();
        SECTEUR_ALIASES.put("AGRICULTURE",  "AGRICOLE");
        SECTEUR_ALIASES.put("AGRICOLE",     "AGRICOLE");
        SECTEUR_ALIASES.put("COMMERCE",     "COMMERCE");
        SECTEUR_ALIASES.put("ARTISANAT",    "ARTISANAT");
        SECTEUR_ALIASES.put("ARTISAN",      "ARTISANAT");
        SECTEUR_ALIASES.put("CONSTRUCTION", "ARTISANAT");
        SECTEUR_ALIASES.put("BATIMENT",     "ARTISANAT");
        SECTEUR_ALIASES.put("BÂTIMENT",     "ARTISANAT");
        SECTEUR_ALIASES.put("BTP",          "ARTISANAT");
        SECTEUR_ALIASES.put("ELEVAGE",      "ELEVAGE");
        SECTEUR_ALIASES.put("PECHE",        "PECHE");
        SECTEUR_ALIASES.put("PÊCHE",        "PECHE");
        SECTEUR_ALIASES.put("TRANSPORT",    "TRANSPORT");
        SECTEUR_ALIASES.put("SERVICES",     "SERVICES");
        SECTEUR_ALIASES.put("SERVICE",      "SERVICES");
        SECTEUR_ALIASES.put("MIXTE",        "MIXTE");
    }

    // ── Couleurs ──────────────────────────────────────────────────────────────

    private static final byte[] COULEUR_ENTETE  = {(byte)0x1E, (byte)0x40, (byte)0x8A};
    private static final byte[] COULEUR_EXEMPLE = {(byte)0xD9, (byte)0xE8, (byte)0xFF};
    private static final byte[] COULEUR_REQUIS  = {(byte)0xFF, (byte)0xEB, (byte)0xCC};

    // ═══════════════════════════════════════════════════════════════════════════
    // GÉNÉRATION DES MODÈLES
    // ═══════════════════════════════════════════════════════════════════════════

    public byte[] genererTemplateClients() throws IOException {
        String[][] colonnes = {
            {"client_id_externe",       "Identifiant unique client (ex: CLI-001)",             "O"},
            {"nom_complet",             "Nom et prénom du client",                             "O"},
            {"telephone_principal",     "Numéro principal (ex: +237697001122)",                "O"},
            {"telephone_secondaire",    "Numéro secondaire (optionnel)",                       "N"},
            {"sexe",                    "H ou F",                                              "N"},
            {"date_naissance",          "Format YYYY-MM-DD (ex: 1985-03-15)",                  "N"},
            {"secteur_principal",       String.join(", ", SECTEURS_VALIDES),                   "N"},
            {"sous_secteur",            "Sous-secteur (ex: Alimentation, Maraîchage…)",        "N"},
            {"agence_code",             "Nom de l'agence (ex: Agence Nord Yaoundé)",           "N"},
            {"zone_id",                 "Identifiant de zone géographique",                    "N"},
            {"adresse_activite",        "Quartier ou marché principal",                        "N"},
            {"marche_principal",        "Nom du marché fréquenté",                             "N"},
            {"revenu_mensuel_estime",   "Revenu mensuel en FCFA (ex: 75000)",                  "N"},
            {"annees_experience",       "Années d'expérience dans le secteur",                 "N"},
            {"situation_familiale",     String.join(", ", SITUATION_FAM_VALIDES),              "N"},
            {"nombre_personnes_charge", "Nombre de personnes à charge",                       "N"},
            {"agent_email",             "Email de l'agent terrain responsable",                "O"},
        };
        String[][] exemple = {{
            "CLI-001", "Kouam Marie", "+237697001122", "+237656001100",
            "F", "1985-03-15", "COMMERCE", "Alimentation",
            "AG-NORD", "ZONE-1", "Marché Central Yaoundé", "Marché Central",
            "85000", "8", "MARIE", "3", "agent@imf.cm"
        }};
        // col 4 = sexe, col 6 = secteur_principal, col 14 = situation_familiale
        Map<Integer, String[]> dropdowns = new LinkedHashMap<>();
        dropdowns.put(4,  SEXES);
        dropdowns.put(6,  SECTEURS_VALIDES);
        dropdowns.put(14, SITUATION_FAM_VALIDES);
        return buildWorkbook("Clients", colonnes, exemple, dropdowns);
    }

    public byte[] genererTemplateAgents() throws IOException {
        String[][] colonnes = {
            {"username",               "Identifiant de connexion (ex: agent.dupont)",  "O"},
            {"email",                  "Adresse email professionnelle",                "O"},
            {"zone_id",                "Zone géographique de l'agent",                 "N"},
            {"agence_code",            "Code de l'agence (ex: AG-NORD)",               "N"},
            {"mot_de_passe_provisoire","Mot de passe initial (min. 8 caractères)",     "O"},
        };
        String[][] exemple = {{"dupont.jean", "dupont.jean@imf.cm", "ZONE-NORD", "AG-NORD", "Imf@2025!"}};
        return buildWorkbook("Agents", colonnes, exemple, Map.of());
    }

    public byte[] genererTemplateAgences() throws IOException {
        String[][] colonnes = {
            {"nom",         "Nom de l'agence (ex: Agence Nord Yaoundé)", "O"},
            {"ville",       "Ville de l'agence",                         "N"},
            {"responsable", "Nom du responsable d'agence",               "N"},
            {"telephone",   "Téléphone de l'agence (ex: +237222001100)", "N"},
        };
        String[][] exemple = {{"Agence Nord Yaoundé", "Yaoundé", "Jean Nkomo", "+237222001100"}};
        return buildWorkbook("Agences", colonnes, exemple, Map.of());
    }

    public byte[] genererTemplateUtilisateurs() throws IOException {
        String[][] colonnes = {
            {"username",               "Identifiant de connexion unique",                    "O"},
            {"email",                  "Adresse email professionnelle",                      "O"},
            {"role",                   String.join(", ", ROLES_VALIDES),                     "O"},
            {"zone_id",                "Zone géographique (optionnel)",                      "N"},
            {"mot_de_passe_provisoire","Mot de passe initial (min. 8 caractères)",           "O"},
        };
        String[][] exemple = {{"marie.analyste", "marie@imf.cm", "ANALYSTE", "ZONE-CENTRE", "Imf@2025!"}};
        // col 2 = role
        return buildWorkbook("Utilisateurs", colonnes, exemple, Map.of(2, ROLES_VALIDES));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT — sans @Transactional sur la méthode parente :
    // chaque save/saveAndFlush crée sa propre transaction JPA.
    // Si une ligne viole une contrainte, seule cette ligne est annulée.
    // ═══════════════════════════════════════════════════════════════════════════

    public ImportResultResponse importerClients(MultipartFile file, Long imfId) throws IOException {
        Imf imf = imfRepo.findById(imfId)
                .orElseThrow(() -> new IllegalArgumentException("IMF introuvable : " + imfId));

        List<String> erreurs = new ArrayList<>();
        int importe = 0, miseAJour = 0, total = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || estLigneVide(row)) continue;
                total++;
                try {
                    String clientId   = cellStr(row, 0);
                    String nomComplet = cellStr(row, 1);
                    String telephone  = cellStr(row, 2);

                    if (clientId.isBlank())  { erreurs.add("Ligne " + (i+1) + " : client_id_externe vide"); continue; }
                    if (nomComplet.isBlank()) { erreurs.add("Ligne " + (i+1) + " : nom_complet vide"); continue; }

                    var existing = clientRepo.findByImfIdAndClientIdExterne(imfId, clientId);
                    if (existing.isPresent()) {
                        ClientInformel c = existing.get();
                        c.setNomComplet(nomComplet);
                        if (!telephone.isBlank()) c.setTelephonePrincipal(telephone);
                        applyClientFields(c, row);
                        clientRepo.saveAndFlush(c);
                        miseAJour++;
                    } else {
                        ClientInformel c = ClientInformel.builder()
                                .imf(imf)
                                .clientIdExterne(clientId)
                                .nomComplet(nomComplet)
                                .telephonePrincipal(telephone.isBlank() ? null : telephone)
                                .secteurPrincipal("COMMERCE")
                                .build();
                        applyClientFields(c, row);
                        clientRepo.saveAndFlush(c);
                        importe++;
                    }
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    erreurs.add("Ligne " + (i+1) + " : " + msg);
                }
            }
        }
        log.info("Import clients IMF={} : total={} importe={} maj={} erreurs={}", imfId, total, importe, miseAJour, erreurs.size());
        return new ImportResultResponse(total, importe, miseAJour, erreurs.size(), erreurs);
    }

    public ImportResultResponse importerAgents(MultipartFile file, User currentUser) throws IOException {
        return importerUtilisateurs(file, currentUser, Role.AGENT);
    }

    public ImportResultResponse importerUtilisateurs(MultipartFile file, User currentUser) throws IOException {
        return importerUtilisateurs(file, currentUser, null);
    }

    public ImportResultResponse importerAgences(MultipartFile file, User currentUser) throws IOException {
        List<String> erreurs = new ArrayList<>();
        int importe = 0, total = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || estLigneVide(row)) continue;
                total++;
                try {
                    String nom = cellStr(row, 0);
                    if (nom.isBlank()) { erreurs.add("Ligne " + (i+1) + " : nom requis"); continue; }

                    var req = new CreateAgenceRequest(
                            nom,
                            blankToNull(cellStr(row, 1)),
                            blankToNull(cellStr(row, 2)),
                            blankToNull(cellStr(row, 3)));
                    adminService.createAgence(req);
                    importe++;
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    erreurs.add("Ligne " + (i+1) + " : " + msg);
                }
            }
        }
        log.info("Import agences : total={} importe={} erreurs={}", total, importe, erreurs.size());
        return new ImportResultResponse(total, importe, 0, erreurs.size(), erreurs);
    }

    // ── Utilitaire commun agents / utilisateurs ───────────────────────────────

    private ImportResultResponse importerUtilisateurs(MultipartFile file, User currentUser, Role roleForce) throws IOException {
        boolean modeAgent = (roleForce == Role.AGENT);
        List<String> erreurs = new ArrayList<>();
        int importe = 0, total = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || estLigneVide(row)) continue;
                total++;
                try {
                    String username = cellStr(row, 0);
                    String email    = cellStr(row, 1);
                    String zoneId   = cellStr(row, modeAgent ? 2 : 3);
                    String password = cellStr(row, 4);

                    if (username.isBlank()) { erreurs.add("Ligne " + (i+1) + " : username vide"); continue; }
                    if (email.isBlank())    { erreurs.add("Ligne " + (i+1) + " : email vide"); continue; }
                    if (password.isBlank()) { erreurs.add("Ligne " + (i+1) + " : mot_de_passe_provisoire vide"); continue; }

                    Role role = roleForce;
                    if (role == null) {
                        String roleStr = cellStr(row, 2).toUpperCase().trim();
                        try { role = Role.valueOf(roleStr); }
                        catch (Exception ex) {
                            erreurs.add("Ligne " + (i+1) + " : rôle invalide '" + roleStr + "'");
                            continue;
                        }
                    }

                    var req = new CreateUserRequest(
                            username, password, email,
                            role, blankToNull(zoneId), "fr", null, null);
                    adminService.createUser(req);
                    importe++;
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    erreurs.add("Ligne " + (i+1) + " : " + msg);
                }
            }
        }
        log.info("Import {} : total={} importe={} erreurs={}", modeAgent ? "agents" : "utilisateurs", total, importe, erreurs.size());
        return new ImportResultResponse(total, importe, 0, erreurs.size(), erreurs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHAMPS CLIENT — lecture + validation
    // ═══════════════════════════════════════════════════════════════════════════

    private void applyClientFields(ClientInformel c, Row row) {
        String sexe = cellStr(row, 4).toUpperCase();
        if (sexe.equals("H") || sexe.equals("F")) c.setSexe(sexe);

        String dateNaissStr = cellStr(row, 5);
        if (!dateNaissStr.isBlank()) {
            try { c.setDateNaissance(LocalDate.parse(dateNaissStr)); } catch (DateTimeParseException ignored) {}
        }

        // col 6 — secteur_principal : normalisation via alias puis validation
        String secteurBrut = cellStr(row, 6).toUpperCase().trim();
        if (!secteurBrut.isBlank()) {
            String secteur = SECTEUR_ALIASES.getOrDefault(secteurBrut, secteurBrut);
            if (Arrays.asList(SECTEURS_VALIDES).contains(secteur)) {
                c.setSecteurPrincipal(secteur);
            }
            // valeur inconnue → on garde la valeur par défaut "COMMERCE" déjà positionnée
        }

        String sousSecteur = cellStr(row, 7);
        if (!sousSecteur.isBlank()) c.setSousSecteur(sousSecteur);

        String agenceNom = cellStr(row, 8);
        if (!agenceNom.isBlank()) {
            agenceRepo.findFirstByNomContainingIgnoreCase(agenceNom).ifPresent(c::setAgence);
        }

        String zoneId = cellStr(row, 9);
        if (!zoneId.isBlank()) c.setZoneId(zoneId);

        String adresse = cellStr(row, 10);
        if (!adresse.isBlank()) c.setAdresseActivite(adresse);

        String marche = cellStr(row, 11);
        if (!marche.isBlank()) c.setMarchePrincipal(marche);

        String revenu = cellStr(row, 12);
        if (!revenu.isBlank()) {
            try { c.setRevenuMensuelEstime(new BigDecimal(revenu)); } catch (Exception ignored) {}
        }

        String anneesExp = cellStr(row, 13);
        if (!anneesExp.isBlank()) {
            try { c.setAnneesExperience(Short.parseShort(anneesExp)); } catch (Exception ignored) {}
        }

        // col 14 — situation_familiale (contrainte DB)
        String sitFam = cellStr(row, 14).toUpperCase().trim();
        if (Arrays.asList(SITUATION_FAM_VALIDES).contains(sitFam)) {
            c.setSituationFamiliale(sitFam);
        }

        String nbPersonnes = cellStr(row, 15);
        if (!nbPersonnes.isBlank()) {
            try { c.setNombrePersonnesCharge(Short.parseShort(nbPersonnes)); } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTION DU CLASSEUR EXCEL
    // ═══════════════════════════════════════════════════════════════════════════

    private byte[] buildWorkbook(String titre, String[][] colonnes, String[][] exemples,
                                  Map<Integer, String[]> dropdowns) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Import_" + titre);
            sheet.setDefaultColumnWidth(22);

            CellStyle styleEntete  = creerStyleEntete(wb);
            CellStyle styleExemple = creerStyleExemple(wb);
            CellStyle styleRequis  = creerStyleRequis(wb);

            // Ligne 0 — en-têtes
            Row header = sheet.createRow(0);
            for (int c = 0; c < colonnes.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(colonnes[c][0]);
                cell.setCellStyle(styleEntete);
                sheet.setColumnWidth(c, Math.max(colonnes[c][0].length(), 20) * 280);
            }

            // Ligne 1 — exemple coloré
            if (exemples.length > 0) {
                Row exRow = sheet.createRow(1);
                for (int c = 0; c < exemples[0].length && c < colonnes.length; c++) {
                    Cell cell = exRow.createCell(c);
                    cell.setCellValue(exemples[0][c]);
                    cell.setCellStyle("O".equals(colonnes[c][2]) ? styleRequis : styleExemple);
                }
            }

            // Lignes 2-51 — saisie utilisateur
            for (int r = 2; r < 52; r++) {
                Row dataRow = sheet.createRow(r);
                for (int c = 0; c < colonnes.length; c++) dataRow.createCell(c);
            }

            // Listes déroulantes pour les colonnes contraintes
            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            for (Map.Entry<Integer, String[]> entry : dropdowns.entrySet()) {
                int col = entry.getKey();
                String[] values = entry.getValue();
                CellRangeAddressList range = new CellRangeAddressList(2, 51, col, col);
                DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(values);
                DataValidation validation = dvHelper.createValidation(constraint, range);
                validation.setSuppressDropDownArrow(false);
                validation.setShowErrorBox(true);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox("Valeur invalide",
                        "Choisissez une valeur dans la liste déroulante.");
                sheet.addValidationData(validation);
            }

            // Onglet Guide
            XSSFSheet guide = wb.createSheet("Guide");
            guide.setColumnWidth(0, 10000);
            guide.setColumnWidth(1, 18000);
            guide.setColumnWidth(2, 4000);
            Row gHead = guide.createRow(0);
            gHead.createCell(0).setCellValue("Colonne");
            gHead.createCell(1).setCellValue("Description / Valeurs autorisées");
            gHead.createCell(2).setCellValue("Requis");
            for (Cell cell : gHead) cell.setCellStyle(styleEntete);
            for (int r = 0; r < colonnes.length; r++) {
                Row row = guide.createRow(r + 1);
                row.createCell(0).setCellValue(colonnes[r][0]);
                row.createCell(1).setCellValue(colonnes[r][1]);
                row.createCell(2).setCellValue("O".equals(colonnes[r][2]) ? "Oui" : "Non");
            }

            wb.setActiveSheet(0);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ═══════════════════════════════════════════════════════════════════════════

    private CellStyle creerStyleEntete(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(new XSSFColor(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF}, null));
        f.setFontHeightInPoints((short)11);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(COULEUR_ENTETE, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle creerStyleExemple(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(COULEUR_EXEMPLE, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setItalic(true);
        s.setFont(f);
        return s;
    }

    private CellStyle creerStyleRequis(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(COULEUR_REQUIS, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setItalic(true);
        s.setFont(f);
        return s;
    }

    private static String cellStr(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v)) yield String.valueOf((long) v);
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    private static boolean estLigneVide(Row row) {
        for (Cell c : row) {
            if (c.getCellType() != CellType.BLANK && !cellStr(row, c.getColumnIndex()).isBlank())
                return false;
        }
        return true;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
