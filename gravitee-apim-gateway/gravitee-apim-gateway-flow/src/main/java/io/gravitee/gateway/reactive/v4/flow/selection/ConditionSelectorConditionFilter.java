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
package io.gravitee.gateway.reactive.v4.flow.selection;

import io.gravitee.definition.model.v4.flow.Flow;
import io.gravitee.definition.model.v4.flow.selector.ConditionSelector;
import io.gravitee.definition.model.v4.flow.selector.Selector;
import io.gravitee.definition.model.v4.flow.selector.SelectorType;
import io.gravitee.gateway.reactive.api.context.GenericExecutionContext;
import io.gravitee.gateway.reactive.core.condition.ConditionFilter;
import io.gravitee.gateway.reactive.core.condition.ExpressionLanguageConditionFilter;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Optional;

/**
 * This {@link ConditionFilter} evaluates to true if the request is matching the
 * condition selector declared within the {@link Flow}.
 *
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConditionSelectorConditionFilter implements ConditionFilter<Flow> {

    private final ExpressionLanguageConditionFilter<ConditionSelector> elConditionFilter = new ExpressionLanguageConditionFilter<>();

    @Override
    public Maybe<Flow> filter(final GenericExecutionContext ctx, final Flow flow) {
        return Maybe.defer(() -> {
            Optional<Selector> selectorOptional = flow.selectorByType(SelectorType.CONDITION);
            if (selectorOptional.isPresent()) {
                ConditionSelector conditionSelector = (ConditionSelector) selectorOptional.get();
                return elConditionFilter.filter(ctx, conditionSelector).onErrorResumeWith(Maybe.empty()).map(filter -> flow);
            }
            return Maybe.just(flow);
        });
    }
}
