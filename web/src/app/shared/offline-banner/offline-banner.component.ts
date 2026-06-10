import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { trigger, transition, style, animate } from "@angular/animations";
import { Subscription } from "rxjs";
import { distinctUntilChanged } from "rxjs/operators";
import { NetworkService } from "@core/services/network.service";

@Component({
  selector: "imf-offline-banner",
  templateUrl: "./offline-banner.component.html",
  styleUrls: ["./offline-banner.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger("bannerSlide", [
      transition(":enter", [
        style({ transform: "translateY(-100%)", opacity: 0 }),
        animate(
          "300ms cubic-bezier(0.22,1,0.36,1)",
          style({ transform: "translateY(0)", opacity: 1 }),
        ),
      ]),
      transition(":leave", [
        animate(
          "220ms ease-in",
          style({ transform: "translateY(-100%)", opacity: 0 }),
        ),
      ]),
    ]),
  ],
})
export class OfflineBannerComponent implements OnInit, OnDestroy {
  isOffline = false;
  justCameBack = false;

  private sub?: Subscription;
  private backTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private network: NetworkService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.sub = this.network.isOnline$
      .pipe(distinctUntilChanged())
      .subscribe((online) => {
        if (!online) {
          this.isOffline = true;
          this.justCameBack = false;
        } else if (this.isOffline) {
          this.justCameBack = true;
          clearTimeout(this.backTimer);
          this.backTimer = setTimeout(() => {
            this.isOffline = false;
            this.justCameBack = false;
            this.cdr.markForCheck();
          }, 2500);
        }
        this.cdr.markForCheck();
      });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    clearTimeout(this.backTimer);
  }

  retry(): void {
    window.location.reload();
  }
}
