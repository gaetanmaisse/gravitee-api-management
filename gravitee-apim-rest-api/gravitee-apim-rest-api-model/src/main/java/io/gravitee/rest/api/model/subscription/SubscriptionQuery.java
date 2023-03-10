/**
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
package io.gravitee.rest.api.model.subscription;

import io.gravitee.rest.api.model.SubscriptionStatus;
import java.util.Collection;
import java.util.Collections;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class SubscriptionQuery {

    private Collection<String> apis;

    private Collection<String> plans;

    private Collection<SubscriptionStatus> statuses;

    private Collection<String> applications;

    private Collection<String> planSecurityTypes;

    private String apiKey;

    private long from = -1;
    private long to = -1;

    private long endingAtAfter = -1;
    private long endingAtBefore = -1;

    private boolean includeWithoutEnd;

    public void setApi(String api) {
        if (api != null) {
            this.apis = Collections.singleton(api);
        }
    }

    public void setPlan(String plan) {
        if (plan != null) {
            this.plans = Collections.singleton(plan);
        }
    }

    public void setApplication(String application) {
        if (application != null) {
            this.applications = Collections.singleton(application);
        }
    }

    public boolean matchesApi(String api) {
        return apis == null || apis.contains(api);
    }

    public boolean matchesPlan(String plan) {
        return plans == null || plans.contains(plan);
    }

    public boolean matchesApplication(String application) {
        return applications == null || applications.contains(application);
    }

    public boolean matchesStatus(SubscriptionStatus status) {
        return statuses == null || statuses.contains(status);
    }
}
