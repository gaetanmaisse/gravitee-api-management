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
package io.gravitee.gateway.reactive.core.condition;

import io.gravitee.definition.model.MessageConditionSupplier;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface MessageConditionFilter<T extends MessageConditionSupplier> {
    /**
     * Filters the given element by applying a message condition on it.
     *
     * @param ctx the current request context.
     * @param elt the elt to apply the filter on.
     *
     * @return a {@link Maybe} containing the filtered element or empty if the element didn't pass the filter step.
     */
    Maybe<T> filter(MessageExecutionContext ctx, T elt, Message message);
}
