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
package io.gravitee.gateway.reactive.debug.vertx;

import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.http.vertx.TimeoutServerResponse;
import io.vertx.core.Vertx;

/**
 * This class is created for V3 compatibility in Jupiter when using Debug Mode.
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TimeoutServerResponseDebugDecorator extends TimeoutServerResponse {

    public TimeoutServerResponseDebugDecorator(Vertx vertx, Response response, long timerId) {
        super(vertx, response, timerId);
    }

    public Response response() {
        return response;
    }
}
