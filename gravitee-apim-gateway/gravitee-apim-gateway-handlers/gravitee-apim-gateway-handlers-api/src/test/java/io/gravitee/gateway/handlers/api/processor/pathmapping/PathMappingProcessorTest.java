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
package io.gravitee.gateway.handlers.api.processor.pathmapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.definition.model.Api;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.core.component.CustomComponentProvider;
import io.gravitee.gateway.core.processor.Processor;
import io.gravitee.gateway.handlers.api.processor.pathparameters.PathParametersIndexProcessor;
import io.gravitee.gateway.reactive.core.context.DefaultExecutionContext;
import io.gravitee.gateway.reactive.core.context.MutableRequest;
import io.gravitee.gateway.reactive.core.context.MutableResponse;
import io.gravitee.reporter.api.http.Metrics;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class PathMappingProcessorTest {

    private static final String PATH_INFO = "/path/:id/info";

    private ExecutionContext context;

    @Spy
    private Request mockRequest;

    @Spy
    private Response mockResponse;

    @Mock
    private Handler<ExecutionContext> next;

    protected Api api;

    @BeforeEach
    public void beforeEach() {
        api = new Api();
        context = spy(new SimpleExecutionContext(mockRequest, mockResponse));
        lenient().when(mockRequest.metrics()).thenReturn(Metrics.on(1L).build());
        lenient().when(mockRequest.pathInfo()).thenReturn(PATH_INFO);
        lenient().when(mockRequest.pathParameters()).thenReturn(new LinkedMultiValueMap<>());
    }

    @Test
    public void shouldNotAddMappedPathWithEmptyMapping() {
        api.setPathMappings(Map.of());
        PathMappingProcessor processor = new PathMappingProcessor(new LinkedMultiValueMap());
        processor.handler(next);
        processor.handle(context);
        verify(mockRequest, never()).metrics();
    }

    @Test
    public void shouldAddMappedPathWithMapping() {
        PathMappingProcessor processor = new PathMappingProcessor(Map.of(PATH_INFO, Pattern.compile("/path/.*/info/")));
        processor.handler(next);
        processor.handle(context);
        assertThat(context.request().metrics().getMappedPath()).isEqualTo(PATH_INFO);
    }

    @Test
    public void shouldAddShortestMappedPathWithTwoMapping() {
        String shorterPath = "/path";
        PathMappingProcessor processor = new PathMappingProcessor(
            Map.of(PATH_INFO, Pattern.compile("/path/.*/info/"), shorterPath, Pattern.compile("/path.*"))
        );
        processor.handler(next);
        processor.handle(context);
        assertThat(context.request().metrics().getMappedPath()).isEqualTo(shorterPath);
    }

    @Test
    public void shouldNotAddMappedPathWithMappingButEmptyPathInfo() {
        when(mockRequest.pathInfo()).thenReturn("");
        PathMappingProcessor processor = new PathMappingProcessor(Map.of(PATH_INFO, Pattern.compile("/path/.*/info/")));
        processor.handler(next);
        processor.handle(context);
        verify(mockRequest, never()).metrics();
    }

    @Test
    public void shouldAddPathParametersWithMapping() {
        lenient().when(mockRequest.pathInfo()).thenReturn("/path/path-12/info/info-13");
        PathMappingProcessor processor = new PathMappingProcessor(
            Map.of("/path/:pathId/info/:infoId", Pattern.compile("/path/.*/info/.*"))
        );
        processor.handler(next);
        processor.handle(context);
        assertThat(context.request().pathParameters().get("pathId").get(0)).isEqualTo("path-12");
        assertThat(context.request().pathParameters().get("infoId").get(0)).isEqualTo("info-13");
    }
}
