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
package io.gravitee.gateway.jupiter.core.v4.analytics;

import io.gravitee.definition.model.v4.analytics.Analytics;
import io.gravitee.definition.model.v4.analytics.logging.Logging;
import io.gravitee.definition.model.v4.analytics.logging.LoggingMode;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalyticsUtils {

    @Nullable
    public static AnalyticsContext createAnalyticsContext(
        @Nonnull final io.gravitee.definition.model.Api apiV2,
        final String loggingMaxsize,
        final String loggingExcludedResponseType
    ) {
        io.gravitee.definition.model.Logging loggingV2 = apiV2.getProxy().getLogging();

        // Create v4 logging configuration from v2 as bridge
        Analytics analytics = new Analytics();
        analytics.setEnabled(true);
        if (loggingV2 != null) {
            Logging logging = new Logging();
            if (loggingV2.getMode() != null) {
                logging.getMode().setEntrypoint(loggingV2.getMode().isClientMode());
                logging.getMode().setEndpoint(loggingV2.getMode().isProxyMode());
            }
            if (loggingV2.getScope() != null) {
                logging.getPhase().setRequest(loggingV2.getScope().isRequest());
                logging.getPhase().setResponse(loggingV2.getScope().isResponse());
            }

            if (loggingV2.getContent() != null) {
                logging.getContent().setHeaders(loggingV2.getContent().isHeaders());
                logging.getContent().setPayload(loggingV2.getContent().isPayloads());
            }
            analytics.setLogging(logging);

            LoggingContext loggingContext = new LoggingContext(analytics.getLogging());
            loggingContext.setMaxSizeLogMessage(loggingMaxsize);
            loggingContext.setExcludedResponseTypes(loggingExcludedResponseType);
        }
        return new AnalyticsContext(analytics, false, loggingMaxsize, loggingExcludedResponseType);
    }

    public static boolean isLoggingEnabled(final Analytics analytics) {
        if (analytics != null && analytics.isEnabled()) {
            Logging logging = analytics.getLogging();
            if (logging != null) {
                final LoggingMode loggingMode = logging.getMode();
                return loggingMode != null && loggingMode.isEnabled();
            }
        }
        return false;
    }
}
