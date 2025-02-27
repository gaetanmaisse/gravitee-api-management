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
import { isFunction } from 'lodash';

import { Plan, PlanSecurityType } from './plan';

export function fakeV4Plan(modifier?: Partial<Plan> | ((baseApi: Plan) => Plan)): Plan {
  const base: Plan = {
    id: '45ff00ef-8256-3218-bf0d-b289735d84bb',
    name: 'Free Spaceshuttle',
    security: { type: PlanSecurityType.KEY_LESS, configuration: {} },
    securityDefinition: '{}',
    paths: {},
    flows: [],
    status: 'published',
    apiId: 'api#1',
    order: 0,
  };

  if (isFunction(modifier)) {
    return modifier(base);
  }

  return {
    ...base,
    ...modifier,
  };
}
