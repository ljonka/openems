import { NgModule } from '@angular/core';
import { SharedModule } from './../../shared/shared.module';
import { ChannelsComponent } from './channels/channels.component';
import { IndexComponent as ComponentInstallIndexComponent } from './component/install/index.component';
import { ComponentInstallComponent } from './component/install/install.component';
import { IndexComponent as ComponentUpdateIndexComponent } from './component/update/index.component';
import { ComponentUpdateComponent } from './component/update/update.component';
import { SettingsComponent } from './settings.component';
import { ProfileComponent } from './profile/profile.component';

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    SettingsComponent,
    ChannelsComponent,
    ComponentInstallIndexComponent,
    ComponentInstallComponent,
    ComponentUpdateIndexComponent,
    ComponentUpdateComponent,
    ProfileComponent
  ]
})
export class SettingsModule { }
