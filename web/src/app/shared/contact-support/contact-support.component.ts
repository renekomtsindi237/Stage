import { Component } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { TicketService } from "@core/services/ticket.service";
import { AuthService } from "@core/services/auth.service";

@Component({
  selector: "imf-contact-support",
  templateUrl: "./contact-support.component.html",
  styleUrls: ["./contact-support.component.scss"],
})
export class ContactSupportComponent {
  constructor(
    private dialog: MatDialog,
    public auth: AuthService,
  ) {}

  ouvrirDialog(): void {
    this.dialog.open(ContactSupportDialogComponent, {
      width: "560px",
      panelClass: "support-dialog-panel",
      disableClose: false,
    });
  }
}

@Component({
  selector: "imf-contact-support-dialog",
  template: `
    <div class="sd-wrap">
      <!-- ── Header gradient ── -->
      <div class="sd-header">
        <div class="sd-header-inner">
          <div class="sd-header-icon">
            <mat-icon>support_agent</mat-icon>
          </div>
          <div class="sd-header-text">
            <h2 class="sd-title">Contacter le support</h2>
            <p class="sd-subtitle">
              Notre équipe vous répond sous 24 h ouvrées
            </p>
          </div>
        </div>
        <button mat-icon-button class="sd-close" (click)="fermer()">
          <mat-icon>close</mat-icon>
        </button>
      </div>

      <!-- ── État succès ── -->
      <div class="sd-success" *ngIf="submitted">
        <div class="success-circle">
          <mat-icon>check</mat-icon>
        </div>
        <h3>Ticket envoyé !</h3>
        <p>Votre demande a bien été transmise à l'équipe support.</p>
        <div class="success-ref">
          Référence&nbsp;: <strong>#{{ ticketId }}</strong>
        </div>
        <p class="success-note">Vous recevrez une confirmation par email.</p>
        <button class="sd-btn-submit" (click)="fermer()">Fermer</button>
      </div>

      <!-- ── Formulaire ── -->
      <form
        [formGroup]="form"
        (ngSubmit)="envoyer()"
        class="sd-body"
        *ngIf="!submitted"
      >
        <!-- Titre -->
        <div class="sd-field-group">
          <label class="sd-label"
            >Titre du problème <span class="sd-req">*</span></label
          >
          <div
            class="sd-input-wrap"
            [class.sd-input-focused]="titreFocused"
            [class.sd-input-error]="
              form.get('titre')?.invalid && form.get('titre')?.touched
            "
          >
            <mat-icon class="sd-input-icon">title</mat-icon>
            <input
              class="sd-input"
              formControlName="titre"
              maxlength="200"
              placeholder="Ex : Impossible de se connecter"
              (focus)="titreFocused = true"
              (blur)="titreFocused = false"
            />
            <span class="sd-char-count"
              >{{ form.get("titre")?.value?.length ?? 0 }}/200</span
            >
          </div>
          <span
            class="sd-error-msg"
            *ngIf="
              form.get('titre')?.hasError('required') &&
              form.get('titre')?.touched
            "
            >Titre requis</span
          >
        </div>

        <!-- Catégorie + Priorité -->
        <div class="sd-row-2">
          <div class="sd-field-group">
            <label class="sd-label"
              >Catégorie <span class="sd-req">*</span></label
            >
            <div class="sd-select-wrap">
              <mat-icon class="sd-input-icon">category</mat-icon>
              <select class="sd-select" formControlName="categorie">
                <option value="BUG_TECHNIQUE">Bogue technique</option>
                <option value="QUESTION_FONCTIONNELLE">
                  ❓ Question fonctionnelle
                </option>
                <option value="DEMANDE_ACCES">Demande d'accès</option>
                <option value="PERFORMANCE">Performance</option>
                <option value="AUTRE">Autre</option>
              </select>
              <mat-icon class="sd-caret">expand_more</mat-icon>
            </div>
          </div>

          <div class="sd-field-group">
            <label class="sd-label"
              >Priorité <span class="sd-req">*</span></label
            >
            <div class="sd-priority-pills">
              <button
                type="button"
                class="priority-pill"
                *ngFor="let p of priorities"
                [class.active]="form.get('priorite')?.value === p.value"
                [class]="
                  'priority-pill priority-pill--' +
                  p.color +
                  (form.get('priorite')?.value === p.value ? ' active' : '')
                "
                (click)="form.get('priorite')?.setValue(p.value)"
              >
                <span class="priority-dot"></span>
                {{ p.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- Description -->
        <div class="sd-field-group">
          <label class="sd-label"
            >Description <span class="sd-req">*</span></label
          >
          <div
            class="sd-textarea-wrap"
            [class.sd-input-focused]="descFocused"
            [class.sd-input-error]="
              form.get('description')?.invalid &&
              form.get('description')?.touched
            "
          >
            <textarea
              class="sd-textarea"
              formControlName="description"
              rows="4"
              placeholder="Décrivez le problème, les étapes pour le reproduire et ce que vous observez…"
              (focus)="descFocused = true"
              (blur)="descFocused = false"
            ></textarea>
          </div>
          <span
            class="sd-error-msg"
            *ngIf="
              form.get('description')?.hasError('required') &&
              form.get('description')?.touched
            "
            >Description requise</span
          >
          <span
            class="sd-error-msg"
            *ngIf="
              form.get('description')?.hasError('minlength') &&
              form.get('description')?.touched
            "
            >Minimum 10 caractères</span
          >
        </div>

        <!-- Séparateur coordonnées -->
        <div class="sd-section-sep">
          <span class="sd-section-label">Coordonnées de contact</span>
        </div>

        <!-- Email -->
        <div class="sd-field-group">
          <label class="sd-label"
            >Email de contact <span class="sd-req">*</span></label
          >
          <div
            class="sd-input-wrap"
            [class.sd-input-error]="
              form.get('emailContact')?.invalid &&
              form.get('emailContact')?.touched
            "
          >
            <mat-icon class="sd-input-icon">email</mat-icon>
            <input
              class="sd-input"
              formControlName="emailContact"
              type="email"
              placeholder="votre@email.com"
            />
          </div>
          <span
            class="sd-error-msg"
            *ngIf="
              form.get('emailContact')?.hasError('email') &&
              form.get('emailContact')?.touched
            "
            >Adresse email invalide</span
          >
          <span class="sd-hint"
            >Utilisé pour recevoir la réponse du support</span
          >
        </div>

        <!-- Option copie email -->
        <label class="sd-checkbox-row">
          <input
            type="checkbox"
            formControlName="envoyerCopie"
            class="sd-checkbox"
          />
          <div class="sd-checkbox-content">
            <mat-icon class="sd-checkbox-icon">mail_outline</mat-icon>
            <div>
              <span class="sd-checkbox-label"
                >Envoyer aussi un email à
                <strong>support&#64;microrecouv.cm</strong></span
              >
              <span class="sd-checkbox-sub"
                >Ouvrira votre client email avec le message pré-rempli</span
              >
            </div>
          </div>
        </label>

        <!-- Bannière erreur -->
        <div class="sd-error-banner" *ngIf="error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error }}</span>
        </div>

        <!-- Actions -->
        <div class="sd-actions">
          <button
            type="button"
            class="sd-btn-cancel"
            (click)="fermer()"
            [disabled]="loading"
          >
            Annuler
          </button>
          <button
            type="submit"
            class="sd-btn-submit"
            [disabled]="form.invalid || loading"
          >
            <mat-spinner diameter="16" *ngIf="loading"></mat-spinner>
            <mat-icon *ngIf="!loading">send</mat-icon>
            {{ loading ? "Envoi…" : "Envoyer le ticket" }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [
    `
      :host {
        font-family:
          -apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif;
        display: flex;
        flex-direction: column;
        max-height: 88vh;
        overflow: hidden;
      }

      .sd-wrap {
        display: flex;
        flex-direction: column;
        flex: 1;
        overflow: hidden;
        border-radius: 16px;
      }

      /* ── Header gradient ── */
      .sd-header {
        background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
        padding: 20px 20px 18px;
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        flex-shrink: 0;
      }
      .sd-header-inner {
        display: flex;
        align-items: center;
        gap: 14px;
      }
      .sd-header-icon {
        width: 46px;
        height: 46px;
        border-radius: 13px;
        flex-shrink: 0;
        background: rgba(255, 255, 255, 0.12);
        border: 1px solid rgba(255, 255, 255, 0.18);
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .sd-header-icon mat-icon {
        color: #fff;
        font-size: 24px;
        width: 24px;
        height: 24px;
      }
      .sd-title {
        margin: 0 0 3px;
        font-size: 17px;
        font-weight: 700;
        color: #fff;
      }
      .sd-subtitle {
        margin: 0;
        font-size: 12px;
        color: rgba(255, 255, 255, 0.6);
      }
      .sd-close {
        color: rgba(255, 255, 255, 0.5) !important;
        background: rgba(255, 255, 255, 0.08) !important;
        border-radius: 8px !important;
        width: 32px !important;
        height: 32px !important;
        &:hover {
          color: #fff !important;
          background: rgba(255, 255, 255, 0.16) !important;
        }
        mat-icon {
          font-size: 18px;
        }
      }

      /* ── Body scrollable ── */
      .sd-body {
        padding: 20px 22px 22px;
        display: flex;
        flex-direction: column;
        gap: 16px;
        overflow-y: auto;
        flex: 1;
        min-height: 0;
        scrollbar-width: thin;
        scrollbar-color: #e2e8f0 transparent;
        &::-webkit-scrollbar {
          width: 5px;
        }
        &::-webkit-scrollbar-track {
          background: transparent;
        }
        &::-webkit-scrollbar-thumb {
          background: #e2e8f0;
          border-radius: 4px;
        }
      }

      /* ── Champs ── */
      .sd-field-group {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .sd-label {
        font-size: 13px;
        font-weight: 600;
        color: #374151;
      }
      .sd-req {
        color: #ef4444;
        margin-left: 2px;
      }
      .sd-error-msg {
        font-size: 12px;
        color: #ef4444;
        font-weight: 500;
      }

      .sd-input-wrap,
      .sd-textarea-wrap {
        display: flex;
        align-items: center;
        border: 1.5px solid #e2e8f0;
        border-radius: 10px;
        background: #f8fafc;
        transition:
          border-color 0.18s,
          box-shadow 0.18s,
          background 0.18s;
      }
      .sd-input-focused {
        border-color: #1e293b !important;
        box-shadow: 0 0 0 3px rgba(30, 41, 59, 0.08) !important;
        background: #fff !important;
      }
      .sd-input-error {
        border-color: #ef4444 !important;
        box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.07) !important;
      }

      .sd-input-icon {
        color: #94a3b8;
        font-size: 17px;
        width: 17px;
        height: 17px;
        margin-left: 12px;
        flex-shrink: 0;
      }

      .sd-input {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        padding: 11px 12px;
        font-size: 14px;
        font-family: inherit;
        color: #0f172a;
        border-radius: 0 10px 10px 0;
        &::placeholder {
          color: #94a3b8;
        }
      }
      .sd-char-count {
        font-size: 11px;
        color: #9ca3af;
        padding-right: 10px;
        white-space: nowrap;
        flex-shrink: 0;
      }

      /* Select natif */
      .sd-select-wrap {
        display: flex;
        align-items: center;
        position: relative;
        border: 1.5px solid #e2e8f0;
        border-radius: 10px;
        background: #f8fafc;
        transition:
          border-color 0.18s,
          box-shadow 0.18s,
          background 0.18s;
        &:focus-within {
          border-color: #1e293b;
          box-shadow: 0 0 0 3px rgba(30, 41, 59, 0.08);
          background: #fff;
        }
      }
      .sd-select {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        padding: 11px 36px 11px 40px;
        font-size: 14px;
        font-family: inherit;
        color: #0f172a;
        appearance: none;
        cursor: pointer;
        border-radius: 10px;
      }
      .sd-caret {
        position: absolute;
        right: 10px;
        color: #94a3b8;
        font-size: 18px;
        pointer-events: none;
        flex-shrink: 0;
      }

      .sd-textarea-wrap {
        align-items: flex-start;
      }
      .sd-textarea {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        padding: 11px 12px;
        font-size: 14px;
        font-family: inherit;
        color: #0f172a;
        resize: vertical;
        min-height: 90px;
        border-radius: 10px;
        &::placeholder {
          color: #94a3b8;
        }
      }

      /* ── Grille 2 colonnes ── */
      .sd-row-2 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 14px;
      }

      /* ── Priority pills ── */
      .sd-priority-pills {
        display: flex;
        gap: 6px;
        flex-wrap: wrap;
      }
      .priority-pill {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        padding: 6px 12px;
        border-radius: 20px;
        border: 1.5px solid #e2e8f0;
        background: #f8fafc;
        font-size: 12px;
        font-weight: 600;
        font-family: inherit;
        color: #64748b;
        cursor: pointer;
        transition: all 0.15s ease;
        .priority-dot {
          width: 7px;
          height: 7px;
          border-radius: 50%;
          background: currentColor;
          flex-shrink: 0;
        }
        &:hover {
          border-color: #94a3b8;
        }
      }
      .priority-pill--green {
        &.active {
          background: #f0fdf4;
          border-color: #22c55e;
          color: #16a34a;
        }
      }
      .priority-pill--blue {
        &.active {
          background: #eff6ff;
          border-color: #3b82f6;
          color: #2563eb;
        }
      }
      .priority-pill--orange {
        &.active {
          background: #fff7ed;
          border-color: #f97316;
          color: #ea580c;
        }
      }
      .priority-pill--red {
        &.active {
          background: #fef2f2;
          border-color: #ef4444;
          color: #dc2626;
        }
      }

      /* ── Séparateur de section ── */
      .sd-section-sep {
        display: flex;
        align-items: center;
        gap: 10px;
        margin: 2px 0;
        &::before,
        &::after {
          content: "";
          flex: 1;
          height: 1px;
          background: #e2e8f0;
        }
      }
      .sd-section-label {
        font-size: 11px;
        font-weight: 700;
        color: #94a3b8;
        text-transform: uppercase;
        letter-spacing: 0.8px;
        white-space: nowrap;
      }

      /* ── Hint / Opt ── */
      .sd-hint {
        font-size: 11px;
        color: #94a3b8;
        margin-top: 2px;
      }
      .sd-opt {
        font-size: 11px;
        color: #94a3b8;
        font-weight: 400;
        margin-left: 4px;
      }

      /* ── Checkbox envoi email ── */
      .sd-checkbox-row {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        padding: 12px 14px;
        border-radius: 10px;
        border: 1.5px solid #e2e8f0;
        background: #f8fafc;
        cursor: pointer;
        transition:
          border-color 0.15s,
          background 0.15s;
        &:has(.sd-checkbox:checked) {
          border-color: #1e293b;
          background: #f1f5f9;
        }
        &:hover {
          border-color: #94a3b8;
        }
      }
      .sd-checkbox {
        width: 16px;
        height: 16px;
        margin-top: 2px;
        flex-shrink: 0;
        accent-color: #1e293b;
        cursor: pointer;
      }
      .sd-checkbox-content {
        display: flex;
        align-items: flex-start;
        gap: 10px;
      }
      .sd-checkbox-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #64748b;
        margin-top: 1px;
        flex-shrink: 0;
      }
      .sd-checkbox-label {
        font-size: 13px;
        font-weight: 500;
        color: #374151;
        display: block;
      }
      .sd-checkbox-sub {
        font-size: 11px;
        color: #94a3b8;
        display: block;
        margin-top: 2px;
      }

      /* ── Bannières ── */
      .sd-error-banner {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        padding: 11px 14px;
        border-radius: 10px;
        font-size: 13px;
        line-height: 1.5;
        background: #fef2f2;
        color: #dc2626;
        border: 1px solid #fecaca;
        mat-icon {
          font-size: 18px;
          width: 18px;
          height: 18px;
          flex-shrink: 0;
          margin-top: 1px;
          color: #ef4444;
        }
      }

      /* ── Actions ── */
      .sd-actions {
        display: flex;
        justify-content: flex-end;
        align-items: center;
        gap: 10px;
        padding-top: 4px;
      }
      .sd-btn-cancel {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 14px;
        font-weight: 600;
        color: #64748b;
        font-family: inherit;
        padding: 8px 16px;
        border-radius: 8px;
        transition: background 0.15s;
        &:hover:not(:disabled) {
          background: #f1f5f9;
        }
        &:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
      }
      .sd-btn-submit {
        display: inline-flex;
        align-items: center;
        gap: 7px;
        border: none;
        border-radius: 10px;
        padding: 10px 20px;
        background: #1e293b;
        color: #fff;
        font-size: 14px;
        font-weight: 700;
        font-family: inherit;
        cursor: pointer;
        transition:
          background 0.18s,
          box-shadow 0.18s;
        box-shadow: 0 2px 8px rgba(30, 41, 59, 0.25);
        mat-icon {
          font-size: 17px;
          width: 17px;
          height: 17px;
        }
        mat-spinner {
          --mdc-circular-progress-active-indicator-color: #fff;
        }
        &:hover:not(:disabled) {
          background: #0f172a;
          box-shadow: 0 4px 14px rgba(30, 41, 59, 0.35);
        }
        &:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
      }

      /* ── Succès ── */
      .sd-success {
        padding: 44px 24px 36px;
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 10px;
      }
      .success-circle {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        background: linear-gradient(135deg, #22c55e, #16a34a);
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 8px 24px rgba(34, 197, 94, 0.3);
        mat-icon {
          color: #fff;
          font-size: 34px;
          width: 34px;
          height: 34px;
        }
      }
      .sd-success h3 {
        font-size: 20px;
        font-weight: 800;
        color: #0f172a;
        margin: 8px 0 0;
      }
      .sd-success p {
        color: #475569;
        margin: 0;
        font-size: 14px;
      }
      .success-ref {
        background: #f1f5f9;
        border-radius: 8px;
        padding: 8px 16px;
        font-size: 14px;
        color: #374151;
        strong {
          color: #2563eb;
          font-size: 15px;
        }
      }
      .success-note {
        font-size: 12px !important;
        color: #94a3b8 !important;
      }
      .sd-success .sd-btn-submit {
        margin-top: 10px;
      }
    `,
  ],
})
export class ContactSupportDialogComponent {
  form: FormGroup;
  loading = false;
  submitted = false;
  error = "";
  ticketId = 0;
  titreFocused = false;
  descFocused = false;

  readonly priorities = [
    { value: "BASSE", label: "Basse", color: "green" },
    { value: "NORMALE", label: "Normale", color: "blue" },
    { value: "HAUTE", label: "Haute", color: "orange" },
    { value: "CRITIQUE", label: "Critique", color: "red" },
  ];

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private ticketService: TicketService,
    private snack: MatSnackBar,
    public dialogRef: MatDialogRef<ContactSupportDialogComponent>,
  ) {
    this.form = this.fb.group({
      titre: ["", [Validators.required, Validators.maxLength(200)]],
      description: ["", [Validators.required, Validators.minLength(10)]],
      categorie: ["BUG_TECHNIQUE", Validators.required],
      priorite: ["NORMALE", Validators.required],
      emailContact: [
        this.auth.getUsername() ?? "",
        [Validators.required, Validators.email],
      ],
      envoyerCopie: [true],
    });
  }

  envoyer(): void {
    if (this.form.invalid || this.loading) return;
    this.loading = true;
    this.error = "";

    const { envoyerCopie, emailContact, ...ticketData } = this.form.value;
    const payload = { ...ticketData, emailContact };

    this.ticketService.creer(payload).subscribe({
      next: (res) => {
        this.loading = false;
        this.submitted = true;
        this.ticketId = res.data?.id ?? 0;
        if (envoyerCopie && emailContact) {
          const subject = encodeURIComponent(`[Support] ${ticketData.titre}`);
          const body = encodeURIComponent(
            `Bonjour,\n\nJe souhaite signaler le problème suivant :\n\n${ticketData.description}\n\nCatégorie : ${ticketData.categorie}\nPriorité : ${ticketData.priorite}\n\nCordialement,\n${this.auth.getUsername()}`,
          );
          window.open(
            `mailto:support@microrecouv.cm?subject=${subject}&body=${body}`,
            "_blank",
          );
        }
        this.snack.open("Ticket envoyé avec succès", "Fermer", {
          duration: 4000,
        });
        setTimeout(() => this.dialogRef.close(true), 2500);
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err?.error?.message ?? "Erreur lors de l'envoi. Veuillez réessayer.";
      },
    });
  }

  fermer(): void {
    this.dialogRef.close(false);
  }
}
