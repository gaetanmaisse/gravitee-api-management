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
package io.gravitee.gateway.reactive.v4.flow;

import io.gravitee.definition.model.v4.Api;
import io.gravitee.definition.model.v4.flow.Flow;
import io.gravitee.gateway.reactive.api.context.GenericExecutionContext;
import io.gravitee.gateway.reactive.api.context.InternalContextAttributes;
import io.gravitee.gateway.reactor.ReactableApi;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * This flow resolver resolves only the {@link Flow} which best matches according to the incoming request.
 * This flow resolver relies on the result of a previous {@link FlowResolver} used to build this instance.
 * This means that the flows list patterns to filter for Best Match mode already matched the request.
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BestMatchFlowResolver implements FlowResolver {

    private final FlowResolver flowResolver;

    public BestMatchFlowResolver(final FlowResolver flowResolver) {
        this.flowResolver = flowResolver;
    }

    @Override
    public Flowable<Flow> resolve(final GenericExecutionContext ctx) {
        return provideFlows(ctx)
            .toList()
            .flatMapMaybe(flows ->
                Maybe.fromCallable(() -> {
                    ReactableApi<?> reactableApi = ctx.getInternalAttribute(InternalContextAttributes.ATTR_INTERNAL_REACTABLE_API);
                    Api definition = (Api) reactableApi.getDefinition();
                    return BestMatchFlowSelector.forPath(definition.getType(), flows, ctx.request().pathInfo());
                })
            )
            .toFlowable();
    }

    @Override
    public Flowable<Flow> provideFlows(final GenericExecutionContext ctx) {
        return flowResolver.resolve(ctx);
    }
}
