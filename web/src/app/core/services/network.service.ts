import { Injectable, OnDestroy } from "@angular/core";
import { BehaviorSubject } from "rxjs";

@Injectable({ providedIn: "root" })
export class NetworkService implements OnDestroy {
  private readonly online$ = new BehaviorSubject<boolean>(
    typeof navigator !== "undefined" ? navigator.onLine : true,
  );

  readonly isOnline$ = this.online$.asObservable();

  private readonly onOnline = () => this.online$.next(true);
  private readonly onOffline = () => this.online$.next(false);

  constructor() {
    window.addEventListener("online", this.onOnline);
    window.addEventListener("offline", this.onOffline);
  }

  get isOnline(): boolean {
    return this.online$.value;
  }

  ngOnDestroy(): void {
    window.removeEventListener("online", this.onOnline);
    window.removeEventListener("offline", this.onOffline);
  }
}
