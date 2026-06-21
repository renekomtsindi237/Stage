package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.ImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IClientImportService {

    /**
     * Importe les clients depuis un fichier CSV.
     * Le fichier doit utiliser le séparateur ';' et l'encodage UTF-8.
     * Chaque ligne contient les données client + indicateurs KPI historiques N-1.
     *
     * @param fichier  fichier CSV uploadé
     * @param imfCode  code de l'IMF cible (ex: "FINANCE") — ignoré si agent_email résout l'IMF
     */
    ImportResultResponse importerDepuisCsv(MultipartFile fichier, String imfCode);

    /**
     * Retourne le template CSV vide avec en-têtes + 2 lignes d'exemple commentées.
     */
    String genererTemplateCsv();

    /**
     * Exporte la liste des clients gérés par un agent (identifié par email)
     * avec leurs indicateurs KPI historiques, au format CSV.
     */
    String exporterClientsAgent(String agentEmail, String imfCode);
}
