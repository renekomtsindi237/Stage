import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";

export interface ScoringClient {
  clientIdExterne: string;
  nomComplet: string;
  agence: string;
  scoreMcrs: number;
  niveauRisque: string;
  probaDefaut30j: number;
  probaDefaut90j: number;
  encours: number;
  cobacClasse: string;
  provisionRequise: number;
  scoredAt: string;
}

export interface TraitementDag {
  dagId: string;
  nomMetier: string;
  statut: "SUCCESS" | "RUNNING" | "FAILED" | "PENDING";
  dureeSecondes: number;
  lignesLues: number;
  lignesEcrites: number;
  lignesRejetees: number;
  derniereExecution: string;
  historiqueStatuts: string[];
}

export interface ModeleInfo {
  version: string;
  psiActuel: number;
  statutDerive: "STABLE" | "ATTENTION" | "DERIVE";
  dernierEntrainement: string;
  featuresContribution: Array<{ nomMetier: string; nomTechnique: string; psi: number; contribution: number }>;
  evolutionPsi: Array<{ date: string; valeur: number }>;
}

const NOM_METIER: Record<string, string> = {
  ingest_core_banking: "Collecte des données bancaires",
  calc_features_mcrs: "Calcul des indicateurs de scoring",
  predict_mcrs_batch: "Calcul des scores clients",
  sync_cobac_reports: "Synchronisation des rapports réglementaires",
  dag_recouvrement: "Traitement des dossiers de recouvrement",
  dag_collecte_epargne: "Traitement des collectes d'épargne",
};

@Injectable({ providedIn: "root" })
export class AnalysteService {
  private readonly API = "/api/v1";

  constructor(private http: HttpClient) {}

  getScoringClients(page = 0, size = 20, niveauRisque?: string): Observable<any> {
    let params = new HttpParams().set("page", page).set("size", size);
    if (niveauRisque) params = params.set("niveauRisque", niveauRisque);
    return this.http.get<any>(`${this.API}/scoring/clients`, { params });
  }

  getModeleInfo(): Observable<ModeleInfo> {
    return this.http.get<ModeleInfo>(`${this.API}/scoring/modele`);
  }

  getTraitements(): Observable<TraitementDag[]> {
    return this.http.get<TraitementDag[]>(`${this.API}/pipeline/status`);
  }

  getNomMetier(dagId: string): string {
    return NOM_METIER[dagId] ?? dagId;
  }
}
