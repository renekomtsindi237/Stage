import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { HttpClient } from '@angular/common/http';
import { ApiResponse } from '@core/models/api-response.model';
import { trigger, transition, style, animate } from '@angular/animations';

function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const np = group.get('newPassword')?.value;
  const cp = group.get('confirmPassword')?.value;
  return np && cp && np !== cp ? { mismatch: true } : null;
}

@Component({
  selector: 'imf-change-password',
  templateUrl: './change-password.component.html',
  styleUrls: ['./change-password.component.scss'],
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(-10px)' }),
        animate('200ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ])
  ]
})
export class ChangePasswordComponent {

  form: FormGroup;
  loading = false;
  success = false;
  error = '';
  hideCurrent = true;
  hideNew = true;
  hideConfirm = true;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    public dialogRef: MatDialogRef<ChangePasswordComponent>,
  ) {
    this.form = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    }, { validators: passwordMatchValidator });
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading) return;
    this.loading = true;
    this.error = '';

    const { currentPassword, newPassword } = this.form.value;
    this.http.patch<ApiResponse<void>>('/api/users/me/password', { currentPassword, newPassword })
      .subscribe({
        next: () => {
          this.loading = false;
          this.success = true;
          setTimeout(() => this.dialogRef.close(true), 1800);
        },
        error: (err) => {
          this.loading = false;
          if (err?.status === 400) {
            this.error = 'Mot de passe actuel incorrect.';
          } else if (err?.status === 403) {
            this.error = err?.error?.message ?? 'Opération non autorisée.';
          } else {
            this.error = 'Une erreur est survenue. Veuillez réessayer.';
          }
        }
      });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }

  get newPwStrength(): number {
    const pw: string = this.form.get('newPassword')?.value ?? '';
    let score = 0;
    if (pw.length >= 8)          score++;
    if (/[A-Z]/.test(pw))        score++;
    if (/[0-9]/.test(pw))        score++;
    if (/[^A-Za-z0-9]/.test(pw)) score++;
    return score;
  }

  get strengthLabel(): string {
    const labels = ['', 'Faible', 'Moyen', 'Fort', 'Très fort'];
    return labels[this.newPwStrength] ?? '';
  }

  get strengthClass(): string {
    const classes = ['', 'weak', 'medium', 'strong', 'very-strong'];
    return classes[this.newPwStrength] ?? '';
  }
}
