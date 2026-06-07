import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { trigger, state, style, animate, transition } from '@angular/animations';

@Component({
  selector: 'app-splash',
  template: `
    <div class="splash-screen" [@fadeOut]="fading ? 'out' : 'in'" [style.pointer-events]="fading ? 'none' : 'auto'">
      <div class="splash-content">
        <img src="assets/MicroRecouv.png" alt="MicroRecouv" class="splash-logo" [@logoAnim]="'in'">
        <div class="splash-progress">
          <div class="splash-bar" [style.width]="progress + '%'"></div>
        </div>
        <p class="splash-text">{{ message }}</p>
      </div>
    </div>
  `,
  styles: [`
    .splash-screen {
      position: fixed;
      inset: 0;
      background: linear-gradient(135deg, #071A32 0%, #0F2D52 50%, #163863 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
    }
    .splash-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 24px;
    }
    .splash-logo {
      height: 100px;
      width: auto;
      filter: drop-shadow(0 8px 32px rgba(200,146,58,0.4));
      animation: float 3s ease-in-out infinite;
    }
    @keyframes float {
      0%, 100% { transform: translateY(0); }
      50%       { transform: translateY(-8px); }
    }
    .splash-progress {
      width: 220px;
      height: 3px;
      background: rgba(255,255,255,0.12);
      border-radius: 2px;
      overflow: hidden;
    }
    .splash-bar {
      height: 100%;
      background: linear-gradient(90deg, #2E87AF, #C8923A);
      border-radius: 2px;
      transition: width 0.3s ease;
    }
    .splash-text {
      color: rgba(255,255,255,0.5);
      font-size: 0.78rem;
      font-family: 'Inter', sans-serif;
      letter-spacing: 0.5px;
      margin: 0;
    }
  `],
  animations: [
    trigger('fadeOut', [
      state('in',  style({ opacity: 1 })),
      state('out', style({ opacity: 0 })),
      transition('in => out', animate('400ms ease')),
    ]),
    trigger('logoAnim', [
      transition(':enter', [
        style({ opacity: 0, transform: 'scale(0.8)' }),
        animate('600ms cubic-bezier(0.34,1.56,0.64,1)', style({ opacity: 1, transform: 'scale(1)' })),
      ])
    ])
  ]
})
export class SplashComponent implements OnInit {

  @Output() done = new EventEmitter<void>();

  progress = 0;
  fading = false;
  message = 'Initialisation…';

  private readonly STEPS = [
    { pct: 30,  msg: 'Chargement des modules…' },
    { pct: 60,  msg: 'Connexion au serveur…' },
    { pct: 85,  msg: 'Préparation de l\'interface…' },
    { pct: 100, msg: 'Prêt !' },
  ];

  ngOnInit(): void {
    this.runProgress();
  }

  private runProgress(): void {
    let i = 0;
    const tick = () => {
      if (i >= this.STEPS.length) {
        setTimeout(() => {
          this.fading = true;
          setTimeout(() => this.done.emit(), 450);
        }, 300);
        return;
      }
      const step = this.STEPS[i++];
      this.progress = step.pct;
      this.message  = step.msg;
      setTimeout(tick, 380);
    };
    setTimeout(tick, 200);
  }
}
