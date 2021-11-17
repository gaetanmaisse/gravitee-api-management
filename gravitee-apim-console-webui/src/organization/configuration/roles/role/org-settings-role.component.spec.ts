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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { HttpTestingController } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { GioSaveBarHarness } from '@gravitee/ui-particles-angular';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';

import { OrgSettingsRoleComponent } from './org-settings-role.component';

import { OrganizationSettingsModule } from '../../organization-settings.module';
import { CONSTANTS_TESTING, GioHttpTestingModule } from '../../../../shared/testing';
import { UIRouterState, UIRouterStateParams } from '../../../../ajs-upgraded-providers';
import { fakeRole } from '../../../../entities/role/role.fixture';
import { Role } from '../../../../entities/role/role';
import { fakePermissionsByScopes } from '../../../../entities/role/permission.fixtures';

describe('OrgSettingsRoleComponent', () => {
  const roleScope = 'ORGANIZATION';
  const role = 'USER';
  const fakeAjsState = {
    go: jest.fn(),
  };

  let fixture: ComponentFixture<OrgSettingsRoleComponent>;
  let httpTestingController: HttpTestingController;
  let loader: HarnessLoader;

  afterEach(() => {
    httpTestingController.verify();
    jest.resetAllMocks();
  });

  describe('edit mode', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        imports: [NoopAnimationsModule, GioHttpTestingModule, OrganizationSettingsModule],
        providers: [
          { provide: UIRouterState, useValue: fakeAjsState },
          { provide: UIRouterStateParams, useValue: { roleScope, role } },
        ],
      });
      httpTestingController = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(OrgSettingsRoleComponent);
      loader = TestbedHarnessEnvironment.loader(fixture);
      fixture.detectChanges();
    });

    it('should update role', async () => {
      const role = fakeRole({ id: 'roleId', scope: roleScope });
      expectRoleGetRequest(role);
      expectGetPermissionsByScopeRequest(['1_USER_P', '2_ROLE_P']);

      const saveBar = await loader.getHarness(GioSaveBarHarness);

      const descriptionInput = await loader.getHarness(MatInputHarness.with({ selector: '[formControlName=description]' }));
      await descriptionInput.setValue('New description');

      const defaultToggle = await loader.getHarness(MatSlideToggleHarness.with({ selector: '[formControlName=default]' }));
      await defaultToggle.toggle();

      expect(await saveBar.isSubmitButtonInvalid()).toEqual(false);
      await saveBar.clickSubmit();

      const req = httpTestingController.expectOne({
        url: `${CONSTANTS_TESTING.org.baseURL}/configuration/rolescopes/${role.scope}/roles/${role.name}`,
        method: 'PUT',
      });
      expect(req.request.body).toEqual({ ...role, description: 'New description', default: false });
      // No flush to stop test here
    });

    it('should disable form with a system role', async () => {
      const role = fakeRole({ id: 'roleId', system: true, scope: roleScope });
      expectRoleGetRequest(role);
      expectGetPermissionsByScopeRequest(['1_USER_P', '2_ROLE_P']);

      const descriptionInput = await loader.getHarness(MatInputHarness.with({ selector: '[formControlName=description]' }));
      expect(await descriptionInput.isDisabled()).toEqual(true);

      const defaultToggle = await loader.getHarness(MatSlideToggleHarness.with({ selector: '[formControlName=default]' }));
      expect(await defaultToggle.isDisabled()).toEqual(true);
    });
  });

  describe('create mode', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        imports: [NoopAnimationsModule, GioHttpTestingModule, OrganizationSettingsModule],
        providers: [
          { provide: UIRouterState, useValue: fakeAjsState },
          { provide: UIRouterStateParams, useValue: { roleScope } },
        ],
      });
      httpTestingController = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(OrgSettingsRoleComponent);
      loader = TestbedHarnessEnvironment.loader(fixture);
      fixture.detectChanges();
    });

    it('should create role', async () => {
      expectGetPermissionsByScopeRequest(['1_USER_P', '2_ROLE_P']);

      const saveBar = await loader.getHarness(GioSaveBarHarness);

      const nameInput = await loader.getHarness(MatInputHarness.with({ selector: '[formControlName=name]' }));
      await nameInput.setValue('New name');

      const descriptionInput = await loader.getHarness(MatInputHarness.with({ selector: '[formControlName=description]' }));
      await descriptionInput.setValue('New description');

      const defaultToggle = await loader.getHarness(MatSlideToggleHarness.with({ selector: '[formControlName=default]' }));
      await defaultToggle.toggle();

      expect(await saveBar.isSubmitButtonInvalid()).toEqual(false);
      await saveBar.clickSubmit();

      const req = httpTestingController.expectOne({
        url: `${CONSTANTS_TESTING.org.baseURL}/configuration/rolescopes/${roleScope}/roles`,
        method: 'POST',
      });
      expect(req.request.body).toEqual({
        name: 'New name',
        description: 'New description',
        default: true,
        scope: roleScope,
      });
      req.flush(null);
      fixture.detectChanges();

      expect(fakeAjsState.go).toHaveBeenCalledWith('organization.settings.ng-roleedit', { role: 'NEW NAME', roleScope: 'ORGANIZATION' });
    });
  });

  function expectRoleGetRequest(role: Role) {
    httpTestingController
      .expectOne({ url: `${CONSTANTS_TESTING.org.baseURL}/configuration/rolescopes/${role.scope}/roles/${role.name}`, method: 'GET' })
      .flush(role);
    fixture.detectChanges();
  }

  function expectGetPermissionsByScopeRequest(permissions: string[]) {
    httpTestingController
      .expectOne({ url: `${CONSTANTS_TESTING.org.baseURL}/configuration/rolescopes`, method: 'GET' })
      .flush(fakePermissionsByScopes({ [roleScope]: permissions }));
    fixture.detectChanges();
  }
});
