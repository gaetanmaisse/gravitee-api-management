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
package io.gravitee.gateway.reactive.handlers.api.processor.cors;

import static io.gravitee.gateway.api.http.HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD;
import static io.gravitee.gateway.api.http.HttpHeaderNames.ORIGIN;
import static io.gravitee.gateway.reactive.api.context.InternalContextAttributes.ATTR_INTERNAL_INVOKER;
import static io.gravitee.gateway.reactive.api.context.InternalContextAttributes.ATTR_INTERNAL_SECURITY_SKIP;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.gravitee.common.http.HttpMethod;
import io.gravitee.definition.model.Cors;
import io.gravitee.definition.model.Proxy;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.handlers.api.processor.cors.CorsPreflightInvoker;
import io.gravitee.gateway.reactive.core.context.interruption.InterruptionException;
import io.gravitee.gateway.reactive.handlers.api.processor.AbstractProcessorTest;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
class CorsPreflightRequestProcessorTest extends AbstractProcessorTest {

    private CorsPreflightRequestProcessor corsPreflightRequestProcessor;

    @BeforeEach
    public void beforeEach() {
        corsPreflightRequestProcessor = CorsPreflightRequestProcessor.instance();
        lenient().when(mockRequest.method()).thenReturn(HttpMethod.OPTIONS);
        Proxy proxy = new Proxy();
        Cors cors = new Cors();
        cors.setEnabled(true);
        cors.setAccessControlAllowOrigin(Set.of("*"));
        cors.setAccessControlAllowMethods(Set.of("GET"));
        proxy.setCors(cors);
        api.setProxy(proxy);
    }

    @Test
    public void shouldInterruptWithDefaultHeadersWhenCorsEnabledAndValidRequest() {
        spyRequestHeaders.set(ORIGIN, "origin");
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "GET");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertError(InterruptionException.class);
        assertThat(spyCtx.metrics().getApplicationId()).isEqualTo("1");
        verify(mockResponse, times(2)).headers();

        verify(spyResponseHeaders, times(2)).set(any(), anyString());
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("origin");
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS)).isEqualTo("GET");
        verify(mockResponse, times(1)).status(eq(200));
        assertThat(spyCtx.<Boolean>getInternalAttribute(ATTR_INTERNAL_SECURITY_SKIP)).isNull();
        assertThat(spyCtx.<CorsPreflightInvoker>getInternalAttribute(ATTR_INTERNAL_INVOKER)).isNull();
    }

    @Test
    public void shouldInterruptWithCredentialsWhenCorsEnabledAndValidRequest() {
        api.getProxy().getCors().setAccessControlAllowCredentials(true);
        spyRequestHeaders.set(ORIGIN, "origin");
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "GET");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertError(InterruptionException.class);
        assertThat(spyCtx.metrics().getApplicationId()).isEqualTo("1");
        verify(mockResponse, times(3)).headers();
        verify(spyResponseHeaders, times(3)).set(any(), anyString());
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("origin");
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS)).isEqualTo("GET");
        verify(mockResponse, times(1)).status(eq(200));
        assertThat(spyCtx.<Boolean>getInternalAttribute(ATTR_INTERNAL_SECURITY_SKIP)).isNull();
        assertThat(spyCtx.<CorsPreflightInvoker>getInternalAttribute(ATTR_INTERNAL_INVOKER)).isNull();
    }

    @Test
    public void shouldInterruptWithControlMaxAgeHeaderWhenCorsEnabledAndValidRequest() {
        api.getProxy().getCors().setAccessControlMaxAge(10);
        spyRequestHeaders.set(ORIGIN, "origin");
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "GET");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertError(InterruptionException.class);
        assertThat(spyCtx.metrics().getApplicationId()).isEqualTo("1");
        verify(mockResponse, times(3)).headers();
        verify(spyResponseHeaders, times(3)).set(any(), anyString());
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("origin");
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS)).isEqualTo("GET");
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE)).isEqualTo("10");
        verify(mockResponse, times(1)).status(eq(200));
        assertThat(spyCtx.<Boolean>getInternalAttribute(ATTR_INTERNAL_SECURITY_SKIP)).isNull();
        assertThat(spyCtx.<CorsPreflightInvoker>getInternalAttribute(ATTR_INTERNAL_INVOKER)).isNull();
    }

    @Test
    public void shouldInterruptWithInvalidRequestBadMethodAndPoliciesNotRun() {
        // if policies are not executed, the context is interrupted what ever is the CORS validation status.
        // only the absence of CORS Specific Header and the error message assert the right behaviour
        api.getProxy().getCors().setRunPolicies(false);

        spyRequestHeaders.set(ORIGIN, "origin");
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "POST");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertError(InterruptionException.class);
        assertThat(spyCtx.metrics().getApplicationId()).isEqualTo("1");
        assertThat(spyCtx.metrics().getErrorMessage()).contains("Request method 'POST' is not allowed");
        verify(mockResponse, times(1)).status(eq(400));
        assertThat(spyCtx.<Boolean>getInternalAttribute(ATTR_INTERNAL_SECURITY_SKIP)).isNull();
        assertThat(spyCtx.<CorsPreflightInvoker>getInternalAttribute(ATTR_INTERNAL_INVOKER)).isNull();
        verify(mockResponse, never()).headers();
    }

    @Test
    public void shouldInterruptWithInvalidRequestBadMethodAndRunPolicies() {
        // if policies are marked as runnable, the context must be interrupted if the CORS validation status fails.
        api.getProxy().getCors().setRunPolicies(true);

        spyRequestHeaders.set(ORIGIN, "origin");
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "POST");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertError(InterruptionException.class);
        assertThat(spyCtx.metrics().getApplicationId()).isEqualTo("1");
        assertThat(spyCtx.metrics().getErrorMessage()).contains("Request method 'POST' is not allowed");
        verify(mockResponse, times(1)).status(eq(400));
        assertThat(spyCtx.<Boolean>getInternalAttribute(ATTR_INTERNAL_SECURITY_SKIP)).isNull();
        assertThat(spyCtx.<CorsPreflightInvoker>getInternalAttribute(ATTR_INTERNAL_INVOKER)).isNull();
        verify(mockResponse, never()).headers();
    }

    @Test
    public void shouldInterruptWithAccessControlAllowHeadersWhenCorsEnabledAndValidRequest() {
        Set<String> accessControlAllowHeaders = Set.of("X-Test", "X-Test-2");
        api.getProxy().getCors().setAccessControlAllowHeaders(accessControlAllowHeaders);
        spyRequestHeaders.set(ORIGIN, "origin");
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "GET");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertError(InterruptionException.class);
        assertThat(spyCtx.metrics().getApplicationId()).isEqualTo("1");
        verify(mockResponse, times(3)).headers();
        verify(spyResponseHeaders, times(3)).set(any(), anyString());
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("origin");
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS)).isEqualTo("GET");
        assertThat(spyResponseHeaders.get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS))
            .isEqualTo(String.join(", ", accessControlAllowHeaders));
        verify(mockResponse, times(1)).status(eq(200));
        assertThat(spyCtx.<Boolean>getInternalAttribute(ATTR_INTERNAL_SECURITY_SKIP)).isNull();
        assertThat(spyCtx.<CorsPreflightInvoker>getInternalAttribute(ATTR_INTERNAL_INVOKER)).isNull();
    }

    @Test
    public void shouldCompleteWithoutChangingResponseWhenCorsDisabled() {
        api.getProxy().getCors().setEnabled(false);
        corsPreflightRequestProcessor.execute(spyCtx).test().assertResult();
        verify(spyCtx, never()).metrics();
        verifyNoInteractions(mockResponse);
    }

    @Test
    public void shouldCompleteWithoutAddingHeadersWhenCorsEnableButNotOptionsMethod() {
        when(mockRequest.method()).thenReturn(HttpMethod.GET);
        spyRequestHeaders.set(ORIGIN, "origin");
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "GET");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertResult();
        verify(spyCtx, never()).metrics();
        verifyNoInteractions(mockResponse);
    }

    @Test
    public void shouldCompleteWithoutAddingHeadersWhenCorsEnableButNoOrigin() {
        spyRequestHeaders.set(ACCESS_CONTROL_REQUEST_METHOD, "GET");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertResult();
        verify(spyCtx, never()).metrics();
        verifyNoInteractions(mockResponse);
    }

    @Test
    public void shouldCompleteWithoutAddingHeadersWhenCorsEnableButNoHeaderMethod() {
        spyRequestHeaders.set(ORIGIN, "origin");
        corsPreflightRequestProcessor.execute(spyCtx).test().assertResult();
        verify(spyCtx, never()).metrics();
        verifyNoInteractions(mockResponse);
    }
}
