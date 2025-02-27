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
package io.gravitee.rest.api.management.rest.filter;

import io.gravitee.rest.api.management.rest.security.Permission;
import io.gravitee.rest.api.management.rest.security.Permissions;
import io.gravitee.rest.api.service.PermissionService;
import io.gravitee.rest.api.service.common.ExecutionContext;
import io.gravitee.rest.api.service.common.GraviteeContext;
import io.gravitee.rest.api.service.exceptions.ForbiddenAccessException;
import io.gravitee.rest.api.service.exceptions.UnauthorizedAccessException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Provider
@Priority(200)
public class PermissionsFilter implements ContainerRequestFilter {

    @Context
    protected ResourceInfo resourceInfo;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private PermissionService permissionService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        findRequiredPermissions()
            .ifPresent(requiredPermissions -> {
                mustBeAuthenticated();
                filter(requiredPermissions, requestContext, GraviteeContext.getExecutionContext());
            });
    }

    protected void filter(Permissions permissions, ContainerRequestContext requestContext, ExecutionContext executionContext) {
        Stream
            .of(permissions.value())
            .filter(permission -> hasPermission(permission, requestContext, executionContext))
            .findAny()
            .orElseThrow(ForbiddenAccessException::new);
    }

    private boolean hasPermission(Permission permission, ContainerRequestContext requestContext, ExecutionContext executionContext) {
        switch (permission.value().getScope()) {
            case ORGANIZATION:
                return hasPermission(executionContext, permission, executionContext.getOrganizationId());
            case ENVIRONMENT:
                return (
                    executionContext.hasEnvironmentId() && hasPermission(executionContext, permission, executionContext.getEnvironmentId())
                );
            case APPLICATION:
                return hasPermission(executionContext, permission, getApplicationId(requestContext));
            case API:
                return hasPermission(executionContext, permission, getApiId(requestContext));
            case GROUP:
                return hasPermission(executionContext, permission, getGroupId(requestContext));
            default:
                return false;
        }
    }

    private boolean hasPermission(final ExecutionContext executionContext, Permission permission, String referenceId) {
        return permissionService.hasPermission(executionContext, permission.value(), referenceId, permission.acls());
    }

    private String getGroupId(ContainerRequestContext requestContext) {
        return getId("group", requestContext);
    }

    private String getApiId(ContainerRequestContext requestContext) {
        return getId("api", requestContext);
    }

    private String getApplicationId(ContainerRequestContext requestContext) {
        return getId("application", requestContext);
    }

    private String getId(String key, ContainerRequestContext requestContext) {
        List<String> pathParams = requestContext.getUriInfo().getPathParameters().get(key);
        if (pathParams != null) {
            return pathParams.iterator().next();
        } else {
            List<String> queryParams = requestContext.getUriInfo().getQueryParameters().get(key);
            if (queryParams != null) {
                return queryParams.iterator().next();
            }
        }
        return null;
    }

    private Optional<Permissions> findRequiredPermissions() {
        return Optional
            .ofNullable(resourceInfo.getResourceMethod().getDeclaredAnnotation(Permissions.class))
            .or(() -> Optional.ofNullable(resourceInfo.getResourceClass().getDeclaredAnnotation(Permissions.class)));
    }

    private void mustBeAuthenticated() {
        Principal principal = securityContext.getUserPrincipal();
        if (principal == null) {
            throw new UnauthorizedAccessException();
        }
    }
}
