import {
  Component,
  Input,
  OnChanges,
  ViewChild,
  ElementRef,
  OnDestroy,
  NgZone,
} from "@angular/core";
import { Chart, DoughnutController, ArcElement, Tooltip } from "chart.js";

Chart.register(DoughnutController, ArcElement, Tooltip);

@Component({
  selector: "imf-platform-donut-chart",
  template: ` <div class="cw">
    <canvas #c></canvas>
    <div class="center" *ngIf="total > 0">
      <span class="val">{{ total }}</span>
      <span class="lbl">Total</span>
    </div>
  </div>`,
  styles: [
    `
      .cw {
        position: relative;
        height: 180px;
        width: 180px;
        margin: 0 auto;
      }
      .center {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        text-align: center;
        pointer-events: none;
      }
      .val {
        display: block;
        font-size: 1.6rem;
        font-weight: 900;
        color: #000;
        line-height: 1;
      }
      .lbl {
        display: block;
        font-size: 0.7rem;
        font-weight: 600;
        color: #9ca3af;
        margin-top: 2px;
      }
    `,
  ],
})
export class PlatformDonutChartComponent implements OnChanges, OnDestroy {
  @Input() active = 0;
  @Input() inactive = 0;
  @ViewChild("c", { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;
  private chart?: Chart;

  constructor(private ngZone: NgZone) {}

  get total(): number {
    return this.active + this.inactive;
  }

  ngOnChanges(): void {
    this.build();
  }
  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private build(): void {
    if (this.total === 0) return;
    this.chart?.destroy();
    this.ngZone.runOutsideAngular(() => {
      this.chart = new Chart(this.canvasRef.nativeElement, {
        type: "doughnut",
        data: {
          labels: ["Actives", "Inactives"],
          datasets: [
            {
              data: [this.active, this.inactive],
              backgroundColor: ["#0066FF", "#E5E7EB"],
              borderColor: "#fff",
              borderWidth: 3,
              hoverOffset: 4,
            },
          ],
        },
        options: {
          responsive: false,
          cutout: "72%",
          plugins: {
            legend: { display: false },
            tooltip: {
              backgroundColor: "#fff",
              titleColor: "#111827",
              bodyColor: "#374151",
              borderColor: "#E5E7EB",
              borderWidth: 1,
              padding: 10,
            },
          },
        },
      });
    }); // runOutsideAngular
  }
}
