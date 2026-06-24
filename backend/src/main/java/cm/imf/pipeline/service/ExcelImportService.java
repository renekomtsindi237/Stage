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
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Génère les modèles Excel (.xlsx) et traite les imports pour :
 * clients, agents, agences, utilisateurs.
 *
 * La première ligne est toujours un en-tête coloré.
 * La deuxième ligne contient un exemple commenté.
 * À partir de la troisième ligne, les données réelles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final ImfRepository           imfRepo;
    private final ClientInformelRepository clientRepo;
    private final AgenceRepository        agenceRepo;
    private final IAdminService           adminService;

    // ── Couleurs ──────────────────────────────────────────────────────────────

    private static final byte[] COULEUR_ENTETE  = {(byte)0x1E, (byte)0x40, (byte)0x8A}; // bleu marine
    private static final byte[] COULEUR_EXEMPLE = {(byte)0xD9, (byte)0xE8, (byte)0xFF}; // bleu très clair
    private static final byte[] COULEUR_REQUIS  = {(byte)0xFF, (byte)0xEB, (byte)0xCC}; // orange clair (requis)

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
            {"secteur_principal",       "Agriculture, Commerce, Artisanat, Elevage, Peche...", "N"},
            {"sous_secteur",            "Sous-secteur d'activité",                             "N"},
            {"agence_code",             "Code de l'agence (ex: AG-NORD)",                      "N"},
            {"zone_id",                 "Identifiant de zone géographique",                    "N"},
            {"adresse_activite",        "Quartier ou marché principal",                        "N"},
            {"marche_principal",        "Nom du marché fréquenté",                             "N"},
            {"revenu_mensuel_estime",   "Revenu mensuel en FCFA (ex: 75000)",                  "N"},
            {"annees_experience",       "Années d'expérience dans le secteur",                 "N"},
            {"situation_familiale",     "CELIBATAIRE, MARIE, DIVORCE, VEUF",                  "N"},
            {"nombre_personnes_charge", "Nombre de personnes à charge",                       "N"},
            {"agent_email",             "Email de l'agent terrain responsable",                "O"},
        };
        String[][] exemple = {
            {"CLI-001", "Kouam Marie", "+237697001122", "+237656001100",
             "F", "1985-03-15", "Commerce", "Alimentation",
             "AG-NORD", "ZONE-1", "Marché Central Yaoundé", "Marché Central",
             "85000", "8", "MARIE", "3", "agent@imf.cm"}
        };
        return buildWorkbook("Clients", colonnes, exemple);
    }

    public byte[] genererTemplateAgents() throws IOException {
        String[][] colonnes = {
            {"username",             "Identifiant de connexion (ex: agent.dupont)",  "O"},
            {"email",                "Adresse email professionnelle",                "O"},
            {"zone_id",              "Zone géographique de l'agent",                 "N"},
            {"agence_code",          "Code de l'agence (ex: AG-NORD)",               "N"},
            {"mot_de_passe_provisoire", "Mot de passe initial (min. 8 caractères)", "O"},
        };
        String[][] exemple = {
            {"dupont.jean", "dupont.jean@imf.cm", "ZONE-NORD", "AG-NORD", "Imf@2025!"}
        };
        return buildWorkbook("Agents", colonnes, exemple);
    }

    public byte[] genererTemplateAgences() throws IOException {
        String[][] colonnes = {
            {"nom",          "Nom de l'agence (ex: Agence Nord Yaoundé)", "O"},
            {"ville",        "Ville de l'agence",                         "N"},
            {"responsable",  "Nom du responsable d'agence",               "N"},
            {"telephone",    "Téléphone de l'agence (ex: +237222001100)", "N"},
        };
        String[][] exemple = {
            {"Agence Nord Yaoundé", "Yaoundé", "Jean Nkomo", "+237222001100"}
        };
        return buildWorkbook("Agences", colonnes, exemple);
    }

    public byte[] genererTemplateUtilisateurs() throws IOException {
        String rolesValides = "DIRECTEUR,RESPONSABLE_RECOUVREMENT,ANALYSTE,AGENT,DSI,CAISSIER,AGENT_CREDIT";
        String[][] colonnes = {
            {"username",                "Identifiant de connexion unique",                     "O"},
            {"email",                   "Adresse email professionnelle",                       "O"},
            {"role",                    "Rôle : " + rolesValides,                              "O"},
            {"zone_id",                 "Zone géographique (optionnel)",                       "N"},
            {"mot_de_passe_provisoire", "Mot de passe initial (min. 8 caractères)",            "O"},
        };
        String[][] exemple = {
            {"marie.analyste", "marie@imf.cm", "ANALYSTE", "ZONE-CENTRE", "Imf@2025!"}
        };
        return buildWorkbook("Utilisateurs", colonnes, exemple);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public ImportResultResponse importerClients(MultipartFile file, Long imfId) throws IOException {
        Imf imf = imfRepo.findById(imfId)
                .orElseThrow(() -> new IllegalArgumentException("IMF introuvable : " + imfId));

        List<String> erreurs = new ArrayList<>();
        int importe = 0, miseAJour = 0, total = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            // Lignes 0=entête, 1=exemple → données à partir de la ligne 2
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || estLigneVide(row)) continue;
                total++;
                try {
                    String clientId    = cellStr(row, 0);
                    String nomComplet  = cellStr(row, 1);
                    String telephone   = cellStr(row, 2);
                    String agentEmail  = cellStr(row, 16);

                    if (clientId.isBlank()) { erreurs.add("Ligne " + (i+1) + " : client_id_externe vide"); continue; }
                    if (nomComplet.isBlank()) { erreurs.add("Ligne " + (i+1) + " : nom_complet vide"); continue; }

                    var existing = clientRepo.findByImfIdAndClientIdExterne(imfId, clientId);
                    if (existing.isPresent()) {
                        ClientInformel c = existing.get();
                        c.setNomComplet(nomComplet);
                        if (!telephone.isBlank()) c.setTelephonePrincipal(telephone);
                        applyClientFields(c, row);
                        clientRepo.save(c);
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
                        clientRepo.save(c);
                        importe++;
                    }
                } catch (Exception e) {
                    erreurs.add("Ligne " + (i+1) + " : " + e.getMessage());
                }
            }
        }
        log.info("Import clients IMF={} : total={} importe={} maj={} erreurs={}", imfId, total, importe, miseAJour, erreurs.size());
        return new ImportResultResponse(total, importe, miseAJour, erreurs.size(), erreurs);
    }

    @Transactional
    public ImportResultResponse importerAgents(MultipartFile file, User currentUser) throws IOException {
        return importerUtilisateurs(file, currentUser, Role.AGENT);
    }

    @Transactional
    public ImportResultResponse importerUtilisateurs(MultipartFile file, User currentUser) throws IOException {
        return importerUtilisateurs(file, currentUser, null);
    }

    @Transactional
    public ImportResultResponse importerAgences(MultipartFile file, User currentUser) throws IOException {
        List<String> erreurs = new ArrayList<>();
        int importe = 0, miseAJour = 0, total = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || estLigneVide(row)) continue;
                total++;
                try {
                    String nom = cellStr(row, 0);
                    if (nom.isBlank()) { erreurs.add("Ligne " + (i+1) + " : nom requis"); continue; }

                    String ville        = cellStr(row, 1);
                    String responsable  = cellStr(row, 2);
                    String telephone    = cellStr(row, 3);

                    var req = new CreateAgenceRequest(nom,
                            ville.isBlank()       ? null : ville,
                            responsable.isBlank() ? null : responsable,
                            telephone.isBlank()   ? null : telephone);
                    adminService.createAgence(req);
                    importe++;
                } catch (Exception e) {
                    erreurs.add("Ligne " + (i+1) + " : " + e.getMessage());
                }
            }
        }
        log.info("Import agences : total={} importe={} erreurs={}", total, importe, erreurs.size());
        return new ImportResultResponse(total, importe, miseAJour, erreurs.size(), erreurs);
    }

    // ── Utilitaire commun agents/utilisateurs ─────────────────────────────────

    private ImportResultResponse importerUtilisateurs(MultipartFile file, User currentUser, Role roleForce) throws IOException {
        // Colonnes : 0=username 1=email 2=role(ou ignoré) 3=zone_id 4=password
        // Pour agents (roleForce=AGENT), la colonne 2 est ignorée
        boolean modeAgent = (roleForce == Role.AGENT);
        int colRole = modeAgent ? -1 : 2;

        List<String> erreurs = new ArrayList<>();
        int importe = 0, miseAJour = 0, total = 0;

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
                    String password = cellStr(row, modeAgent ? 4 : 4);

                    if (username.isBlank()) { erreurs.add("Ligne " + (i+1) + " : username vide"); continue; }
                    if (email.isBlank())    { erreurs.add("Ligne " + (i+1) + " : email vide"); continue; }
                    if (password.isBlank()) { erreurs.add("Ligne " + (i+1) + " : mot_de_passe_provisoire vide"); continue; }

                    Role role = roleForce;
                    if (role == null) {
                        String roleStr = cellStr(row, colRole);
                        try { role = Role.valueOf(roleStr.toUpperCase()); }
                        catch (Exception ex) {
                            erreurs.add("Ligne " + (i+1) + " : rôle invalide '" + roleStr + "'");
                            continue;
                        }
                    }

                    var req = new CreateUserRequest(
                            username, password, email.isBlank() ? null : email,
                            role, zoneId.isBlank() ? null : zoneId, "fr", null, null);
                    adminService.createUser(req);
                    importe++;
                } catch (Exception e) {
                    erreurs.add("Ligne " + (i+1) + " : " + e.getMessage());
                }
            }
        }
        log.info("Import {} : total={} importe={} erreurs={}", modeAgent ? "agents" : "utilisateurs", total, importe, erreurs.size());
        return new ImportResultResponse(total, importe, miseAJour, erreurs.size(), erreurs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITAIRES EXCEL
    // ═══════════════════════════════════════════════════════════════════════════

    private byte[] buildWorkbook(String titre, String[][] colonnes, String[][] exemples) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Import_" + titre);
            sheet.setDefaultColumnWidth(22);

            // Styles
            CellStyle styleEntete = creerStyleEntete(wb);
            CellStyle styleExemple = creerStyleExemple(wb);
            CellStyle styleRequis = creerStyleRequis(wb);

            // Ligne 0 — en-têtes
            Row header = sheet.createRow(0);
            for (int c = 0; c < colonnes.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(colonnes[c][0]);
                cell.setCellStyle(styleEntete);
                sheet.setColumnWidth(c, Math.max(colonnes[c][0].length(), 20) * 280);
            }

            // Ligne 1 — exemples
            if (exemples.length > 0) {
                Row exRow = sheet.createRow(1);
                for (int c = 0; c < exemples[0].length && c < colonnes.length; c++) {
                    Cell cell = exRow.createCell(c);
                    cell.setCellValue(exemples[0][c]);
                    cell.setCellStyle("O".equals(colonnes[c][2]) ? styleRequis : styleExemple);
                }
            }

            // Ligne 2+ — lignes vides pour saisie
            for (int r = 2; r < 52; r++) {
                Row dataRow = sheet.createRow(r);
                for (int c = 0; c < colonnes.length; c++) {
                    dataRow.createCell(c);
                }
            }

            // Onglet "Guide"
            XSSFSheet guide = wb.createSheet("Guide");
            guide.setColumnWidth(0, 10000);
            guide.setColumnWidth(1, 15000);
            guide.setColumnWidth(2, 4000);
            Row gHead = guide.createRow(0);
            gHead.createCell(0).setCellValue("Colonne");
            gHead.createCell(1).setCellValue("Description");
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

    private void applyClientFields(ClientInformel c, Row row) {
        String sexe = cellStr(row, 4);
        if (!sexe.isBlank()) c.setSexe(sexe);
        String dateNaissStr = cellStr(row, 5);
        if (!dateNaissStr.isBlank()) {
            try { c.setDateNaissance(LocalDate.parse(dateNaissStr)); } catch (DateTimeParseException ignored) {}
        }
        String secteur = cellStr(row, 6);
        if (!secteur.isBlank()) c.setSecteurPrincipal(secteur);
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
        String sitFam = cellStr(row, 15);
        if (!sitFam.isBlank()) c.setSituationFamiliale(sitFam);
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
            if (c.getCellType() != CellType.BLANK && !cellStr(row, c.getColumnIndex()).isBlank()) return false;
        }
        return true;
    }
}
