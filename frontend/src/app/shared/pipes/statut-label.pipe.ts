import { Pipe, PipeTransform, inject } from "@angular/core";
import { TranslateService } from "@ngx-translate/core";

const KNOWN_KEYS: Record<string, string> = {
  RELANCE_AMIABLE: "common.phase_relance_amiable",
  MEDIATION_AMIABLE: "common.phase_mediation_amiable",
  MISE_EN_DEMEURE: "common.phase_mise_en_demeure",
  CONTENTIEUX: "common.phase_contentieux",
  REECHELONNEMENT: "common.phase_reechelonnement",
  PERTE: "common.phase_perte",
  APPEL_TELEPHONIQUE: "rec_actions.type_appel",
  SMS_RELANCE: "rec_actions.type_sms",
  EMAIL_RELANCE: "rec_actions.type_email",
  VISITE_TERRAIN: "rec_actions.type_visite",
  MEDIATION_CHEF_QUARTIER: "rec_actions.type_med_quartier",
  MEDIATION_FAMILLE: "rec_actions.type_med_famille",
  CONTACT_CAUTION: "rec_actions.type_contact_caution",
  SAISIE_GARANTIE: "rec_actions.type_saisie",
  MISE_EN_DEMEURE_LETTRE: "rec_actions.type_mise_en_demeure",
  INTERVENTION_HUISSIER: "rec_actions.type_huissier",
  COMITE_RECOUVREMENT: "rec_actions.type_comite",
  ASSIGNATION_TRIBUNAL: "rec_actions.type_assignation",
  ENCAISSEMENT_PARTIEL: "rec_actions.type_encaissement_partiel",
  ENCAISSEMENT_TOTAL: "rec_actions.type_encaissement_total",
  ACCORD_REECHELONNEMENT: "rec_actions.type_accord",
  CESSION_CREANCE: "rec_actions.type_cession",
  RADIATION: "rec_actions.type_radiation",
  EN_ATTENTE: "rec_actions.res_attente",
  CONTACT_ETABLI: "rec_actions.res_contacte",
  SANS_REPONSE: "rec_actions.res_absent",
  REFUSE: "rec_actions.res_refuse",
  PROMESSE_PAIEMENT: "rec_actions.res_promesse",
  PAIEMENT_PARTIEL: "rec_actions.res_partiel",
  PAIEMENT_EFFECTUE: "rec_actions.res_total",
  ACCORD_OBTENU: "rec_actions.res_accord",
  OUVERT: "sup_tickets.tab_OUVERT",
  EN_COURS: "sup_tickets.tab_EN_COURS",
  RESOLU: "sup_tickets.tab_RESOLU",
  FERME: "sup_tickets.tab_FERME",
  BASSE: "statut.BASSE",
  NORMALE: "statut.NORMALE",
  HAUTE: "statut.HAUTE",
  CRITIQUE: "statut.CRITIQUE",
  ESPECES: "caisse.canal_especes",
  MTN: "rec_actions.canal_mtn",
  ORANGE: "rec_actions.canal_orange",
  VIREMENT: "caisse.canal_virement",
  MOBILE_MONEY: "caisse.canal_mobile_money",
  CHEQUE: "caisse.canal_cheque",
};

@Pipe({ name: "statutLabel", standalone: true })
export class StatutLabelPipe implements PipeTransform {
  private readonly i18n = inject(TranslateService);

  transform(code: string | null | undefined): string {
    if (!code) return "—";
    const mapped = KNOWN_KEYS[code];
    if (mapped) {
      const t = this.i18n.instant(mapped);
      if (t !== mapped) return t;
    }
    const key = "statut." + code;
    const t = this.i18n.instant(key);
    if (t !== key) return t;
    return code.replace(/_/g, " ");
  }
}
