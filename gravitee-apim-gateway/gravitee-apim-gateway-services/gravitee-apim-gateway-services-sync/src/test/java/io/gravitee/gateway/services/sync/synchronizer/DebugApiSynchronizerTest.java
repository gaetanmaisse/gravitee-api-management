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
package io.gravitee.gateway.services.sync.synchronizer;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.common.event.EventManager;
import io.gravitee.gateway.reactor.ReactorEvent;
import io.gravitee.gateway.reactor.impl.ReactableWrapper;
import io.gravitee.gateway.services.sync.builder.RepositoryApiBuilder;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginRegistry;
import io.gravitee.repository.management.api.EventRepository;
import io.gravitee.repository.management.model.Api;
import io.gravitee.repository.management.model.ApiDebugStatus;
import io.gravitee.repository.management.model.Event;
import io.gravitee.repository.management.model.EventType;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Guillaume CUSNIEUX (guillaume.cusnieux at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class DebugApiSynchronizerTest {

    private static final String GATEWAY_ID = "gateway-id";

    private DebugApiSynchronizer debugApiSynchronizer;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventManager eventManager;

    @Mock
    private Node node;

    @Mock
    private PluginRegistry pluginRegistry;

    @Mock
    private Configuration configuration;

    static final List<String> ENVIRONMENTS = Arrays.asList("DEFAULT", "OTHER_ENV");

    @BeforeEach
    void setup() {
        Plugin debugPlugin = mock(Plugin.class);
        when(debugPlugin.id()).thenReturn("gateway-debug");
        when(pluginRegistry.plugins()).thenReturn(List.of(debugPlugin));
        when(configuration.getProperty("gravitee.services.gateway-debug.enabled", Boolean.class, true)).thenReturn(true);

        debugApiSynchronizer = new DebugApiSynchronizer(eventRepository, eventManager, pluginRegistry, configuration, node);
        lenient().when(node.id()).thenReturn(GATEWAY_ID);
    }

    @Test
    void shouldSynchronizeDebugEvents() {
        io.gravitee.repository.management.model.Api api = new RepositoryApiBuilder()
            .id("api-test")
            .updatedAt(new Date())
            .definition("test")
            .build();

        final Event mockEvent = mockEvent(api);

        when(
            eventRepository.search(
                argThat(criteria ->
                    criteria != null &&
                    criteria.getTypes().size() == 1 &&
                    criteria.getTypes().contains(EventType.DEBUG_API) &&
                    criteria.getEnvironments().containsAll(ENVIRONMENTS) &&
                    criteria
                        .getProperties()
                        .get(Event.EventProperties.API_DEBUG_STATUS.name().toLowerCase())
                        .equals(ApiDebugStatus.TO_DEBUG.name())
                )
            )
        )
            .thenReturn(singletonList(mockEvent));

        debugApiSynchronizer.synchronize(System.currentTimeMillis() - 5000, System.currentTimeMillis(), ENVIRONMENTS);

        verify(eventManager, times(1)).publishEvent(eq(ReactorEvent.DEBUG), any(ReactableWrapper.class));
    }

    @Test
    void shouldNotSyncIfDebugModeServiceIsDisabled() {
        when(configuration.getProperty("gravitee.services.gateway-debug.enabled", Boolean.class, true)).thenReturn(false);
        debugApiSynchronizer = new DebugApiSynchronizer(eventRepository, eventManager, pluginRegistry, configuration, node);

        debugApiSynchronizer.synchronize(System.currentTimeMillis() - 5000, System.currentTimeMillis(), ENVIRONMENTS);

        verify(eventRepository, never()).search(any());
        verify(eventManager, never()).publishEvent(eq(ReactorEvent.DEBUG), any(ReactableWrapper.class));
    }

    @Test
    void shouldNotSyncIfDebugModePluginIsAbsent() {
        when(pluginRegistry.plugins()).thenReturn(List.of());
        debugApiSynchronizer = new DebugApiSynchronizer(eventRepository, eventManager, pluginRegistry, configuration, node);

        debugApiSynchronizer.synchronize(System.currentTimeMillis() - 5000, System.currentTimeMillis(), ENVIRONMENTS);

        verify(eventRepository, never()).search(any());
        verify(eventManager, never()).publishEvent(eq(ReactorEvent.DEBUG), any(ReactableWrapper.class));
    }

    private Event mockEvent(final Api api) {
        Map<String, String> properties = new HashMap<>();
        properties.put(Event.EventProperties.API_DEBUG_STATUS.getValue(), ApiDebugStatus.TO_DEBUG.name());
        properties.put(Event.EventProperties.GATEWAY_ID.getValue(), node.id());
        Event event = new Event();
        event.setType(EventType.DEBUG_API);
        event.setCreatedAt(new Date());
        event.setProperties(properties);
        event.setPayload(api.getId());
        event.setId("eventId");
        return event;
    }
}
