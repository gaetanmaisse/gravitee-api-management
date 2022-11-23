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
package io.gravitee.gateway.services.sync.cache.repository;

import io.gravitee.common.data.domain.Page;
import io.gravitee.gateway.services.sync.cache.SubscriptionsCache;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.SubscriptionRepository;
import io.gravitee.repository.management.api.search.Order;
import io.gravitee.repository.management.api.search.Pageable;
import io.gravitee.repository.management.api.search.SubscriptionCriteria;
import io.gravitee.repository.management.model.Subscription;
import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SubscriptionRepositoryWrapper implements SubscriptionRepository {

    private final SubscriptionRepository wrapped;
    private final SubscriptionsCache cache;

    public SubscriptionRepositoryWrapper(final SubscriptionRepository wrapped, final SubscriptionsCache cache) {
        this.wrapped = wrapped;
        this.cache = cache;
    }

    @Override
    public Optional<Subscription> findById(String s) throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public Subscription create(Subscription item) throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public Subscription update(Subscription item) throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public void delete(String s) throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public Page<Subscription> search(SubscriptionCriteria criteria, Pageable pageable) throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public List<Subscription> search(SubscriptionCriteria criteria) throws TechnicalException {
        // If criteria does not include the clientId, it is a search from underlying repository
        // If clientId is included, the search is done by the gateway to check a subscription from the cache.
        if (criteria.getClientId() == null) {
            return this.wrapped.search(criteria);
        }

        final String api = criteria.getApis() != null && !criteria.getApis().isEmpty() ? criteria.getApis().iterator().next() : null;
        final String plan = criteria.getPlans() != null && !criteria.getPlans().isEmpty() ? criteria.getPlans().iterator().next() : null;

        return cache.get(api, criteria.getClientId(), plan).map(Collections::singletonList).orElse(null);
    }

    @Override
    public Set<String> findReferenceIdsOrderByNumberOfSubscriptions(SubscriptionCriteria criteria, Order order) throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public Set<Subscription> findAll() throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public List<Subscription> findByIdIn(Collection<String> ids) throws TechnicalException {
        return wrapped.findByIdIn(ids);
    }
}
