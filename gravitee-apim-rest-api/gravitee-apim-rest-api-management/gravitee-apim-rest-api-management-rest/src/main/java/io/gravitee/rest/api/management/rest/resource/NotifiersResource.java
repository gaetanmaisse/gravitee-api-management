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
package io.gravitee.rest.api.management.rest.resource;

import io.gravitee.common.http.MediaType;
import io.gravitee.rest.api.management.rest.security.Permission;
import io.gravitee.rest.api.management.rest.security.Permissions;
import io.gravitee.rest.api.model.NotifierEntity;
import io.gravitee.rest.api.model.NotifierListItem;
import io.gravitee.rest.api.model.permissions.RolePermission;
import io.gravitee.rest.api.model.permissions.RolePermissionAction;
import io.gravitee.rest.api.service.NotifierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

/**
 * Defines the REST resources to manage {@link io.gravitee.notifier.api.Notifier} plugins.
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "Plugins")
public class NotifiersResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private NotifierService notifierService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List notifier plugins", description = "User must have the MANAGEMENT_API[READ] permission to use this service")
    @ApiResponse(
        responseCode = "200",
        description = "List of notifiers",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            array = @ArraySchema(schema = @Schema(implementation = NotifierListItem.class))
        )
    )
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @Permissions({ @Permission(value = RolePermission.ENVIRONMENT_API, acls = RolePermissionAction.READ) })
    public Collection<NotifierListItem> getNotifiers(@QueryParam("expand") List<String> expand) {
        Stream<NotifierListItem> stream = notifierService.findAll().stream().map(this::convert);

        if (expand != null && !expand.isEmpty()) {
            for (String s : expand) {
                switch (s) {
                    case "schema":
                        stream =
                            stream.map(item -> {
                                item.setSchema(notifierService.getSchema(item.getId()));
                                return item;
                            });
                        break;
                    default:
                        break;
                }
            }
        }

        return stream.sorted(Comparator.comparing(NotifierListItem::getName)).collect(Collectors.toList());
    }

    @Path("{notifier}")
    public NotifierResource getNotifierResource() {
        return resourceContext.getResource(NotifierResource.class);
    }

    private NotifierListItem convert(NotifierEntity notifier) {
        NotifierListItem item = new NotifierListItem();

        item.setId(notifier.getId());
        item.setName(notifier.getName());
        item.setDescription(notifier.getDescription());
        item.setVersion(notifier.getVersion());

        return item;
    }
}
