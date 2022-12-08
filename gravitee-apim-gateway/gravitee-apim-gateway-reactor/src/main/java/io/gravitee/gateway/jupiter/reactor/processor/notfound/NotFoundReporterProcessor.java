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
package io.gravitee.gateway.jupiter.reactor.processor.notfound;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.jupiter.core.context.MutableExecutionContext;
import io.gravitee.gateway.jupiter.core.processor.Processor;
import io.gravitee.gateway.report.ReporterService;
import io.gravitee.reporter.api.common.Request;
import io.gravitee.reporter.api.v4.log.Log;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.core.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotFoundReporterProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotFoundReporterProcessor.class);
    private static final String UNKNOWN_SERVICE = "1";

    private final ReporterService reporterService;
    private final boolean logEnabled;

    public NotFoundReporterProcessor(final ReporterService reporterService, final boolean logEnabled) {
        this.reporterService = reporterService;
        this.logEnabled = logEnabled;
    }

    @Override
    public String getId() {
        return "processor-not-found-reporter";
    }

    @Override
    public Completable execute(final MutableExecutionContext ctx) {
        return Completable
            .defer(
                () -> {
                    Metrics metrics = ctx.metrics();
                    metrics.setApiId(UNKNOWN_SERVICE);
                    metrics.setApplicationId(UNKNOWN_SERVICE);
                    metrics.setPath(ctx.request().pathInfo());

                    if (logEnabled) {
                        Buffer payload = Buffer.buffer();
                        return ctx
                            .request()
                            .bodyOrEmpty()
                            .doOnSuccess(
                                buffer -> {
                                    Request entrypointRequest = new Request();
                                    entrypointRequest.setMethod(ctx.request().method());
                                    entrypointRequest.setUri(ctx.request().uri());
                                    entrypointRequest.setHeaders(ctx.request().headers());
                                    entrypointRequest.setBody(payload.toString());

                                    metrics.setLog(
                                        Log
                                            .builder()
                                            .timestamp(System.currentTimeMillis())
                                            .apiId(UNKNOWN_SERVICE)
                                            .requestId(ctx.request().id())
                                            .entrypointRequest(entrypointRequest)
                                            .build()
                                    );

                                    reporterService.report(metrics);
                                }
                            )
                            .ignoreElement();
                    } else {
                        reporterService.report(metrics);
                        return Completable.complete();
                    }
                }
            )
            .doOnError(throwable -> LOGGER.error("An error occurs while reporting metrics for not found request", throwable))
            .onErrorComplete();
    }
}
