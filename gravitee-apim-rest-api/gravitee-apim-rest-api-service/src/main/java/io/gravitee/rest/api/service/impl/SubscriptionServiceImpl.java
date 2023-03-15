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

import io.gravitee.common.data.domain.Page;
import io.gravitee.common.event.EventManager;
import io.gravitee.definition.model.DefinitionVersion;
import io.gravitee.definition.model.v4.listener.ListenerType;
import io.gravitee.definition.model.v4.plan.PlanSecurity;
import io.gravitee.definition.model.v4.plan.PlanStatus;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.SubscriptionRepository;
import io.gravitee.repository.management.api.search.Order;
import io.gravitee.repository.management.api.search.SubscriptionCriteria;
import io.gravitee.repository.management.api.search.builder.PageableBuilder;
import io.gravitee.repository.management.model.ApplicationStatus;
import io.gravitee.repository.management.model.ApplicationType;
import io.gravitee.repository.management.model.Audit;
import io.gravitee.repository.management.model.Plan;
import io.gravitee.repository.management.model.Subscription;
import io.gravitee.rest.api.model.ApiKeyEntity;
import io.gravitee.rest.api.model.ApplicationEntity;
import io.gravitee.rest.api.model.NewSubscriptionEntity;
import io.gravitee.rest.api.model.PageEntity;
import io.gravitee.rest.api.model.PrimaryOwnerEntity;
import io.gravitee.rest.api.model.ProcessSubscriptionEntity;
import io.gravitee.rest.api.model.SubscriptionConsumerStatus;
import io.gravitee.rest.api.model.SubscriptionEntity;
import io.gravitee.rest.api.model.SubscriptionStatus;
import io.gravitee.rest.api.model.TransferSubscriptionEntity;
import io.gravitee.rest.api.model.UpdateSubscriptionConfigurationEntity;
import io.gravitee.rest.api.model.UpdateSubscriptionEntity;
import io.gravitee.rest.api.model.UserEntity;
import io.gravitee.rest.api.model.api.ApiEntrypointEntity;
import io.gravitee.rest.api.model.application.ApplicationListItem;
import io.gravitee.rest.api.model.common.Pageable;
import io.gravitee.rest.api.model.pagedresult.Metadata;
import io.gravitee.rest.api.model.parameters.Key;
import io.gravitee.rest.api.model.parameters.ParameterReferenceType;
import io.gravitee.rest.api.model.subscription.SubscriptionMetadataQuery;
import io.gravitee.rest.api.model.subscription.SubscriptionQuery;
import io.gravitee.rest.api.model.v4.api.ApiModel;
import io.gravitee.rest.api.model.v4.api.GenericApiEntity;
import io.gravitee.rest.api.model.v4.api.GenericApiModel;
import io.gravitee.rest.api.model.v4.plan.GenericPlanEntity;
import io.gravitee.rest.api.model.v4.plan.PlanSecurityType;
import io.gravitee.rest.api.model.v4.plan.PlanValidationType;
import io.gravitee.rest.api.service.ApiKeyService;
import io.gravitee.rest.api.service.ApplicationService;
import io.gravitee.rest.api.service.AuditService;
import io.gravitee.rest.api.service.GroupService;
import io.gravitee.rest.api.service.NotifierService;
import io.gravitee.rest.api.service.PageService;
import io.gravitee.rest.api.service.ParameterService;
import io.gravitee.rest.api.service.SubscriptionService;
import io.gravitee.rest.api.service.UserService;
import io.gravitee.rest.api.service.common.ExecutionContext;
import io.gravitee.rest.api.service.common.UuidString;
import io.gravitee.rest.api.service.converter.ApplicationConverter;
import io.gravitee.rest.api.service.event.SubscriptionEvent;
import io.gravitee.rest.api.service.exceptions.ApplicationArchivedException;
import io.gravitee.rest.api.service.exceptions.PlanAlreadyClosedException;
import io.gravitee.rest.api.service.exceptions.PlanAlreadySubscribedException;
import io.gravitee.rest.api.service.exceptions.PlanGeneralConditionAcceptedException;
import io.gravitee.rest.api.service.exceptions.PlanGeneralConditionRevisionException;
import io.gravitee.rest.api.service.exceptions.PlanNotSubscribableException;
import io.gravitee.rest.api.service.exceptions.PlanNotSubscribableWithSharedApiKeyException;
import io.gravitee.rest.api.service.exceptions.PlanNotYetPublishedException;
import io.gravitee.rest.api.service.exceptions.PlanOAuth2OrJWTAlreadySubscribedException;
import io.gravitee.rest.api.service.exceptions.PlanRestrictedException;
import io.gravitee.rest.api.service.exceptions.SubscriptionAlreadyProcessedException;
import io.gravitee.rest.api.service.exceptions.SubscriptionConsumerStatusNotUpdatableException;
import io.gravitee.rest.api.service.exceptions.SubscriptionFailureException;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotClosableException;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotClosedException;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotFoundException;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotPausableException;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotPausedException;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotUpdatableException;
import io.gravitee.rest.api.service.exceptions.TechnicalManagementException;
import io.gravitee.rest.api.service.exceptions.TransferNotAllowedException;
import io.gravitee.rest.api.service.exceptions.UserNotFoundException;
import io.gravitee.rest.api.service.notification.ApiHook;
import io.gravitee.rest.api.service.notification.ApplicationHook;
import io.gravitee.rest.api.service.notification.NotificationParamsBuilder;
import io.gravitee.rest.api.service.v4.ApiEntrypointService;
import io.gravitee.rest.api.service.v4.ApiSearchService;
import io.gravitee.rest.api.service.v4.ApiTemplateService;
import io.gravitee.rest.api.service.v4.PlanSearchService;
import io.gravitee.rest.api.service.v4.validation.SubscriptionValidationService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.repository.management.model.Audit.AuditProperties.API;
import static io.gravitee.repository.management.model.Audit.AuditProperties.APPLICATION;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_CLOSED;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_CREATED;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_DELETED;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_PAUSED;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_PAUSED_BY_CONSUMER;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_RESUMED;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_RESUMED_BY_CONSUMER;
import static io.gravitee.repository.management.model.Subscription.AuditEvent.SUBSCRIPTION_UPDATED;
import static io.gravitee.repository.management.model.Subscription.Status.PENDING;
import static io.gravitee.rest.api.model.ApiKeyMode.EXCLUSIVE;
import static io.gravitee.rest.api.model.ApiKeyMode.UNSPECIFIED;
import static io.gravitee.rest.api.model.v4.plan.PlanValidationType.MANUAL;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class SubscriptionServiceImpl extends AbstractService implements SubscriptionService {

    private static final String SUBSCRIPTION_SYSTEM_VALIDATOR = "system";
    private static final String RFC_3339_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final FastDateFormat dateFormatter = FastDateFormat.getInstance(RFC_3339_DATE_FORMAT);
    private static final char separator = ';';
    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    @Autowired
    private PlanSearchService planSearchService;

    @Lazy
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApiSearchService apiSearchService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private NotifierService notifierService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private ParameterService parameterService;

    @Autowired
    private UserService userService;

    @Autowired
    private PageService pageService;

    @Autowired
    private ApplicationConverter applicationConverter;

    @Autowired
    private ApiEntrypointService apiEntrypointService;

    @Autowired
    private ApiTemplateService apiTemplateService;

    @Autowired
    private SubscriptionValidationService subscriptionValidationService;

    @Autowired
    private EventManager eventManager;

    @Override
    public SubscriptionEntity findById(String subscriptionId) {
        try {
            logger.debug("Find subscription by id : {}", subscriptionId);

            return subscriptionRepository
                .findById(subscriptionId)
                .map(this::convert)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to find a subscription using its ID: {}", subscriptionId, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to find a subscription using its ID: %s", subscriptionId),
                ex
            );
        }
    }

    @Override
    public Set<SubscriptionEntity> findByIdIn(Collection<String> subscriptionIds) {
        try {
            return subscriptionRepository.findByIdIn(subscriptionIds).stream().map(this::convert).collect(toSet());
        } catch (TechnicalException e) {
            logger.error("An error occurs while trying to find subscriptions using IDs [{}]", subscriptionIds, e);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to find subscriptions using IDs [%s]", subscriptionIds),
                e
            );
        }
    }

    @Override
    public Collection<SubscriptionEntity> findByApplicationAndPlan(
        final ExecutionContext executionContext,
        String application,
        String plan
    ) {
        logger.debug("Find subscriptions by application {} and plan {}", application, plan);

        SubscriptionQuery query = new SubscriptionQuery();
        if (plan != null) {
            query.setPlan(plan);
        }

        if (application != null && !application.trim().isEmpty()) {
            query.setApplication(application);
        } else if (isAuthenticated()) {
            Set<ApplicationListItem> applications = applicationService.findByUser(executionContext, getAuthenticatedUsername());
            query.setApplications(applications.stream().map(ApplicationListItem::getId).collect(toList()));
        }

        return search(executionContext, query);
    }

    @Override
    public Collection<SubscriptionEntity> findByApi(final ExecutionContext executionContext, String api) {
        logger.debug("Find subscriptions by api {}", api);

        SubscriptionQuery query = new SubscriptionQuery();
        query.setApi(api);

        return search(executionContext, query);
    }

    @Override
    public Collection<SubscriptionEntity> findByPlan(final ExecutionContext executionContext, String plan) {
        logger.debug("Find subscriptions by plan {}", plan);

        SubscriptionQuery query = new SubscriptionQuery();
        query.setPlan(plan);

        return search(executionContext, query);
    }

    @Override
    public SubscriptionEntity create(final ExecutionContext executionContext, NewSubscriptionEntity newSubscriptionEntity) {
        return create(executionContext, newSubscriptionEntity, null);
    }

    @Override
    public SubscriptionEntity create(
        final ExecutionContext executionContext,
        NewSubscriptionEntity newSubscriptionEntity,
        String customApiKey
    ) {
        String plan = newSubscriptionEntity.getPlan();
        String application = newSubscriptionEntity.getApplication();

        try {
            logger.debug("Create a new subscription for plan {} and application {}", plan, application);

            if (newSubscriptionEntity.getConfiguration() != null && !newSubscriptionEntity.getConfiguration().isEmpty()) {
                subscriptionValidationService.validateAndSanitize(newSubscriptionEntity);
            }

            GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, plan);

            if (genericPlanEntity.getPlanStatus() == PlanStatus.DEPRECATED) {
                throw new PlanNotSubscribableException(plan);
            }

            if (genericPlanEntity.getPlanStatus() == PlanStatus.CLOSED) {
                throw new PlanAlreadyClosedException(plan);
            }

            if (genericPlanEntity.getPlanStatus() == PlanStatus.STAGING) {
                throw new PlanNotYetPublishedException(plan);
            }

            PlanSecurity planSecurity = genericPlanEntity.getPlanSecurity();
            PlanSecurityType planSecurityType = PlanSecurityType.valueOfLabel(planSecurity.getType());
            if (planSecurityType == PlanSecurityType.KEY_LESS) {
                throw new PlanNotSubscribableException("A key_less plan is not subscribable !");
            }

            if (genericPlanEntity.getExcludedGroups() != null && !genericPlanEntity.getExcludedGroups().isEmpty()) {
                final boolean userAuthorizedToAccessApiData = groupService.isUserAuthorizedToAccessApiData(
                    apiSearchService.findGenericById(executionContext, genericPlanEntity.getApiId()),
                    genericPlanEntity.getExcludedGroups(),
                    getAuthenticatedUsername()
                );
                if (!userAuthorizedToAccessApiData && !isEnvironmentAdmin()) {
                    throw new PlanRestrictedException(plan);
                }
            }

            if (genericPlanEntity.getGeneralConditions() != null && !genericPlanEntity.getGeneralConditions().isEmpty()) {
                if (
                    (
                        Boolean.FALSE.equals(newSubscriptionEntity.getGeneralConditionsAccepted()) ||
                        (newSubscriptionEntity.getGeneralConditionsContentRevision() == null)
                    )
                ) {
                    throw new PlanGeneralConditionAcceptedException(genericPlanEntity.getName());
                }

                PageEntity generalConditions = pageService.findById(genericPlanEntity.getGeneralConditions());
                if (!generalConditions.getContentRevisionId().equals(newSubscriptionEntity.getGeneralConditionsContentRevision())) {
                    throw new PlanGeneralConditionRevisionException(genericPlanEntity.getName());
                }
            }

            ApplicationEntity applicationEntity = applicationService.findById(executionContext, application);
            if (ApplicationStatus.ARCHIVED.name().equals(applicationEntity.getStatus())) {
                throw new ApplicationArchivedException(applicationEntity.getName());
            }

            // Check existing subscriptions
            List<Subscription> subscriptions = subscriptionRepository.search(
                new SubscriptionCriteria.Builder()
                    .applications(Collections.singleton(application))
                    .apis(Collections.singleton(genericPlanEntity.getApiId()))
                    .build()
            );

            if (!subscriptions.isEmpty()) {
                Predicate<Subscription> onlyValidSubs = subscription ->
                    subscription.getStatus() != Subscription.Status.REJECTED && subscription.getStatus() != Subscription.Status.CLOSED;

                // First, check that there is no subscription to the same plan
                long subscriptionCount = subscriptions
                    .stream()
                    .filter(onlyValidSubs)
                    .filter(subscription -> subscription.getPlan().equals(plan))
                    .count();

                if (subscriptionCount > 0) {
                    throw new PlanAlreadySubscribedException(plan);
                }

                // Then, if user is subscribing to an oauth2 or jwt plan.
                // Check that there is no existing subscription based on an OAuth2 or JWT plan
                if (planSecurityType == PlanSecurityType.OAUTH2 || planSecurityType == PlanSecurityType.JWT) {
                    long count = subscriptions
                        .stream()
                        .filter(onlyValidSubs)
                        .map(Subscription::getPlan)
                        .distinct()
                        .map(plan1 -> planSearchService.findById(executionContext, plan1))
                        .filter(
                            subPlan -> {
                                PlanSecurity subPlanSecurity = subPlan.getPlanSecurity();
                                PlanSecurityType subPlanSecurityType = PlanSecurityType.valueOfLabel(subPlanSecurity.getType());
                                return subPlanSecurityType == PlanSecurityType.OAUTH2 || subPlanSecurityType == PlanSecurityType.JWT;
                            }
                        )
                        .count();

                    if (count > 0) {
                        throw new PlanOAuth2OrJWTAlreadySubscribedException(
                            "An other OAuth2 or JWT plan is already subscribed by the same application."
                        );
                    }
                }

                if (planSecurityType == PlanSecurityType.API_KEY && applicationEntity.hasApiKeySharedMode()) {
                    long count = subscriptions
                        .stream()
                        .filter(onlyValidSubs)
                        .map(Subscription::getPlan)
                        .distinct()
                        .map(plan1 -> planSearchService.findById(executionContext, plan1))
                        .filter(
                            subPlan -> {
                                PlanSecurity subPlanSecurity = subPlan.getPlanSecurity();
                                PlanSecurityType subPlanSecurityType = PlanSecurityType.valueOfLabel(subPlanSecurity.getType());
                                return subPlanSecurityType == PlanSecurityType.API_KEY;
                            }
                        )
                        .count();

                    if (count > 0) {
                        throw new PlanNotSubscribableWithSharedApiKeyException();
                    }
                }
            }

            // Extract the client_id according to the application type
            String clientId = null;
            if (planSecurityType == PlanSecurityType.OAUTH2 || planSecurityType == PlanSecurityType.JWT) {
                if (ApplicationType.SIMPLE.name().equals(applicationEntity.getType())) {
                    clientId =
                        (applicationEntity.getSettings() != null && applicationEntity.getSettings().getApp() != null)
                            ? applicationEntity.getSettings().getApp().getClientId()
                            : null;
                } else {
                    clientId =
                        (applicationEntity.getSettings() != null && applicationEntity.getSettings().getoAuthClient() != null)
                            ? applicationEntity.getSettings().getoAuthClient().getClientId()
                            : null;
                }

                // Check that the application contains a client_id
                if (clientId == null || clientId.trim().isEmpty()) {
                    throw new PlanNotSubscribableException("A client_id is required to subscribe to an OAuth2 or JWT plan.");
                }
            }

            updateApplicationApiKeyMode(executionContext, planSecurityType, applicationEntity);

            Subscription subscription = new Subscription();
            subscription.setPlan(plan);
            subscription.setId(UuidString.generateRandom());
            subscription.setApplication(application);
            subscription.setCreatedAt(new Date());
            subscription.setUpdatedAt(subscription.getCreatedAt());
            subscription.setStatus(Subscription.Status.PENDING);
            subscription.setRequest(newSubscriptionEntity.getRequest());
            subscription.setSubscribedBy(getAuthenticatedUser().getUsername());
            subscription.setClientId(clientId);
            subscription.setMetadata(newSubscriptionEntity.getMetadata());
            subscription.setConfiguration(newSubscriptionEntity.getConfiguration());
            String apiId = genericPlanEntity.getApiId();
            subscription.setApi(apiId);
            subscription.setGeneralConditionsAccepted(newSubscriptionEntity.getGeneralConditionsAccepted());
            if (newSubscriptionEntity.getGeneralConditionsContentRevision() != null) {
                subscription.setGeneralConditionsContentRevision(newSubscriptionEntity.getGeneralConditionsContentRevision().getRevision());
                subscription.setGeneralConditionsContentPageId(newSubscriptionEntity.getGeneralConditionsContentRevision().getPageId());
            }

            if (planSecurityType == PlanSecurityType.SUBSCRIPTION) {
                subscription.setType(Subscription.Type.SUBSCRIPTION);
            } else {
                subscription.setType(Subscription.Type.STANDARD);
            }

            subscription = subscriptionRepository.create(subscription);

            createAudit(executionContext, apiId, application, SUBSCRIPTION_CREATED, subscription.getCreatedAt(), null, subscription);

            final GenericApiModel api = apiTemplateService.findByIdForTemplates(executionContext, apiId);
            final PrimaryOwnerEntity apiOwner = api.getPrimaryOwner();

            String managementURL = parameterService.find(executionContext, Key.MANAGEMENT_URL, ParameterReferenceType.ORGANIZATION);

            String subscriptionsUrl = "";

            if (!StringUtils.isEmpty(managementURL)) {
                if (managementURL.endsWith("/")) {
                    managementURL = managementURL.substring(0, managementURL.length() - 1);
                }
                subscriptionsUrl =
                    managementURL +
                    "/#!/environments/" +
                    executionContext.getEnvironmentId() +
                    "/apis/" +
                    api.getId() +
                    "/subscriptions/" +
                    subscription.getId();
            }

            final Map<String, Object> params = new NotificationParamsBuilder()
                .api(api)
                .plan(genericPlanEntity)
                .application(applicationEntity)
                .owner(apiOwner)
                .subscription(convert(subscription))
                .subscriptionsUrl(subscriptionsUrl)
                .build();

            if (PlanValidationType.AUTO == genericPlanEntity.getPlanValidation()) {
                ProcessSubscriptionEntity process = new ProcessSubscriptionEntity();
                process.setId(subscription.getId());
                process.setAccepted(true);
                process.setStartingAt(new Date());
                process.setCustomApiKey(customApiKey);
                // Do process
                return process(executionContext, process, SUBSCRIPTION_SYSTEM_VALIDATOR);
            } else {
                notifierService.trigger(executionContext, ApiHook.SUBSCRIPTION_NEW, apiId, params);
                notifierService.trigger(executionContext, ApplicationHook.SUBSCRIPTION_NEW, application, params);
                return convert(subscription);
            }
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to subscribe to the plan {}", plan, ex);
            throw new TechnicalManagementException(String.format("An error occurs while trying to subscribe to the plan %s", plan), ex);
        }
    }

    private void updateApplicationApiKeyMode(
        final ExecutionContext executionContext,
        PlanSecurityType planSecurityType,
        ApplicationEntity application
    ) {
        if (
            planSecurityType == PlanSecurityType.API_KEY &&
            application.getApiKeyMode() == UNSPECIFIED &&
            countApiKeySubscriptions(executionContext, application) > 0
        ) {
            logger.debug("Force application {} Api Key mode to EXCLUSIVE, as it's his second subscription", application.getId());
            application.setApiKeyMode(EXCLUSIVE);
            applicationService.update(executionContext, application.getId(), applicationConverter.toUpdateApplicationEntity(application));
        }
    }

    @Override
    public SubscriptionEntity update(final ExecutionContext executionContext, UpdateSubscriptionEntity updateSubscription) {
        return update(executionContext, updateSubscription, null);
    }

    @Override
    public SubscriptionEntity update(
        final ExecutionContext executionContext,
        UpdateSubscriptionConfigurationEntity subscriptionConfigEntity
    ) {
        try {
            Subscription subscription = subscriptionRepository
                .findById(subscriptionConfigEntity.getSubscriptionId())
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionConfigEntity.getSubscriptionId()));

            if (subscription.getStatus() == Subscription.Status.CLOSED) {
                throw new SubscriptionNotUpdatableException(subscriptionConfigEntity.getSubscriptionId());
            }

            if (subscriptionConfigEntity.getConfiguration() != null && !subscriptionConfigEntity.getConfiguration().isEmpty()) {
                subscriptionValidationService.validateAndSanitize(subscriptionConfigEntity);
            }

            final GenericPlanEntity planEntity = planSearchService.findById(executionContext, subscription.getPlan());
            Subscription.Status newSubscriptionStatus = planEntity.getPlanValidation() == MANUAL ? PENDING : subscription.getStatus();

            Subscription previousSubscription = new Subscription(subscription);
            subscription.setConfiguration(subscriptionConfigEntity.getConfiguration());
            subscription.setUpdatedAt(new Date());
            subscription.setStatus(newSubscriptionStatus);
            // Recover from failure or keep previous consumer status
            subscription.setConsumerStatus(
                previousSubscription.getConsumerStatus().equals(Subscription.ConsumerStatus.FAILURE)
                    ? Subscription.ConsumerStatus.STARTED
                    : previousSubscription.getConsumerStatus()
            );
            subscription.setFailureCause(null);

            subscription = subscriptionRepository.update(subscription);

            createAudit(
                executionContext,
                planEntity.getApiId(),
                subscription.getApplication(),
                SUBSCRIPTION_UPDATED,
                subscription.getUpdatedAt(),
                previousSubscription,
                subscription
            );

            return convert(subscription);
        } catch (TechnicalException ex) {
            logger.error(
                "An error occurs while trying to update subscription {} configuration",
                subscriptionConfigEntity.getSubscriptionId(),
                ex
            );
            throw new TechnicalManagementException(
                String.format(
                    "An error occurs while trying to update subscription %s configuration",
                    subscriptionConfigEntity.getSubscriptionId()
                ),
                ex
            );
        }
    }

    @Override
    public SubscriptionEntity updateDaysToExpirationOnLastNotification(String subscriptionId, Integer value) {
        try {
            return subscriptionRepository
                .findById(subscriptionId)
                .map(
                    subscription -> {
                        subscription.setDaysToExpirationOnLastNotification(value);
                        try {
                            return subscriptionRepository.update(subscription);
                        } catch (TechnicalException ex) {
                            logger.error("An error occurs while trying to update subscription {}", subscriptionId, ex);
                            throw new TechnicalManagementException(
                                String.format("An error occurs while trying to update subscription %s", subscriptionId),
                                ex
                            );
                        }
                    }
                )
                .map(this::convert)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to update subscription {}", subscriptionId, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to update subscription %s", subscriptionId),
                ex
            );
        }
    }

    @Override
    public SubscriptionEntity update(
        final ExecutionContext executionContext,
        UpdateSubscriptionEntity updateSubscription,
        String clientId
    ) {
        try {
            logger.debug("Update subscription {}", updateSubscription.getId());

            Subscription subscription = subscriptionRepository
                .findById(updateSubscription.getId())
                .orElseThrow(() -> new SubscriptionNotFoundException(updateSubscription.getId()));

            if (subscription.getStatus() == Subscription.Status.ACCEPTED) {
                if (updateSubscription.getConfiguration() != null && !updateSubscription.getConfiguration().isEmpty()) {
                    subscriptionValidationService.validateAndSanitize(updateSubscription);
                }

                Subscription previousSubscription = new Subscription(subscription);
                subscription.setConfiguration(updateSubscription.getConfiguration());
                subscription.setMetadata(updateSubscription.getMetadata());
                subscription.setUpdatedAt(new Date());
                subscription.setStartingAt(updateSubscription.getStartingAt());
                subscription.setEndingAt(updateSubscription.getEndingAt());
                // Reset info about pre expiration notification as the expiration date has changed
                subscription.setDaysToExpirationOnLastNotification(null);
                if (clientId != null && subscription.getClientId() != null) {
                    subscription.setClientId(clientId);
                }

                subscription = subscriptionRepository.update(subscription);
                final GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());
                createAudit(
                    executionContext,
                    genericPlanEntity.getApiId(),
                    subscription.getApplication(),
                    SUBSCRIPTION_UPDATED,
                    subscription.getUpdatedAt(),
                    previousSubscription,
                    subscription
                );

                // Update the expiration date for not yet revoked api-keys relative to this subscription (except for shared API keys)
                Date endingAt = subscription.getEndingAt();
                PlanSecurity planSecurity = genericPlanEntity.getPlanSecurity();
                PlanSecurityType planSecurityType = PlanSecurityType.valueOfLabel(planSecurity.getType());
                if (planSecurityType == PlanSecurityType.API_KEY && endingAt != null) {
                    streamActiveApiKeys(executionContext, subscription.getId())
                        .filter(apiKey -> !apiKey.getApplication().hasApiKeySharedMode())
                        .forEach(
                            apiKey -> {
                                apiKey.setExpireAt(endingAt);
                                apiKeyService.update(executionContext, apiKey);
                            }
                        );
                }

                return convert(subscription);
            }

            throw new SubscriptionNotUpdatableException(updateSubscription.getId());
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to update subscription {}", updateSubscription.getId(), ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to update subscription %s", updateSubscription.getId()),
                ex
            );
        }
    }

    @Override
    public SubscriptionEntity process(
        final ExecutionContext executionContext,
        ProcessSubscriptionEntity processSubscription,
        String userId
    ) {
        try {
            logger.debug("Subscription {} processed by {}", processSubscription.getId(), userId);

            Subscription subscription = subscriptionRepository
                .findById(processSubscription.getId())
                .orElseThrow(() -> new SubscriptionNotFoundException(processSubscription.getId()));

            Subscription previousSubscription = new Subscription(subscription);

            if (subscription.getStatus() != Subscription.Status.PENDING) {
                throw new SubscriptionAlreadyProcessedException(subscription.getId());
            }

            GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());

            if (genericPlanEntity.getPlanStatus() == PlanStatus.CLOSED) {
                throw new PlanAlreadyClosedException(genericPlanEntity.getId());
            }

            subscription.setProcessedBy(userId);
            Date now = new Date();
            subscription.setProcessedAt(now);
            subscription.setUpdatedAt(now);

            if (processSubscription.isAccepted()) {
                subscription.setStatus(Subscription.Status.ACCEPTED);
                subscription.setStartingAt(
                    (processSubscription.getStartingAt() != null) ? processSubscription.getStartingAt() : new Date()
                );
                subscription.setEndingAt(processSubscription.getEndingAt());
                subscription.setReason(processSubscription.getReason());
            } else {
                subscription.setStatus(Subscription.Status.REJECTED);
                subscription.setReason(processSubscription.getReason());
                subscription.setClosedAt(new Date());
            }

            final String apiId = genericPlanEntity.getApiId();
            final GenericApiModel genericApiModel = apiTemplateService.findByIdForTemplates(executionContext, apiId);

            ApplicationEntity application = applicationService.findById(executionContext, subscription.getApplication());

            SubscriptionEntity subscriptionEntity = convert(subscription);
            PlanSecurity planSecurity = genericPlanEntity.getPlanSecurity();
            PlanSecurityType planSecurityType = PlanSecurityType.valueOfLabel(planSecurity.getType());
            if (planSecurityType == PlanSecurityType.API_KEY && subscriptionEntity.getStatus() == SubscriptionStatus.ACCEPTED) {
                apiKeyService.generate(executionContext, application, subscriptionEntity, processSubscription.getCustomApiKey());
            }

            subscription = subscriptionRepository.update(subscription);

            final PrimaryOwnerEntity owner = application.getPrimaryOwner();
            createAudit(
                executionContext,
                apiId,
                subscription.getApplication(),
                SUBSCRIPTION_UPDATED,
                subscription.getUpdatedAt(),
                previousSubscription,
                subscription
            );

            final Map<String, Object> params = new NotificationParamsBuilder()
                .owner(owner)
                .application(application)
                .api(genericApiModel)
                .plan(genericPlanEntity)
                .subscription(subscriptionEntity)
                .build();
            if (subscription.getStatus() == Subscription.Status.ACCEPTED) {
                notifierService.trigger(executionContext, ApiHook.SUBSCRIPTION_ACCEPTED, apiId, params);
                notifierService.trigger(executionContext, ApplicationHook.SUBSCRIPTION_ACCEPTED, application.getId(), params);
                searchSubscriberEmail(executionContext, subscriptionEntity)
                    .ifPresent(
                        subscriberEmail -> {
                            if (
                                !notifierService.hasEmailNotificationFor(
                                    executionContext,
                                    ApplicationHook.SUBSCRIPTION_ACCEPTED,
                                    application.getId(),
                                    params,
                                    subscriberEmail
                                )
                            ) {
                                notifierService.triggerEmail(
                                    executionContext,
                                    ApplicationHook.SUBSCRIPTION_ACCEPTED,
                                    apiId,
                                    params,
                                    subscriberEmail
                                );
                            }
                        }
                    );
            } else {
                notifierService.trigger(executionContext, ApiHook.SUBSCRIPTION_REJECTED, apiId, params);
                notifierService.trigger(executionContext, ApplicationHook.SUBSCRIPTION_REJECTED, application.getId(), params);
                searchSubscriberEmail(executionContext, subscriptionEntity)
                    .ifPresent(
                        subscriberEmail -> {
                            if (
                                !notifierService.hasEmailNotificationFor(
                                    executionContext,
                                    ApplicationHook.SUBSCRIPTION_REJECTED,
                                    application.getId(),
                                    params,
                                    subscriberEmail
                                )
                            ) {
                                notifierService.triggerEmail(
                                    executionContext,
                                    ApplicationHook.SUBSCRIPTION_REJECTED,
                                    apiId,
                                    params,
                                    subscriberEmail
                                );
                            }
                        }
                    );
            }

            eventManager.publishEvent(SubscriptionEvent.PROCESSED, subscriptionEntity);

            return subscriptionEntity;
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to process subscription {} by {}", processSubscription.getId(), userId, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to process subscription %s by %s", processSubscription.getId(), userId),
                ex
            );
        }
    }

    private Optional<String> searchSubscriberEmail(final ExecutionContext executionContext, SubscriptionEntity subscriptionEntity) {
        try {
            UserEntity subscriber = userService.findById(executionContext, subscriptionEntity.getSubscribedBy());
            return Optional.ofNullable(subscriber.getEmail());
        } catch (UserNotFoundException e) {
            logger.warn("Subscriber '{}' not found, unable to retrieve email", subscriptionEntity.getSubscribedBy());
            return Optional.empty();
        }
    }

    @Override
    public SubscriptionEntity close(final ExecutionContext executionContext, String subscriptionId) {
        try {
            logger.debug("Close subscription {}", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            switch (subscription.getStatus()) {
                case ACCEPTED:
                case PAUSED:
                    Subscription previousSubscription = new Subscription(subscription);
                    final Date now = new Date();
                    subscription.setUpdatedAt(now);
                    subscription.setStatus(Subscription.Status.CLOSED);

                    subscription.setClosedAt(new Date());

                    subscription = subscriptionRepository.update(subscription);

                    // Send an email to subscriber
                    final ApplicationEntity application = applicationService.findById(executionContext, subscription.getApplication());
                    final GenericPlanEntity plan = planSearchService.findById(executionContext, subscription.getPlan());
                    String apiId = plan.getApiId();
                    final GenericApiModel genericApiModel = apiTemplateService.findByIdForTemplates(executionContext, apiId);
                    final PrimaryOwnerEntity owner = application.getPrimaryOwner();
                    final Map<String, Object> params = new NotificationParamsBuilder()
                        .owner(owner)
                        .api(genericApiModel)
                        .plan(plan)
                        .application(application)
                        .build();

                    notifierService.trigger(executionContext, ApiHook.SUBSCRIPTION_CLOSED, apiId, params);
                    notifierService.trigger(executionContext, ApplicationHook.SUBSCRIPTION_CLOSED, application.getId(), params);
                    createAudit(
                        executionContext,
                        apiId,
                        subscription.getApplication(),
                        SUBSCRIPTION_CLOSED,
                        subscription.getUpdatedAt(),
                        previousSubscription,
                        subscription
                    );

                    // revoke associated active API Keys (if they are not shared)
                    if (!application.hasApiKeySharedMode()) {
                        streamActiveApiKeys(executionContext, subscription.getId())
                            .forEach(apiKey -> apiKeyService.revoke(executionContext, apiKey, false));
                    }

                    return convert(subscription);
                case PENDING:
                    ProcessSubscriptionEntity processSubscriptionEntity = new ProcessSubscriptionEntity();
                    processSubscriptionEntity.setId(subscription.getId());
                    processSubscriptionEntity.setAccepted(false);
                    processSubscriptionEntity.setReason("Subscription has been closed.");
                    return this.process(executionContext, processSubscriptionEntity, getAuthenticatedUsername());
                default:
                    throw new SubscriptionNotClosableException(subscription);
            }
        } catch (TechnicalException ex) {
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to close subscription %s", subscriptionId),
                ex
            );
        }
    }

    @Override
    public SubscriptionEntity fail(String subscriptionId, String failureCause) {
        try {
            logger.debug("Fail subscription {}", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            final Date now = new Date();
            subscription.setUpdatedAt(now);
            subscription.setConsumerPausedAt(null);
            subscription.setConsumerStatus(Subscription.ConsumerStatus.FAILURE);
            subscription.setFailureCause(failureCause);

            subscription = subscriptionRepository.update(subscription);

            return convert(subscription);
        } catch (TechnicalException ex) {
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to fail subscription %s", subscriptionId),
                ex
            );
        }
    }

    @Override
    public SubscriptionEntity pauseConsumer(ExecutionContext executionContext, String subscriptionId) {
        try {
            logger.debug("Pause subscription {} by consumer", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            final ApplicationEntity application = applicationService.findById(executionContext, subscription.getApplication());
            final GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());
            String apiId = genericPlanEntity.getApiId();
            final GenericApiModel genericApiModel = apiTemplateService.findByIdForTemplates(executionContext, apiId);
            validateConsumerStatus(subscription, genericApiModel);

            // Here, do not care about the status managed by the publisher. The subscription will be active on the gateway if consumerStatus == ACTIVE and status == ACCEPTED
            if (subscription.canBeStoppedByConsumer()) {
                Subscription previousSubscription = new Subscription(subscription);
                final Date now = new Date();
                subscription.setUpdatedAt(now);
                subscription.setConsumerPausedAt(now);
                subscription.setConsumerStatus(Subscription.ConsumerStatus.STOPPED);

                subscription = subscriptionRepository.update(subscription);

                createAudit(
                    executionContext,
                    apiId,
                    subscription.getApplication(),
                    SUBSCRIPTION_PAUSED_BY_CONSUMER,
                    subscription.getUpdatedAt(),
                    previousSubscription,
                    subscription
                );

                // active API Keys are automatically paused (if they are not shared)
                pauseNonSharedApiKeys(executionContext, subscription, application);
                return convert(subscription);
            }
            throw new SubscriptionNotPausableException(subscription);
        } catch (TechnicalException ex) {
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to pause subscription %s by consumer", subscriptionId),
                ex
            );
        }
    }

    private void pauseNonSharedApiKeys(ExecutionContext executionContext, Subscription subscription, ApplicationEntity application) {
        if (!application.hasApiKeySharedMode()) {
            streamActiveApiKeys(executionContext, subscription.getId())
                .forEach(
                    apiKey -> {
                        apiKey.setPaused(true);
                        apiKeyService.update(executionContext, apiKey);
                    }
                );
        }
    }

    @Override
    public SubscriptionEntity pause(final ExecutionContext executionContext, String subscriptionId) {
        try {
            logger.debug("Pause subscription {}", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            if (subscription.getStatus() == Subscription.Status.ACCEPTED) {
                Subscription previousSubscription = new Subscription(subscription);
                final Date now = new Date();
                subscription.setUpdatedAt(now);
                subscription.setPausedAt(now);
                subscription.setStatus(Subscription.Status.PAUSED);

                subscription = subscriptionRepository.update(subscription);

                // Send an email to subscriber
                final ApplicationEntity application = applicationService.findById(executionContext, subscription.getApplication());
                final GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());
                String apiId = genericPlanEntity.getApiId();
                final GenericApiModel genericApiModel = apiTemplateService.findByIdForTemplates(executionContext, apiId);
                final PrimaryOwnerEntity owner = application.getPrimaryOwner();
                final Map<String, Object> params = new NotificationParamsBuilder()
                    .owner(owner)
                    .api(genericApiModel)
                    .plan(genericPlanEntity)
                    .application(application)
                    .build();

                notifierService.trigger(executionContext, ApiHook.SUBSCRIPTION_PAUSED, apiId, params);
                notifierService.trigger(executionContext, ApplicationHook.SUBSCRIPTION_PAUSED, application.getId(), params);
                createAudit(
                    executionContext,
                    apiId,
                    subscription.getApplication(),
                    SUBSCRIPTION_PAUSED,
                    subscription.getUpdatedAt(),
                    previousSubscription,
                    subscription
                );

                // active API Keys are automatically paused (if they are not shared)
                pauseNonSharedApiKeys(executionContext, subscription, application);

                return convert(subscription);
            }

            throw new SubscriptionNotPausableException(subscription);
        } catch (TechnicalException ex) {
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to pause subscription %s", subscriptionId),
                ex
            );
        }
    }

    @Override
    public SubscriptionEntity resumeConsumer(final ExecutionContext executionContext, String subscriptionId) {
        try {
            logger.debug("Resume subscription by {} by consumer", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            final GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());
            String apiId = genericPlanEntity.getApiId();
            final GenericApiModel genericApiModel = apiTemplateService.findByIdForTemplates(executionContext, apiId);
            validateConsumerStatus(subscription, genericApiModel);

            if (subscription.canBeStartedByConsumer()) {
                Subscription previousSubscription = new Subscription(subscription);
                final Date now = new Date();
                subscription.setUpdatedAt(now);
                subscription.setConsumerPausedAt(null);
                subscription.setConsumerStatus(Subscription.ConsumerStatus.STARTED);

                subscription = subscriptionRepository.update(subscription);

                createAudit(
                    executionContext,
                    apiId,
                    subscription.getApplication(),
                    SUBSCRIPTION_RESUMED_BY_CONSUMER,
                    subscription.getUpdatedAt(),
                    previousSubscription,
                    subscription
                );

                // active API Keys are automatically unpause
                resumeApiKeys(executionContext, subscription);

                return convert(subscription);
            }

            throw new SubscriptionNotPausedException(subscription);
        } catch (TechnicalException ex) {
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to resume subscription %s", subscriptionId),
                ex
            );
        }
    }

    private void resumeApiKeys(ExecutionContext executionContext, Subscription subscription) {
        streamActiveApiKeys(executionContext, subscription.getId())
            .forEach(
                apiKey -> {
                    apiKey.setPaused(false);
                    apiKeyService.update(executionContext, apiKey);
                }
            );
    }

    private static void validateConsumerStatus(Subscription subscription, GenericApiModel genericApiModel) {
        if (subscription.getConsumerStatus() == Subscription.ConsumerStatus.FAILURE) {
            throw new SubscriptionFailureException(subscription);
        }
        if (!DefinitionVersion.V4.equals(genericApiModel.getDefinitionVersion())) {
            throw new SubscriptionConsumerStatusNotUpdatableException(
                subscription,
                SubscriptionConsumerStatusNotUpdatableException.Cause.DEFINITION_NOT_V4
            );
        } else {
            final ApiModel v4ApiModel = (ApiModel) genericApiModel;
            if (
                v4ApiModel.getListeners() == null ||
                v4ApiModel.getListeners().stream().noneMatch(l -> l.getType().equals(ListenerType.SUBSCRIPTION))
            ) {
                throw new SubscriptionConsumerStatusNotUpdatableException(
                    subscription,
                    SubscriptionConsumerStatusNotUpdatableException.Cause.NO_SUBSCRIPTION_LISTENER
                );
            }
        }
    }

    @Override
    public SubscriptionEntity resume(final ExecutionContext executionContext, String subscriptionId) {
        try {
            logger.debug("Resume subscription {}", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            if (subscription.getStatus() == Subscription.Status.PAUSED) {
                Subscription previousSubscription = new Subscription(subscription);
                final Date now = new Date();
                subscription.setUpdatedAt(now);
                subscription.setPausedAt(null);
                subscription.setStatus(Subscription.Status.ACCEPTED);

                subscription = subscriptionRepository.update(subscription);

                // Send an email to subscriber
                final ApplicationEntity application = applicationService.findById(executionContext, subscription.getApplication());
                final GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());
                String apiId = genericPlanEntity.getApiId();
                final GenericApiModel genericApiModel = apiTemplateService.findByIdForTemplates(executionContext, apiId);
                final PrimaryOwnerEntity owner = application.getPrimaryOwner();
                final Map<String, Object> params = new NotificationParamsBuilder()
                    .owner(owner)
                    .api(genericApiModel)
                    .plan(genericPlanEntity)
                    .application(application)
                    .build();

                notifierService.trigger(executionContext, ApiHook.SUBSCRIPTION_RESUMED, apiId, params);
                notifierService.trigger(executionContext, ApplicationHook.SUBSCRIPTION_RESUMED, application.getId(), params);
                createAudit(
                    executionContext,
                    apiId,
                    subscription.getApplication(),
                    SUBSCRIPTION_RESUMED,
                    subscription.getUpdatedAt(),
                    previousSubscription,
                    subscription
                );

                // active API Keys are automatically unpause
                resumeApiKeys(executionContext, subscription);

                return convert(subscription);
            }

            throw new SubscriptionNotPausedException(subscription);
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to resume subscription {}", subscriptionId, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to resume subscription %s", subscriptionId),
                ex
            );
        }
    }

    /**
     * Restore a closed subscription in PENDING status
     */
    @Override
    public SubscriptionEntity restore(final ExecutionContext executionContext, String subscriptionId) {
        try {
            logger.debug("Restore subscription {}", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            if (subscription.getStatus() == Subscription.Status.CLOSED || subscription.getStatus() == Subscription.Status.REJECTED) {
                Subscription previousSubscription = new Subscription(subscription);
                final Date now = new Date();
                subscription.setUpdatedAt(now);
                subscription.setPausedAt(null);
                subscription.setStatus(Subscription.Status.PENDING);

                subscription = subscriptionRepository.update(subscription);

                createAudit(
                    executionContext,
                    subscription.getApi(),
                    subscription.getApplication(),
                    SUBSCRIPTION_RESUMED,
                    subscription.getUpdatedAt(),
                    previousSubscription,
                    subscription
                );

                // active API Keys are automatically unpause
                streamActiveApiKeys(executionContext, subscription.getId())
                    .forEach(
                        apiKey -> {
                            apiKey.setPaused(false);
                            apiKeyService.update(executionContext, apiKey);
                        }
                    );

                return convert(subscription);
            }

            throw new SubscriptionNotClosedException(subscriptionId);
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to restore subscription {}", subscriptionId, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to restore subscription %s", subscriptionId),
                ex
            );
        }
    }

    @Override
    public void delete(final ExecutionContext executionContext, String subscriptionId) {
        try {
            logger.debug("Delete subscription {}", subscriptionId);

            Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

            // Delete API Keys
            apiKeyService.findBySubscription(executionContext, subscriptionId).forEach(apiKey -> apiKeyService.delete(apiKey.getKey()));

            // Delete subscription
            subscriptionRepository.delete(subscriptionId);
            createAudit(
                executionContext,
                subscription.getApi(),
                subscription.getApplication(),
                SUBSCRIPTION_DELETED,
                subscription.getUpdatedAt(),
                subscription,
                null
            );
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to delete subscription: {}", subscriptionId, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to delete subscription: %s", subscriptionId),
                ex
            );
        }
    }

    @Override
    public Collection<SubscriptionEntity> search(final ExecutionContext executionContext, SubscriptionQuery query) {
        try {
            logger.debug("Search subscriptions {}", query);

            final SubscriptionCriteria.Builder builder = toSubscriptionCriteriaBuilder(query);

            Set<String> subscriptionsIds = null;
            if (query.getApiKey() != null && !query.getApiKey().isEmpty()) {
                if (query.getApis() != null && query.getApis().size() == 1) {
                    // Search by API & API Key
                    final ApiKeyEntity apiKey = apiKeyService.findByKeyAndApi(
                        executionContext,
                        query.getApiKey(),
                        query.getApis().iterator().next()
                    );
                    if (apiKey != null) {
                        subscriptionsIds = apiKey.getSubscriptions().stream().map(SubscriptionEntity::getId).collect(Collectors.toSet());
                    }
                } else {
                    // Search by API Key
                    List<ApiKeyEntity> apiKeys = apiKeyService.findByKey(executionContext, query.getApiKey());
                    if (apiKeys != null) {
                        subscriptionsIds =
                            apiKeys
                                .stream()
                                .flatMap(apiKeyEntity -> apiKeyEntity.getSubscriptions().stream())
                                .map(SubscriptionEntity::getId)
                                .collect(Collectors.toSet());
                    }
                }
            }
            builder.ids(subscriptionsIds);

            Stream<SubscriptionEntity> subscriptionsStream = subscriptionRepository.search(builder.build()).stream().map(this::convert);

            return subscriptionsStream.collect(toList());
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to search for subscriptions: {}", query, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to search for subscriptions: %s", query),
                ex
            );
        }
    }

    @Override
    public Page<SubscriptionEntity> search(ExecutionContext executionContext, SubscriptionQuery query, Pageable pageable) {
        return search(executionContext, query, pageable, false, false);
    }

    @Override
    public Page<SubscriptionEntity> search(
        ExecutionContext executionContext,
        SubscriptionQuery query,
        Pageable pageable,
        boolean fillApiKey,
        boolean fillPlanSecurityType
    ) {
        try {
            logger.debug("Search pageable subscriptions {}", query);

            if (query.getApiKey() != null && !query.getApiKey().isEmpty()) {
                List<SubscriptionEntity> filteredSubscriptions = apiKeyService
                    .findByKey(executionContext, query.getApiKey())
                    .stream()
                    .flatMap(apiKey -> findByIdIn(apiKey.getSubscriptionIds()).stream())
                    .filter(
                        subscription ->
                            query.matchesApi(subscription.getApi()) &&
                            query.matchesApplication(subscription.getApplication()) &&
                            query.matchesPlan(subscription.getPlan()) &&
                            query.matchesStatus(subscription.getStatus())
                    )
                    .collect(toList());

                return new Page<>(filteredSubscriptions, 1, filteredSubscriptions.size(), filteredSubscriptions.size());
            } else {
                final SubscriptionCriteria.Builder builder = toSubscriptionCriteriaBuilder(query);

                Page<Subscription> pageSubscription = subscriptionRepository.search(
                    builder.build(),
                    new PageableBuilder().pageNumber(pageable.getPageNumber() - 1).pageSize(pageable.getPageSize()).build()
                );

                List<SubscriptionEntity> subscriptions = pageSubscription.getContent().stream().map(this::convert).collect(toList());

                if (fillPlanSecurityType) {
                    fillPlanSecurityType(executionContext, subscriptions);
                }
                if (fillApiKey) {
                    fillApiKeys(executionContext, subscriptions);
                }

                return new Page<>(
                    subscriptions,
                    pageSubscription.getPageNumber() + 1,
                    (int) pageSubscription.getPageElements(),
                    pageSubscription.getTotalElements()
                );
            }
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to search for pageable subscriptions: {}", query, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to search for pageable subscriptions: %s", query),
                ex
            );
        }
    }

    private void fillPlanSecurityType(ExecutionContext executionContext, List<SubscriptionEntity> subscriptions) {
        Map<String, List<SubscriptionEntity>> subscriptionsByPlan = subscriptions
            .stream()
            .filter(subscription -> subscription.getPlan() != null)
            .collect(groupingBy(SubscriptionEntity::getPlan));

        planSearchService
            .findByIdIn(executionContext, subscriptionsByPlan.keySet())
            .forEach(
                plan -> {
                    PlanSecurityType planSecurityType = PlanSecurityType.valueOfLabel(plan.getPlanSecurity().getType());
                    subscriptionsByPlan.get(plan.getId()).forEach(subscription -> subscription.setSecurity(planSecurityType.name()));
                }
            );
    }

    private void fillApiKeys(ExecutionContext executionContext, List<SubscriptionEntity> subscriptions) {
        subscriptions.forEach(
            subscriptionEntity -> {
                final List<String> keys = streamActiveApiKeys(executionContext, subscriptionEntity.getId())
                    .map(ApiKeyEntity::getKey)
                    .collect(toList());
                subscriptionEntity.setKeys(keys);
            }
        );
    }

    private SubscriptionCriteria.Builder toSubscriptionCriteriaBuilder(SubscriptionQuery query) {
        SubscriptionCriteria.Builder builder = new SubscriptionCriteria.Builder()
            .apis(query.getApis())
            .applications(query.getApplications())
            .plans(query.getPlans())
            .from(query.getFrom())
            .to(query.getTo())
            .endingAtAfter(query.getEndingAtAfter())
            .endingAtBefore(query.getEndingAtBefore());

        if (query.getStatuses() != null) {
            builder.statuses(
                query
                    .getStatuses()
                    .stream()
                    .map(subscriptionStatus -> Subscription.Status.valueOf(subscriptionStatus.name()))
                    .collect(toSet())
            );
        }

        if (query.getPlanSecurityTypes() != null) {
            builder.planSecurityTypes(query.getPlanSecurityTypes().stream().map(Plan.PlanSecurityType::valueOf).collect(toList()));
        }

        return builder;
    }

    @Override
    public SubscriptionEntity transfer(
        ExecutionContext executionContext,
        final TransferSubscriptionEntity transferSubscription,
        String userId
    ) {
        try {
            logger.debug("Subscription {} transferred by {}", transferSubscription.getId(), userId);

            GenericPlanEntity transferGenericPlanEntity = planSearchService.findById(executionContext, transferSubscription.getPlan());

            Subscription subscription = subscriptionRepository
                .findById(transferSubscription.getId())
                .orElseThrow(() -> new SubscriptionNotFoundException(transferSubscription.getId()));
            GenericPlanEntity subscriptionGenericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());
            if (
                transferGenericPlanEntity.getPlanStatus() != PlanStatus.PUBLISHED ||
                !transferGenericPlanEntity.getPlanSecurity().getType().equals(subscriptionGenericPlanEntity.getPlanSecurity().getType()) ||
                (transferGenericPlanEntity.getGeneralConditions() != null && !transferGenericPlanEntity.getGeneralConditions().isEmpty())
            ) {
                throw new TransferNotAllowedException(transferGenericPlanEntity.getId());
            }

            Subscription previousSubscription = new Subscription(subscription);

            subscription.setUpdatedAt(new Date());
            subscription.setPlan(transferSubscription.getPlan());

            subscription = subscriptionRepository.update(subscription);

            final ApplicationEntity application = applicationService.findById(executionContext, subscription.getApplication());
            final String apiId = subscriptionGenericPlanEntity.getApiId();
            final GenericApiModel genericApiModel = apiTemplateService.findByIdForTemplates(executionContext, apiId);
            final PrimaryOwnerEntity owner = application.getPrimaryOwner();
            createAudit(
                executionContext,
                apiId,
                subscription.getApplication(),
                SUBSCRIPTION_UPDATED,
                subscription.getUpdatedAt(),
                previousSubscription,
                subscription
            );

            SubscriptionEntity subscriptionEntity = convert(subscription);

            final Map<String, Object> params = new NotificationParamsBuilder()
                .owner(owner)
                .application(application)
                .api(genericApiModel)
                .plan(subscriptionGenericPlanEntity)
                .subscription(subscriptionEntity)
                .build();
            notifierService.trigger(executionContext, ApiHook.SUBSCRIPTION_TRANSFERRED, apiId, params);
            notifierService.trigger(executionContext, ApplicationHook.SUBSCRIPTION_TRANSFERRED, application.getId(), params);
            return subscriptionEntity;
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to transfer subscription {} by {}", transferSubscription.getId(), userId, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to transfer subscription %s by %s", transferSubscription.getId(), userId),
                ex
            );
        }
    }

    @Override
    public String exportAsCsv(Collection<SubscriptionEntity> subscriptions, Map<String, Map<String, Object>> metadata) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Plan");
        sb.append(separator);
        sb.append("Application");
        sb.append(separator);
        sb.append("Creation date");
        sb.append(separator);
        sb.append("Process date");
        sb.append(separator);
        sb.append("Start date");
        sb.append(separator);
        sb.append("End date date");
        sb.append(separator);
        sb.append("Status");
        sb.append(lineSeparator());

        if (subscriptions == null || subscriptions.isEmpty()) {
            return sb.toString();
        }
        for (final SubscriptionEntity subscription : subscriptions) {
            final Object plan = metadata.get(subscription.getPlan());
            sb.append(getName(plan));
            sb.append(separator);

            final Object application = metadata.get(subscription.getApplication());
            sb.append(getName(application));
            sb.append(separator);

            if (subscription.getCreatedAt() != null) {
                sb.append(dateFormatter.format(subscription.getCreatedAt()));
                sb.append(separator);
            }

            if (subscription.getProcessedAt() != null) {
                sb.append(dateFormatter.format(subscription.getProcessedAt()));
                sb.append(separator);
            }

            if (subscription.getStartingAt() != null) {
                sb.append(dateFormatter.format(subscription.getStartingAt()));
                sb.append(separator);
            }

            if (subscription.getEndingAt() != null) {
                sb.append(dateFormatter.format(subscription.getEndingAt()));
                sb.append(separator);
            }

            sb.append(subscription.getStatus());

            sb.append(lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public Set<String> findReferenceIdsOrderByNumberOfSubscriptions(SubscriptionQuery query, Order order) {
        try {
            return subscriptionRepository.findReferenceIdsOrderByNumberOfSubscriptions(toSubscriptionCriteriaBuilder(query).build(), order);
        } catch (TechnicalException ex) {
            logger.error("An error occurs while trying to findReferenceIdsOrderByNumberOfSubscriptions for subscriptions: {}", query, ex);
            throw new TechnicalManagementException(
                String.format("An error occurs while trying to findReferenceIdsOrderByNumberOfSubscriptions for subscriptions: %s", query),
                ex
            );
        }
    }

    private String getName(Object map) {
        return map == null ? "" : ((Map) map).get("name").toString();
    }

    @Override
    public Metadata getMetadata(ExecutionContext executionContext, SubscriptionMetadataQuery query) {
        Metadata metadata = new Metadata();
        Collection<SubscriptionEntity> subscriptions = query.getSubscriptions();

        final Optional<Map<String, ApplicationListItem>> applicationsById = query
            .ifApplications()
            .map(
                withApplications -> {
                    Set<String> appIds = subscriptions.stream().map(SubscriptionEntity::getApplication).collect(toSet());
                    return applicationService
                        .findByIds(new ExecutionContext(query.getOrganization(), query.getEnvironment()), appIds)
                        .stream()
                        .collect(toMap(ApplicationListItem::getId, Function.identity()));
                }
            );

        final Optional<Map<String, GenericApiEntity>> apisById = query
            .ifApis()
            .map(
                withApis -> {
                    Set<String> apiIds = subscriptions.stream().map(SubscriptionEntity::getApi).collect(toSet());
                    return apiSearchService
                        .findGenericByEnvironmentAndIdIn(executionContext, apiIds)
                        .stream()
                        .collect(toMap(GenericApiEntity::getId, Function.identity()));
                }
            );

        final Optional<Map<String, GenericPlanEntity>> plansById = query
            .ifPlans()
            .map(
                withPlans -> {
                    Set<String> planIds = subscriptions.stream().map(SubscriptionEntity::getPlan).collect(toSet());
                    return planSearchService
                        .findByIdIn(executionContext, planIds)
                        .stream()
                        .collect(toMap(GenericPlanEntity::getId, Function.identity()));
                }
            );

        final Optional<Map<String, UserEntity>> subscribersById = query
            .ifSubscribers()
            .map(
                withSubscribers -> {
                    Set<String> subscriberIds = subscriptions.stream().map(SubscriptionEntity::getSubscribedBy).collect(toSet());
                    return userService
                        .findByIds(executionContext, subscriberIds)
                        .stream()
                        .collect(toMap(UserEntity::getId, Function.identity()));
                }
            );

        subscriptions.forEach(
            subscription -> {
                applicationsById.ifPresent(byId -> fillApplicationMetadata(byId, metadata, subscription));
                apisById.ifPresent(byId -> fillApiMetadata(executionContext, byId, metadata, subscription, query));
                plansById.ifPresent(byId -> fillPlanMetadata(byId, metadata, subscription));
                subscribersById.ifPresent(byId -> fillSubscribersMetadata(byId, metadata, subscription));
            }
        );

        return metadata;
    }

    private Metadata fillApplicationMetadata(
        Map<String, ApplicationListItem> applications,
        Metadata metadata,
        SubscriptionEntity subscription
    ) {
        if (applications.containsKey(subscription.getApplication())) {
            ApplicationListItem application = applications.get(subscription.getApplication());
            metadata.put(application.getId(), "name", application.getName());
        }
        return metadata;
    }

    private Metadata fillPlanMetadata(Map<String, GenericPlanEntity> plans, Metadata metadata, SubscriptionEntity subscription) {
        if (plans.containsKey(subscription.getPlan())) {
            GenericPlanEntity plan = plans.get(subscription.getPlan());
            metadata.put(plan.getId(), "name", plan.getName());
            metadata.put(plan.getId(), "securityType", PlanSecurityType.valueOfLabel(plan.getPlanSecurity().getType()).name());
        }
        return metadata;
    }

    private Metadata fillApiMetadata(
        ExecutionContext executionContext,
        Map<String, GenericApiEntity> apis,
        Metadata metadata,
        SubscriptionEntity subscription,
        SubscriptionMetadataQuery query
    ) {
        if (apis.containsKey(subscription.getApi())) {
            GenericApiEntity api = apis.get(subscription.getApi());
            metadata.put(api.getId(), "name", api.getName());
            if (query.hasDetails()) {
                metadata.put(api.getId(), "state", api.getLifecycleState());
                metadata.put(api.getId(), "version", api.getApiVersion());

                List<ApiEntrypointEntity> apiEntrypoints = apiEntrypointService.getApiEntrypoints(executionContext, api);
                metadata.put(api.getId(), "entrypoints", apiEntrypoints);
            }
            query.getApiDelegate().forEach(delegate -> delegate.apply(metadata, api));
        }
        return metadata;
    }

    private Metadata fillSubscribersMetadata(Map<String, UserEntity> users, Metadata metadata, SubscriptionEntity subscription) {
        if (users.containsKey(subscription.getSubscribedBy())) {
            UserEntity user = users.get(subscription.getSubscribedBy());
            metadata.put(user.getId(), "name", user.getDisplayName());
        }
        return metadata;
    }

    private SubscriptionEntity convert(Subscription subscription) {
        SubscriptionEntity entity = new SubscriptionEntity();

        entity.setId(subscription.getId());
        entity.setApi(subscription.getApi());
        entity.setPlan(subscription.getPlan());
        entity.setProcessedAt(subscription.getProcessedAt());
        entity.setStatus(SubscriptionStatus.valueOf(subscription.getStatus().name()));
        if (subscription.getConsumerStatus() != null) {
            entity.setConsumerStatus(SubscriptionConsumerStatus.valueOf(subscription.getConsumerStatus().name()));
        }
        entity.setProcessedBy(subscription.getProcessedBy());
        entity.setRequest(subscription.getRequest());
        entity.setReason(subscription.getReason());
        entity.setApplication(subscription.getApplication());
        entity.setStartingAt(subscription.getStartingAt());
        entity.setEndingAt(subscription.getEndingAt());
        entity.setCreatedAt(subscription.getCreatedAt());
        entity.setUpdatedAt(subscription.getUpdatedAt());
        entity.setSubscribedBy(subscription.getSubscribedBy());
        entity.setClosedAt(subscription.getClosedAt());
        entity.setClientId(subscription.getClientId());
        entity.setPausedAt(subscription.getPausedAt());
        entity.setConsumerPausedAt(subscription.getConsumerPausedAt());
        entity.setDaysToExpirationOnLastNotification(subscription.getDaysToExpirationOnLastNotification());
        entity.setConfiguration(subscription.getConfiguration());
        entity.setMetadata(subscription.getMetadata());
        entity.setFailureCause(subscription.getFailureCause());

        return entity;
    }

    private void createAudit(
        ExecutionContext executionContext,
        String apiId,
        String applicationId,
        Audit.AuditEvent event,
        Date createdAt,
        Subscription oldValue,
        Subscription newValue
    ) {
        auditService.createApiAuditLog(
            executionContext,
            apiId,
            Collections.singletonMap(APPLICATION, applicationId),
            event,
            createdAt,
            oldValue,
            newValue
        );
        auditService.createApplicationAuditLog(
            executionContext,
            applicationId,
            Collections.singletonMap(API, apiId),
            event,
            createdAt,
            oldValue,
            newValue
        );
    }

    private long countApiKeySubscriptions(ExecutionContext executionContext, ApplicationEntity application) {
        SubscriptionQuery subscriptionQuery = new SubscriptionQuery();
        subscriptionQuery.setApplication(application.getId());
        return search(executionContext, subscriptionQuery)
            .stream()
            .filter(
                subscription -> {
                    GenericPlanEntity genericPlanEntity = planSearchService.findById(executionContext, subscription.getPlan());
                    PlanSecurityType planSecurityType = PlanSecurityType.valueOfLabel(genericPlanEntity.getPlanSecurity().getType());
                    return planSecurityType == PlanSecurityType.API_KEY;
                }
            )
            .count();
    }

    private Stream<ApiKeyEntity> streamActiveApiKeys(ExecutionContext executionContext, String subscriptionId) {
        return apiKeyService
            .findBySubscription(executionContext, subscriptionId)
            .stream()
            .filter(apiKey -> !apiKey.isRevoked() && !apiKey.isExpired());
    }
}
