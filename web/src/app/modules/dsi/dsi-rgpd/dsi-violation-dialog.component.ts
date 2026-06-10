import { Component } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MatDialogRef } from "@angular/material/dialog";
import { DsiService } from "../dsi.service";
import { MatSnackBar } from "@angular/material/snack-bar";

@Component({
  selector: "imf-dsi-violation-dialog",
  template: `
    <h2 mat-dialog-title>Déclarer une violation de données</h2>
    <mat-dialog-content>
      <div class="sla-banner">
        <mat-icon>schedule</mat-icon>
        <span
          >Délai réglementaire RGPD : <strong>72 heures</strong> pour notifier
          l'autorité compétente</span
        >
      </div>
      <form [formGroup]="form" class="viol-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Type de violation</mat-label>
          <mat-select formControlName="typeViolation">
            <mat-option value="ACCES_NON_AUTORISE"
              >Accès non autorisé à des données personnelles</mat-option
            >
            <mat-option value="DIVULGATION_NON_AUTORISEE"
              >Divulgation non autorisée</mat-option
            >
            <mat-option value="PERTE_DONNEES"
              >Perte ou destruction de données</mat-option
            >
            <mat-option value="MODIFICATION_ACCIDENTELLE"
              >Modification accidentelle</mat-option
            >
            <mat-option value="AUTRE">Autre incident de sécurité</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Sévérité</mat-label>
          <mat-select formControlName="severite">
            <mat-option value="CRITIQUE"
              >Critique — risque élevé pour les personnes concernées</mat-option
            >
            <mat-option value="MAJEURE">Majeure — risque modéré</mat-option>
            <mat-option value="MINEURE"
              >Mineure — risque faible ou nul</mat-option
            >
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nombre de personnes concernées (estimation)</mat-label>
          <input
            matInput
            type="number"
            formControlName="nbPersonnesConcernees"
            min="1"
          />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description de l'incident</mat-label>
          <textarea
            matInput
            formControlName="description"
            rows="4"
            placeholder="Décrivez les circonstances, les données impactées et les mesures prises..."
          ></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Mesures de remédiation engagées</mat-label>
          <textarea
            matInput
            formControlName="mesuresRemediation"
            rows="3"
            placeholder="Ex: accès révoqué, mot de passe réinitialisé, patch appliqué..."
          ></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button
        mat-flat-button
        color="warn"
        [disabled]="form.invalid || saving"
        (click)="soumettre()"
      >
        <mat-icon>send</mat-icon> Déclarer la violation
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .sla-banner {
        display: flex;
        align-items: center;
        gap: 10px;
        background: #fef2f2;
        border: 1px solid #fecaca;
        border-radius: 8px;
        padding: 12px 16px;
        margin-bottom: 20px;
        color: #991b1b;
        font-size: 0.875rem;
        mat-icon {
          color: #dc2626;
          flex-shrink: 0;
        }
      }
      .viol-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .full-width {
        width: 100%;
      }
    `,
  ],
})
export class DsiViolationDialogComponent {
  form: FormGroup;
  saving = false;

  constructor(
    private fb: FormBuilder,
    private dsi: DsiService,
    private snack: MatSnackBar,
    private ref: MatDialogRef<DsiViolationDialogComponent>,
  ) {
    this.form = this.fb.group({
      typeViolation: ["", Validators.required],
      severite: ["", Validators.required],
      nbPersonnesConcernees: [1, [Validators.required, Validators.min(1)]],
      description: ["", [Validators.required, Validators.minLength(20)]],
      mesuresRemediation: ["", Validators.required],
    });
  }

  soumettre(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.dsi.declarerViolation(this.form.value).subscribe({
      next: () => {
        this.snack.open(
          "Violation déclarée — le SLA de 72h est en cours",
          "Fermer",
          { duration: 4000 },
        );
        this.ref.close(true);
      },
      error: () => {
        this.saving = false;
        this.snack.open("Erreur lors de la déclaration", "Fermer", {
          duration: 3000,
        });
      },
    });
  }
}
