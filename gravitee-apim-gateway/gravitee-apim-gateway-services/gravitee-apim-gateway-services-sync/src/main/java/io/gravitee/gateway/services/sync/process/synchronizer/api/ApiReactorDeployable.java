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
package io.gravitee.gateway.services.sync.process.synchronizer.api;

import io.gravitee.gateway.api.service.ApiKey;
import io.gravitee.gateway.api.service.Subscription;
import io.gravitee.gateway.reactor.ReactableApi;
import io.gravitee.gateway.services.sync.process.model.ApiKeyDeployable;
import io.gravitee.gateway.services.sync.process.model.SubscriptionDeployable;
import io.gravitee.gateway.services.sync.process.model.SyncAction;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Builder
@Getter
@Setter
@Accessors(fluent = true)
public class ApiReactorDeployable implements ApiKeyDeployable, SubscriptionDeployable {

    private String apiId;
    private ReactableApi<?> reactableApi;
    private SyncAction syncAction;
    private boolean onError;

    @Builder.Default
    private List<Subscription> subscriptions = List.of();

    @Builder.Default
    private List<ApiKey> apiKeys = List.of();

    @Setter(AccessLevel.NONE)
    private Set<String> subscribablePlans;

    @Setter(AccessLevel.NONE)
    private Set<String> apiKeyPlans;

    @Override
    public String id() {
        return apiId;
    }

    public String apiId() {
        if (apiId == null) {
            return reactableApi.getId();
        }
        return apiId;
    }

    public Set<String> subscribablePlans() {
        if (reactableApi != null) {
            if (subscribablePlans == null) {
                subscribablePlans = new HashSet<>();
                if (reactableApi.getSubscribablePlans() != null) {
                    subscribablePlans.addAll(reactableApi.getSubscribablePlans());
                }
            }
            return subscribablePlans;
        }
        return Set.of();
    }

    public Set<String> apiKeyPlans() {
        if (reactableApi != null) {
            if (apiKeyPlans == null) {
                apiKeyPlans = new HashSet<>();
                if (reactableApi.getApiKeyPlans() != null) {
                    apiKeyPlans.addAll(reactableApi.getApiKeyPlans());
                }
            }
            return apiKeyPlans;
        }
        return Set.of();
    }
}
