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
package io.gravitee.rest.api.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.common.data.domain.Page;
import io.gravitee.repository.management.model.Event;
import io.gravitee.rest.api.model.EventEntity;
import io.gravitee.rest.api.model.EventQuery;
import io.gravitee.rest.api.model.EventType;
import io.gravitee.rest.api.model.InstanceEntity;
import io.gravitee.rest.api.model.InstanceListItem;
import io.gravitee.rest.api.model.InstanceQuery;
import io.gravitee.rest.api.model.InstanceState;
import io.gravitee.rest.api.model.PluginEntity;
import io.gravitee.rest.api.service.EventService;
import io.gravitee.rest.api.service.InstanceService;
import io.gravitee.rest.api.service.common.ExecutionContext;
import io.gravitee.rest.api.service.exceptions.EventNotFoundException;
import io.gravitee.rest.api.service.exceptions.InstanceNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class InstanceServiceImpl implements InstanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceServiceImpl.class);
    private static final Pattern PROPERTY_SPLITTER = Pattern.compile(", ");

    private final EventService eventService;

    private final ObjectMapper objectMapper;

    public InstanceServiceImpl(EventService eventService, ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @Value("${gateway.unknown-expire-after:604800}") // default value : 7 days
    private long unknownExpireAfterInSec;

    private static final List<EventType> instancesAllState = new ArrayList<>();

    {
        instancesAllState.add(EventType.GATEWAY_STARTED);
        instancesAllState.add(EventType.GATEWAY_STOPPED);
    }

    private static final List<EventType> instancesRunningOnly = new ArrayList<>();

    {
        instancesRunningOnly.add(EventType.GATEWAY_STARTED);
    }

    @Override
    public Page<InstanceListItem> search(ExecutionContext executionContext, InstanceQuery query) {
        List<EventType> types;

        if (query.isIncludeStopped()) {
            types = instancesAllState;
        } else {
            types = instancesRunningOnly;
        }

        ExpiredPredicate filter = new ExpiredPredicate(Duration.ofSeconds(unknownExpireAfterInSec));
        long from = Instant.now().minus(unknownExpireAfterInSec, ChronoUnit.SECONDS).toEpochMilli();
        long to = Instant.now().toEpochMilli();

        return eventService.search(
            executionContext,
            types,
            query.getProperties(),
            from,
            to,
            query.getPage(),
            query.getSize(),
            new Function<EventEntity, InstanceListItem>() {
                @Override
                public InstanceListItem apply(EventEntity eventEntity) {
                    InstanceEntity instanceEntity = convert(eventEntity);

                    InstanceListItem item = new InstanceListItem();
                    item.setId(instanceEntity.getId());
                    item.setEvent(instanceEntity.getEvent());
                    item.setHostname(instanceEntity.getHostname());
                    item.setIp(instanceEntity.getIp());
                    item.setPort(instanceEntity.getPort());
                    item.setLastHeartbeatAt(instanceEntity.getLastHeartbeatAt());
                    item.setStartedAt(instanceEntity.getStartedAt());
                    item.setStoppedAt(instanceEntity.getStoppedAt());
                    item.setVersion(instanceEntity.getVersion());
                    item.setTags(instanceEntity.getTags());
                    item.setTenant(instanceEntity.getTenant());
                    item.setOperatingSystemName(instanceEntity.getSystemProperties().get("os.name"));
                    item.setState(instanceEntity.getState());

                    return item;
                }
            },
            filter,
            Collections.singletonList(executionContext.getEnvironmentId())
        );
    }

    @Override
    public InstanceEntity findById(final ExecutionContext executionContext, String instanceId) {
        final EventQuery query = new EventQuery();
        query.setId(instanceId);
        query.setTypes(instancesAllState);

        final Collection<EventEntity> events = eventService.search(executionContext, query);
        if (events == null || events.isEmpty()) {
            throw new InstanceNotFoundException(instanceId);
        }

        return convert(events.iterator().next());
    }

    @Override
    public InstanceEntity findByEvent(ExecutionContext executionContext, String eventId) {
        try {
            LOGGER.debug("Find instance by event ID: {}", eventId);

            EventEntity event = eventService.findById(executionContext, eventId);
            List<String> environments = extractProperty(event, Event.EventProperties.ENVIRONMENTS_HRIDS_PROPERTY.getValue());
            List<String> organizations = extractProperty(event, Event.EventProperties.ORGANIZATIONS_HRIDS_PROPERTY.getValue());

            return convert(event, environments, organizations);
        } catch (EventNotFoundException enfe) {
            throw new InstanceNotFoundException(eventId);
        }
    }

    @Override
    public List<InstanceEntity> findAllStarted(final ExecutionContext executionContext) {
        LOGGER.debug("Find started instances by event");

        final EventQuery query = new EventQuery();
        query.setTypes(instancesRunningOnly);

        Collection<EventEntity> events = eventService.search(executionContext, query);

        return events
            .stream()
            .map(event -> {
                List<String> environments = extractProperty(event, Event.EventProperties.ENVIRONMENTS_HRIDS_PROPERTY.getValue());
                List<String> organizations = extractProperty(event, Event.EventProperties.ORGANIZATIONS_HRIDS_PROPERTY.getValue());
                return convert(event, environments, organizations);
            })
            .collect(Collectors.toList());
    }

    private List<String> extractProperty(EventEntity event, String property) {
        final String extractedProperty = event.getProperties().get(property);

        return extractedProperty == null
            ? List.of()
            : Stream.of(PROPERTY_SPLITTER.split(extractedProperty)).filter(StringUtils::hasText).collect(Collectors.toList());
    }

    private InstanceEntity convert(EventEntity event) {
        return convert(event, null, null);
    }

    private InstanceEntity convert(EventEntity event, List<String> environments, List<String> organizations) {
        Instant nowMinusXMinutes = Instant.now().minus(5, ChronoUnit.MINUTES);

        Map<String, String> props = event.getProperties();
        InstanceEntity instance = new InstanceEntity(props.get("id"));
        instance.setEvent(event.getId());
        if (!isBlank(props.get("last_heartbeat_at"))) {
            instance.setLastHeartbeatAt(new Date(Long.parseLong(props.get("last_heartbeat_at"))));
        }
        if (!isBlank(props.get("started_at"))) {
            instance.setStartedAt(new Date(Long.parseLong(props.get("started_at"))));
        }
        instance.setEnvironments(event.getEnvironments());
        instance.setEnvironmentsHrids(environments);
        instance.setOrganizationsHrids(organizations);

        if (event.getPayload() != null) {
            try {
                InstanceInfo info = objectMapper.readValue(event.getPayload(), InstanceInfo.class);
                instance.setHostname(info.getHostname());
                instance.setIp(info.getIp());
                instance.setPort(info.getPort());
                instance.setTenant(info.getTenant());
                instance.setVersion(info.getVersion());
                instance.setTags(info.getTags());
                instance.setSystemProperties(info.getSystemProperties());
                instance.setPlugins(info.getPlugins());
            } catch (IOException ioe) {
                LOGGER.error("Unexpected error while getting instance data from event payload", ioe);
            }
        }

        if (event.getType() == EventType.GATEWAY_STARTED) {
            // If last heartbeat timestamp is < now - 5m, set as unknown state
            if (
                instance.getLastHeartbeatAt() == null ||
                Instant.ofEpochMilli(instance.getLastHeartbeatAt().getTime()).isBefore(nowMinusXMinutes)
            ) {
                instance.setState(InstanceState.UNKNOWN);
            } else {
                instance.setState(InstanceState.STARTED);
            }
        } else {
            instance.setState(InstanceState.STOPPED);
            if (!isBlank(props.get("stopped_at"))) {
                instance.setStoppedAt(new Date(Long.parseLong(props.get("stopped_at"))));
            }
        }

        return instance;
    }

    private static class InstanceInfo {

        private String id;
        private String version;
        private List<String> tags;
        private Set<PluginEntity> plugins;
        private String hostname;
        private String ip;
        private String port;
        private String tenant;
        private Map<String, String> systemProperties;

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public Map<String, String> getSystemProperties() {
            return systemProperties;
        }

        public void setSystemProperties(Map<String, String> systemProperties) {
            this.systemProperties = systemProperties;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Set<PluginEntity> getPlugins() {
            return plugins;
        }

        public void setPlugins(Set<PluginEntity> plugins) {
            this.plugins = plugins;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

    public static final class ExpiredPredicate implements Predicate<InstanceListItem> {

        private Duration threshold;

        public ExpiredPredicate(Duration threshold) {
            this.threshold = threshold;
        }

        @Override
        public boolean test(InstanceListItem instance) {
            boolean result = true;
            if (instance.getState().equals(InstanceState.UNKNOWN)) {
                Instant now = Instant.now();
                result = (now.toEpochMilli() - instance.getLastHeartbeatAt().getTime() <= threshold.toMillis());
            }
            return result;
        }
    }
}
