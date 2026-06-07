import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface FullscreenToast {
  type: 'success' | 'error' | 'logout';
  title: string;
  message: string;
  show: boolean;
  username?: string;
}

@Injectable({
  providedIn: 'root'
})
export class FullscreenToastService {
  private toastSubject = new BehaviorSubject<FullscreenToast>({
    type: 'success',
    title: '',
    message: '',
    show: false
  });

  toast$ = this.toastSubject.asObservable();

  showSuccess(title: string, message: string, duration: number = 2000): void {
    this.toastSubject.next({ type: 'success', title, message, show: true });
    if (duration > 0) setTimeout(() => this.hide(), duration);
  }

  showError(title: string, message: string, duration: number = 3000): void {
    this.toastSubject.next({ type: 'error', title, message, show: true });
    if (duration > 0) setTimeout(() => this.hide(), duration);
  }

  showLogout(username?: string, duration: number = 2500): void {
    this.toastSubject.next({
      type: 'logout',
      title: 'Déconnexion réussie',
      message: username ? `À bientôt, ${username} !` : 'À bientôt !',
      show: true,
      username
    });
    if (duration > 0) setTimeout(() => this.hide(), duration);
  }

  hide(): void {
    const current = this.toastSubject.value;
    this.toastSubject.next({ ...current, show: false });
  }
}
