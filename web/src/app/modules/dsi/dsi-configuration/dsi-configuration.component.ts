import { Component, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MatSnackBar } from "@angular/material/snack-bar";
import { DsiService } from "../dsi.service";
import { AuthService } from "../../../core/services/auth.service";

@Component({
  selector: "imf-dsi-configuration",
  templateUrl: "./dsi-configuration.component.html",
  styleUrls: ["./dsi-configuration.component.scss"],
})
export class DsiConfigurationComponent implements OnInit {
  form!: FormGroup;
  saving = false;
  logoPreview: string | null = null;
  logoFile: File | null = null;

  constructor(
    private fb: FormBuilder,
    private dsi: DsiService,
    private auth: AuthService,
    private snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      nomImf: ["", [Validators.required, Validators.maxLength(100)]],
      codeImf: [{ value: "", disabled: true }],
      adresse: [""],
      telephone: [""],
      email: ["", Validators.email],
      siteWeb: [""],
      alertesEmail: [true],
      alertesSms: [false],
      delaiRelance: [7, [Validators.required, Validators.min(1), Validators.max(30)]],
      delaiMiseEnDemeure: [30, [Validators.required, Validators.min(7), Validators.max(90)]],
      tauxProvisionDouteuse: [50],
      tauxProvisionPerdue: [100],
    });

    const code = this.auth.getImfCode();
    if (code) {
      this.form.patchValue({ codeImf: code });
      const logo = this.auth.getImfLogo(code);
      if (logo) this.logoPreview = logo;
    }

    this.dsi.getConfiguration().subscribe({
      next: cfg => this.form.patchValue(cfg),
      error: () => {},
    });
  }

  onLogoChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      this.snack.open("Le logo ne doit pas dépasser 2 Mo", "Fermer", { duration: 3000 });
      return;
    }
    this.logoFile = file;
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      this.logoPreview = dataUrl;
      const code = this.auth.getImfCode();
      if (code) {
        this.auth.setImfLogo(code, dataUrl);
        this.snack.open("Logo mis à jour pour tous les acteurs de l'IMF", "Fermer", { duration: 3500 });
      }
    };
    reader.readAsDataURL(file);
  }

  supprimerLogo(): void {
    const code = this.auth.getImfCode();
    if (code) {
      localStorage.removeItem(`imf_logo_${code}`);
      this.logoPreview = null;
      this.logoFile = null;
      this.snack.open("Logo supprimé", "Fermer", { duration: 2500 });
    }
  }

  sauvegarder(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.dsi.saveConfiguration(this.form.getRawValue()).subscribe({
      next: () => {
        this.saving = false;
        this.snack.open("Configuration sauvegardée avec succès", "Fermer", { duration: 3000 });
      },
      error: () => {
        this.saving = false;
        this.snack.open("Erreur lors de la sauvegarde", "Fermer", { duration: 3000 });
      },
    });
  }
}
