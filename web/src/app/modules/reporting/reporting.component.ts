import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ReportingService } from './reporting.service';
import { formatDate } from '@angular/common';

@Component({
  selector: 'imf-reporting',
  templateUrl: './reporting.component.html',
  styleUrls: ['./reporting.component.scss']
})
export class ReportingComponent {

  form: FormGroup;
  loading: Record<string, boolean> = {};

  constructor(private fb: FormBuilder, private reportingService: ReportingService) {
    const today = new Date();
    const thirtyDaysAgo = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000);

    this.form = this.fb.group({
      dateDebut: [thirtyDaysAgo, Validators.required],
      dateFin: [today, Validators.required],
    });
  }

  private getDateRange(): { dateDebut: string; dateFin: string } {
    return {
      dateDebut: formatDate(this.form.value.dateDebut, 'yyyy-MM-dd', 'en'),
      dateFin: formatDate(this.form.value.dateFin, 'yyyy-MM-dd', 'en'),
    };
  }

  exportCollectesCSV(): void {
    const { dateDebut, dateFin } = this.getDateRange();
    this.loading['collectes-csv'] = true;
    this.reportingService.exportCollectesCSV(dateDebut, dateFin).subscribe({
      next: (blob) => {
        this.reportingService.downloadBlob(blob, `collectes_${dateDebut}_${dateFin}.csv`);
        this.loading['collectes-csv'] = false;
      },
      error: () => { this.loading['collectes-csv'] = false; }
    });
  }

  exportCollectesPDF(): void {
    const { dateDebut, dateFin } = this.getDateRange();
    this.loading['collectes-pdf'] = true;
    this.reportingService.exportCollectesPDF(dateDebut, dateFin).subscribe({
      next: (blob) => {
        this.reportingService.downloadBlob(blob, `collectes_${dateDebut}_${dateFin}.pdf`);
        this.loading['collectes-pdf'] = false;
      },
      error: () => { this.loading['collectes-pdf'] = false; }
    });
  }

  exportPretsCSV(): void {
    this.loading['prets-csv'] = true;
    this.reportingService.exportPretsEnRetardCSV().subscribe({
      next: (blob) => {
        this.reportingService.downloadBlob(blob, 'prets_en_retard.csv');
        this.loading['prets-csv'] = false;
      },
      error: () => { this.loading['prets-csv'] = false; }
    });
  }

  exportPretsPDF(): void {
    this.loading['prets-pdf'] = true;
    this.reportingService.exportPretsEnRetardPDF().subscribe({
      next: (blob) => {
        this.reportingService.downloadBlob(blob, 'prets_en_retard.pdf');
        this.loading['prets-pdf'] = false;
      },
      error: () => { this.loading['prets-pdf'] = false; }
    });
  }

  exportKpiPDF(): void {
    const { dateDebut, dateFin } = this.getDateRange();
    this.loading['kpi-pdf'] = true;
    this.reportingService.exportKpiRapportPDF(dateDebut, dateFin).subscribe({
      next: (blob) => {
        this.reportingService.downloadBlob(blob, `rapport_kpi_${dateDebut}_${dateFin}.pdf`);
        this.loading['kpi-pdf'] = false;
      },
      error: () => { this.loading['kpi-pdf'] = false; }
    });
  }

  isLoading(key: string): boolean {
    return !!this.loading[key];
  }

  setQuickFilter(days: number): void {
    const today = new Date();
    const startDate = new Date(today.getTime() - days * 24 * 60 * 60 * 1000);
    this.form.patchValue({
      dateDebut: startDate,
      dateFin: today
    });
  }
}
