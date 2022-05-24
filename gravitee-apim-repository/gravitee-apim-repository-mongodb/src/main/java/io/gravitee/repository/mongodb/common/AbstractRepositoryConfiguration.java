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
package io.gravitee.repository.mongodb.common;

import com.mongodb.MongoCredential;
import io.gravitee.repository.mongodb.management.mapper.GraviteeDozerMapper;
import io.gravitee.repository.mongodb.management.mapper.GraviteeMapper;
import io.gravitee.repository.mongodb.management.transaction.NoTransactionManager;
import io.gravitee.repository.mongodb.utils.LiquibaseUrlBuilder;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.integration.spring.SpringResourceAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractRepositoryConfiguration extends AbstractMongoClientConfiguration {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private Environment environment;

    @Override
    protected String getDatabaseName() {
        String uri = environment.getProperty("management.mongodb.uri");
        if (uri != null && !uri.isEmpty()) {
            return URI.create(uri).getPath().substring(1);
        }

        return environment.getProperty("management.mongodb.dbname", "gravitee");
    }

    @Bean
    public GraviteeMapper graviteeMapper() {
        return new GraviteeDozerMapper();
    }

    protected Set<Class<?>> getInitialEntitySet() throws ClassNotFoundException {
        Collection<String> basePackages = getMappingBasePackages();
        Set<Class<?>> initialEntitySet = new HashSet<Class<?>>();

        for (String basePackage : basePackages) {
            if (StringUtils.hasText(basePackage)) {
                ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(false);
                componentProvider.addIncludeFilter(new AnnotationTypeFilter(Document.class));
                componentProvider.addIncludeFilter(new AnnotationTypeFilter(Persistent.class));
                String prefix = environment.getProperty("management.mongodb.prefix", "");

                for (BeanDefinition candidate : componentProvider.findCandidateComponents(basePackage)) {
                    Class<?> entity = ClassUtils.forName(candidate.getBeanClassName(), this.getClass().getClassLoader());
                    initialEntitySet.add(entity);

                    Document documentAnnotation = entity.getAnnotation(Document.class);
                    configureCollectionName(documentAnnotation, prefix);
                }
            }
        }
        return initialEntitySet;
    }

    @Bean
    public AbstractPlatformTransactionManager graviteeTransactionManager() {
        return new NoTransactionManager();
    }

    public static void configureCollectionName(Annotation annotation, String prefix) {
        if (StringUtils.hasText(prefix)) {
            Object handler = Proxy.getInvocationHandler(annotation);
            Field f;
            try {
                f = handler.getClass().getDeclaredField("memberValues");
            } catch (NoSuchFieldException | SecurityException e) {
                throw new IllegalStateException(e);
            }
            f.setAccessible(true);
            Map<String, Object> memberValues;
            try {
                memberValues = (Map<String, Object>) f.get(handler);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }

            String documentValue = memberValues.get("value").toString();
            String documentCollection = memberValues.get("collection").toString();
            String newValue;
            if (StringUtils.hasText(documentValue)) {
                newValue = prefix + documentValue;
                memberValues.put("value", newValue);
            } else if (StringUtils.hasText(documentCollection)) {
                newValue = prefix + documentCollection;
                memberValues.put("collection", newValue);
            }
        }
    }

    protected void runLiquibase() throws LiquibaseException {
        System.setProperty("liquibase.hub.mode", "off");
        try (Liquibase liquibase = new Liquibase("liquibase/master.yml", new SpringResourceAccessor(resourceLoader), configureLiquibaseDatabase())) {
            liquibase.update((Contexts) null);
        }
    }

    private MongoLiquibaseDatabase configureLiquibaseDatabase() throws LiquibaseException {
        String userName = null;
        String password = null;
        MongoCredential credentials = this.mongoClientSettings().getCredential();
        if(null != credentials) {
            userName = credentials.getUserName();
            if(null != credentials.getPassword()) {
                password = new String(credentials.getPassword());
            }
        }

        return (MongoLiquibaseDatabase) DatabaseFactory
                    .getInstance()
                    .openDatabase(buildLiquibaseUrl(), userName, password, null, null);
    }

    private String buildLiquibaseUrl() {
        String uri = environment.getProperty("management.mongodb.uri");
        return StringUtils.hasText(uri) ? LiquibaseUrlBuilder.buildFromUri(uri) : LiquibaseUrlBuilder.buildFromClient(environment, mongoClientSettings());
    }
}
