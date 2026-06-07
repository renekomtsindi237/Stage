import { Component, OnInit } from "@angular/core";
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ValidationErrors,
  Validators,
} from "@angular/forms";
import { Router } from "@angular/router";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { MatSnackBar } from "@angular/material/snack-bar";
import { AdminService } from "../admin.service";
import { Role } from "@core/models/auth.model";
import { map, startWith } from "rxjs/operators";
import { Observable } from "rxjs";

/** Rôles qu'un DSI peut assigner (pas SUPER_ADMIN ni DSI). */
const DSI_ASSIGNABLE_ROLES: {
  value: Role;
  label: string;
  icon: string;
  description: string;
}[] = [
  {
    value: "DIRECTEUR",
    label: "Directeur",
    icon: "supervisor_account",
    description: "Supervision globale de l'IMF, accès aux rapports et alertes",
  },
  {
    value: "RESPONSABLE_RECOUVREMENT",
    label: "Resp. Recouvrement",
    icon: "policy",
    description: "Gestion des impayés, relances et dossiers contentieux",
  },
  {
    value: "ANALYSTE",
    label: "Analyste Crédit",
    icon: "analytics",
    description: "Analyse de dossiers, scoring et reporting crédit",
  },
  {
    value: "AGENT",
    label: "Agent de terrain",
    icon: "badge",
    description: "Collecte de remboursements et suivi client en agence",
  },
];

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const pw = group.get("password")?.value;
  const cpw = group.get("confirmPassword")?.value;
  return pw && cpw && pw !== cpw ? { mismatch: true } : null;
}

@Component({
  selector: "imf-create-user",
  templateUrl: "./create-user.component.html",
  styleUrls: ["./create-user.component.scss"],
})
export class CreateUserComponent implements OnInit {
  form: FormGroup;
  loading = false;
  error = "";
  currentStep = 0;

  hidePassword = true;
  hideConfirm = true;

  agences: string[] = [];
  filteredAgences$!: Observable<string[]>;

  // ── GPS ──────────────────────────────────────────────────────────────────
  gpsLoading = false;
  gpsError = "";
  latitude: number | null = null;
  longitude: number | null = null;
  safeMapSrc: SafeResourceUrl | null = null;

  readonly roles = DSI_ASSIGNABLE_ROLES;

  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private router: Router,
    private snackBar: MatSnackBar,
    private sanitizer: DomSanitizer,
  ) {
    this.form = this.fb.group(
      {
        username: [
          "",
          [
            Validators.required,
            Validators.minLength(3),
            Validators.maxLength(50),
          ],
        ],
        password: [
          "",
          [
            Validators.required,
            Validators.minLength(8),
            Validators.maxLength(100),
          ],
        ],
        confirmPassword: ["", Validators.required],
        email: ["", [Validators.email, Validators.maxLength(255)]],
        role: ["", Validators.required],
        zoneId: [""],
      },
      { validators: passwordsMatch },
    );
  }

  ngOnInit(): void {
    this.adminService.listAgenceNoms().subscribe({
      next: (list) => {
        this.agences = list;
        this.filteredAgences$ = this.form.get("zoneId")!.valueChanges.pipe(
          startWith(""),
          map((value) => this._filterAgences(value ?? "")),
        );
      },
      error: () => {
        this.filteredAgences$ = this.form.get("zoneId")!.valueChanges.pipe(
          startWith(""),
          map(() => []),
        );
      },
    });
  }

  private _filterAgences(value: string): string[] {
    const filter = value.toLowerCase();
    return this.agences.filter((a) => a.toLowerCase().includes(filter));
  }

  nextStep(): void {
    if (this.currentStep === 0 && this.step0Valid) {
      this.currentStep = 1;
    } else if (this.currentStep === 1 && this.form.get("role")?.valid) {
      this.currentStep = 2;
    }
  }

  prevStep(): void {
    if (this.currentStep > 0) this.currentStep--;
  }

  get step0Valid(): boolean {
    const u = this.form.get("username");
    const pw = this.form.get("password");
    const cpw = this.form.get("confirmPassword");
    const emailCtrl = this.form.get("email");
    return (
      (u?.valid &&
        pw?.valid &&
        cpw?.valid &&
        !this.form.hasError("mismatch") &&
        (emailCtrl?.valid ?? true)) ??
      false
    );
  }

  get canProceed(): boolean {
    if (this.currentStep === 0) return this.step0Valid;
    if (this.currentStep === 1) return this.form.get("role")?.valid ?? false;
    if (this.currentStep === 2) return true;
    return false;
  }

  // ── GPS ──────────────────────────────────────────────────────────────────

  requestGps(): void {
    if (!navigator.geolocation) {
      this.gpsError = "Géolocalisation non supportée par ce navigateur.";
      return;
    }
    this.gpsLoading = true;
    this.gpsError = "";
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        this.latitude = Math.round(pos.coords.latitude * 1_000_000) / 1_000_000;
        this.longitude =
          Math.round(pos.coords.longitude * 1_000_000) / 1_000_000;
        this.updateMap();
        this.gpsLoading = false;
      },
      (err) => {
        this.gpsError =
          err.code === 1
            ? "Permission refusée. Autorisez la localisation dans votre navigateur."
            : "Impossible d'obtenir la position GPS.";
        this.gpsLoading = false;
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  }

  clearGps(): void {
    this.latitude = null;
    this.longitude = null;
    this.safeMapSrc = null;
    this.gpsError = "";
  }

  private updateMap(): void {
    if (this.latitude == null || this.longitude == null) return;
    const lat = this.latitude;
    const lng = this.longitude;
    const d = 0.012;
    const raw =
      `https://www.openstreetmap.org/export/embed.html` +
      `?bbox=${lng - d},${lat - d},${lng + d},${lat + d}` +
      `&layer=mapnik&marker=${lat},${lng}`;
    this.safeMapSrc = this.sanitizer.bypassSecurityTrustResourceUrl(raw);
  }

  onSubmit(): void {
    if (!this.step0Valid || this.form.get("role")?.invalid || this.loading)
      return;
    this.loading = true;
    this.error = "";

    const val = this.form.value;
    const payload = {
      username: val.username,
      password: val.password,
      email: val.email?.trim() || null,
      role: val.role,
      zoneId: val.zoneId || undefined,
      latitude: this.latitude,
      longitude: this.longitude,
    };

    this.adminService.createUser(payload).subscribe({
      next: () => {
        this.snackBar.open("Compte créé avec succès", "OK", { duration: 4000 });
        this.router.navigate(["/admin"]);
      },
      error: (err) => {
        this.loading = false;
        if (err?.status === 409) {
          this.error = "Cet identifiant est déjà utilisé.";
        } else if (err?.status === 403) {
          this.error = err?.error?.message ?? "Rôle non autorisé.";
        } else {
          this.error =
            "Erreur lors de la création. Vérifiez les données saisies.";
        }
      },
    });
  }

  selectedRoleData() {
    return (
      this.roles.find((r) => r.value === this.form.get("role")?.value) ?? null
    );
  }
}
