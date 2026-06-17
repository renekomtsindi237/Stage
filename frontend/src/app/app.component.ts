import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastComponent } from './shared/components/toast/toast.component';
import { NotificationService } from './core/services/notification.service';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent],
  template: `
    <router-outlet />
    <app-toast />
  `,
})
export class AppComponent implements OnInit {
  private readonly notifService = inject(NotificationService);
  private readonly auth         = inject(AuthService);

  ngOnInit() {
    if (this.auth.isLoggedIn()) {
      this.notifService.init();
    }
  }
}
