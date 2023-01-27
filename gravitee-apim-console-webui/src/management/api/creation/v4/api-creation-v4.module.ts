/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { GioConfirmDialogModule, GioIconsModule } from '@gravitee/ui-particles-angular';
import { MatInputModule } from '@angular/material/input';
import { ReactiveFormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatListModule } from '@angular/material/list';
import { MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule } from '@angular/material/snack-bar';

import { ApiCreationV4Component } from './api-creation-v4.component';
import { ApiCreationV4Step1Component } from './steps/step-1/api-creation-v4-step-1.component';
import { ApiCreationV4Step2Component } from './steps/step-2/api-creation-v4-step-2.component';
import { ApiCreationV4StepWipComponent } from './steps/step-wip/api-creation-v4-step-wip.component';
import { ApiCreationV4Step6Component } from './steps/step-6/api-creation-v4-step-6.component';
import { ApiCreationV4ConfirmationComponent } from './api-creation-v4-confirmation.component';
import { ApiCreationV4Step21Component } from './steps/step-2-1/api-creation-v4-step-2-1.component';
import { ApiCreationStepperMenuModule } from './components/api-creation-stepper-menu/api-creation-stepper-menu.module';
import { Step1MenuItemComponent } from './steps/step-1-menu-item/step-1-menu-item.component';
import { ApiCreationV4InProgressComponent } from './api-creation-v4-in-progress.component';

import { GioSelectionListOptionModule } from '../../../../shared/components/gio-selection-list-option/gio-selection-list-option.module';
@NgModule({
  imports: [
    CommonModule,
    ReactiveFormsModule,

    MatCardModule,
    MatButtonModule,
    MatInputModule,
    MatCheckboxModule,
    MatListModule,
    MatDialogModule,
    MatSnackBarModule,
    MatTooltipModule,

    GioConfirmDialogModule,
    GioIconsModule,
    GioSelectionListOptionModule,
    GioConfirmDialogModule,
    ApiCreationStepperMenuModule,
  ],
  declarations: [
    ApiCreationV4Component,
    ApiCreationV4StepWipComponent,
    ApiCreationV4Step1Component,
    Step1MenuItemComponent,
    ApiCreationV4Step2Component,
    ApiCreationV4Step21Component,
    ApiCreationV4Step6Component,
    ApiCreationV4ConfirmationComponent,
    ApiCreationV4InProgressComponent,
  ],
  exports: [ApiCreationV4Component],
})
export class ApiCreationV4Module {}
