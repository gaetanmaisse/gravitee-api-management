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
package io.gravitee.repository.bridge.server.handler;

import static io.gravitee.repository.bridge.server.utils.ParamUtils.readSortable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ApiKeyRepository;
import io.gravitee.repository.management.api.SubscriptionRepository;
import io.gravitee.repository.management.api.search.ApiKeyCriteria;
import io.gravitee.repository.management.api.search.Sortable;
import io.gravitee.repository.management.model.ApiKey;
import io.gravitee.repository.management.model.Subscription;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApiKeysHandler extends AbstractHandler {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    public ApiKeysHandler(WorkerExecutor bridgeWorkerExecutor) {
        super(bridgeWorkerExecutor);
    }

    public void findByCriteria(RoutingContext ctx) {
        final JsonObject searchPayload = ctx.body().asJsonObject();

        // Parse criteria
        final ApiKeyCriteria apiKeyCriteria = readCriteria(searchPayload);
        final Sortable sortable = readSortable(ctx);

        bridgeWorkerExecutor.executeBlocking(
            promise -> {
                try {
                    promise.complete(apiKeyRepository.findByCriteria(apiKeyCriteria, sortable));
                } catch (TechnicalException te) {
                    LOGGER.error("Unable to find the API Key", te);
                    promise.fail(te);
                }
            },
            false,
            (Handler<AsyncResult<List<ApiKey>>>) result -> handleResponse(ctx, result)
        );
    }

    /**
     * Bridge clients >= 3.17 will call findByCriteria instead of search
     * This is kept to ensure 0 downtime suring Gravitee upgrade running a 3.16 bridge client server and a 3.17 bridge server
     * @deprecated
     */
    @Deprecated(since = "3.17.0", forRemoval = true)
    public void search(RoutingContext ctx) {
        final JsonObject searchPayload = ctx.body().asJsonObject();

        // Parse criteria
        final ApiKeyCriteria apiKeyCriteria = readCriteria(searchPayload);

        bridgeWorkerExecutor.executeBlocking(
            promise -> {
                try {
                    List<io.gravitee.repository.management.model.ApiKey> apiKeys = apiKeyRepository.findByCriteria(apiKeyCriteria, null);
                    Map<String, Subscription> subscriptionsById = findSubscriptions(apiKeys);
                    promise.complete(
                        apiKeys.stream().flatMap(apiKey -> this.getApiKeysDefinitionsFromModel(apiKey, subscriptionsById)).collect(toList())
                    );
                } catch (TechnicalException te) {
                    LOGGER.error("Unable to search for API Keys", te);
                    promise.fail(te);
                }
            },
            false,
            (Handler<AsyncResult<List<DeprecatedApiKey>>>) result -> handleResponse(ctx, result)
        );
    }

    /**
     * Bridge clients rely on their own ApiKeyWrapper cached method
     * for this call and there is no need to expose this endpoint
     * @deprecated
     */
    @Deprecated(since = "3.17.0", forRemoval = true)
    public void findByKeyAndApi(RoutingContext ctx) {
        final String apiId = ctx.request().getParam("apiId");
        final String key = ctx.request().getParam("key");

        bridgeWorkerExecutor.executeBlocking(
            promise -> {
                try {
                    promise.complete(apiKeyRepository.findByKeyAndApi(key, apiId));
                } catch (TechnicalException te) {
                    LOGGER.error("Unable to find the API Key", te);
                    promise.fail(te);
                }
            },
            false,
            (Handler<AsyncResult<Optional<ApiKey>>>) result -> handleResponse(ctx, result)
        );
    }

    private ApiKeyCriteria readCriteria(JsonObject payload) {
        ApiKeyCriteria.ApiKeyCriteriaBuilder builder = ApiKeyCriteria.builder();
        if (payload != null) {
            Long fromVal = payload.getLong("from");
            if (fromVal != null && fromVal > 0) {
                builder.from(fromVal);
            }

            Long toVal = payload.getLong("to");
            if (toVal != null && toVal > 0) {
                builder.to(toVal);
            }

            Long expireAfterVal = payload.getLong("expireAfter");
            if (expireAfterVal != null && expireAfterVal > 0) {
                builder.expireAfter(expireAfterVal);
            }

            Long expireBeforeVal = payload.getLong("expireBefore");
            if (expireBeforeVal != null && expireBeforeVal > 0) {
                builder.expireBefore(expireBeforeVal);
            }

            Boolean includeWithoutExpirationVal = payload.getBoolean("includeWithoutExpiration");
            if (includeWithoutExpirationVal != null) {
                builder.includeWithoutExpiration(includeWithoutExpirationVal);
            }

            Boolean revokeVal = payload.getBoolean("includeRevoked", false);
            builder.includeRevoked(revokeVal);

            JsonArray subscriptionsArr = payload.getJsonArray("subscriptions");
            if (subscriptionsArr != null) {
                Set<String> subscriptions = subscriptionsArr.stream().map(String.class::cast).collect(Collectors.toSet());
                builder.subscriptions(subscriptions);
            }
        }
        return builder.build();
    }

    private Map<String, Subscription> findSubscriptions(List<io.gravitee.repository.management.model.ApiKey> apiKeys)
        throws TechnicalException {
        Set<String> subscriptionIds = apiKeys.stream().flatMap(key -> key.getSubscriptions().stream()).collect(toSet());
        return subscriptionRepository.findByIdIn(subscriptionIds).stream().collect(toMap(Subscription::getId, Function.identity()));
    }

    private Stream<DeprecatedApiKey> getApiKeysDefinitionsFromModel(
        io.gravitee.repository.management.model.ApiKey apiKey,
        Map<String, Subscription> subscriptionsById
    ) {
        return apiKey
            .getSubscriptions()
            .stream()
            .map(subscriptionsById::get)
            .filter(Objects::nonNull)
            .map(subscription -> new DeprecatedApiKey(apiKey, subscription));
    }

    private static class DeprecatedApiKey extends io.gravitee.repository.management.model.ApiKey {

        private final String plan;
        private final String api;
        private final String subscription;

        public DeprecatedApiKey(io.gravitee.repository.management.model.ApiKey key, Subscription subscription) {
            super(key);
            this.plan = subscription.getPlan();
            this.api = subscription.getApi();
            this.subscription = subscription.getId();
        }

        @SuppressWarnings("removal")
        public String getPlan() {
            return this.plan;
        }

        @SuppressWarnings("removal")
        public String getApi() {
            return this.api;
        }

        @SuppressWarnings("removal")
        public String getSubscription() {
            return subscription;
        }
    }
}
