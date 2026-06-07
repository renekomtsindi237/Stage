import { NgModule, Optional, SkipSelf } from '@angular/core';

@NgModule({
  providers: []
})
export class CoreModule {
  constructor(@Optional() @SkipSelf() parent?: CoreModule) {
    if (parent) {
      throw new Error('CoreModule is already loaded.');
    }
  }
}
