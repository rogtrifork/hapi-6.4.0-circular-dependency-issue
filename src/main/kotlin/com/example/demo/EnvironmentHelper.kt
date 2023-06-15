package com.example.demo

import ca.uhn.fhir.jpa.config.HapiFhirLocalContainerEntityManagerFactoryBean
import ca.uhn.fhir.jpa.search.HapiHSearchAnalysisConfigurers.HapiLuceneAnalysisConfigurer
import ca.uhn.fhir.jpa.search.elastic.ElasticsearchHibernatePropertiesBuilder
import org.apache.lucene.util.Version
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.cfg.AvailableSettings
import org.hibernate.search.backend.elasticsearch.cfg.ElasticsearchBackendSettings
import org.hibernate.search.backend.elasticsearch.index.IndexStatus
import org.hibernate.search.backend.lucene.cfg.LuceneBackendSettings
import org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings
import org.hibernate.search.backend.lucene.lowlevel.directory.impl.LocalFileSystemDirectoryProvider
import org.hibernate.search.engine.cfg.BackendSettings
import org.hibernate.search.mapper.orm.automaticindexing.session.AutomaticIndexingSynchronizationStrategyNames
import org.hibernate.search.mapper.orm.cfg.HibernateOrmMapperSettings
import org.hibernate.search.mapper.orm.schema.management.SchemaManagementStrategyName
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy
import org.springframework.core.env.CompositePropertySource
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.PropertySource
import java.util.*
import java.util.function.Consumer

object EnvironmentHelper {

    fun getHibernateProperties(
        environment: ConfigurableEnvironment,
        myConfigurableListableBeanFactory: ConfigurableListableBeanFactory
    ): Properties {
        val properties = Properties()
        val jpaProps = getPropertiesStartingWith(environment, "spring.jpa.properties")
        for ((key, value) in jpaProps) {
            val strippedKey = key.replace("spring.jpa.properties.", "")
            properties[strippedKey] = value.toString()
        }

        //Spring Boot Autoconfiguration defaults
        properties.putIfAbsent(AvailableSettings.SCANNER, "org.hibernate.boot.archive.scan.internal.DisabledScanner")
        properties.putIfAbsent(
            AvailableSettings.IMPLICIT_NAMING_STRATEGY,
            SpringImplicitNamingStrategy::class.java.name
        )
        properties.putIfAbsent(
            AvailableSettings.PHYSICAL_NAMING_STRATEGY,
            CamelCaseToUnderscoresNamingStrategy::class.java.name
        )
        //TODO The bean factory should be added as parameter but that requires that it can be injected from the entityManagerFactory bean from xBaseConfig
        //properties.putIfAbsent(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(beanFactory));

        //hapi-fhir-jpaserver-base "sensible defaults"
        val hapiJpaPropertyMap =
            HapiFhirLocalContainerEntityManagerFactoryBean(myConfigurableListableBeanFactory).jpaPropertyMap
        hapiJpaPropertyMap.forEach { (key: String?, value: Any?) ->
            properties.putIfAbsent(
                key,
                value
            )
        }

        //hapi-fhir-jpaserver-starter defaults
        properties.putIfAbsent(AvailableSettings.FORMAT_SQL, false)
        properties.putIfAbsent(AvailableSettings.SHOW_SQL, false)
        properties.putIfAbsent(AvailableSettings.HBM2DDL_AUTO, "update")
        properties.putIfAbsent(AvailableSettings.STATEMENT_BATCH_SIZE, 20)
        properties.putIfAbsent(AvailableSettings.USE_QUERY_CACHE, false)
        properties.putIfAbsent(AvailableSettings.USE_SECOND_LEVEL_CACHE, false)
        properties.putIfAbsent(AvailableSettings.USE_STRUCTURED_CACHE, false)
        properties.putIfAbsent(AvailableSettings.USE_MINIMAL_PUTS, false)

        //Hibernate Search defaults
        properties.putIfAbsent(HibernateOrmMapperSettings.ENABLED, false)
        if (java.lang.Boolean.parseBoolean(properties[HibernateOrmMapperSettings.ENABLED].toString())) {
            if (isElasticsearchEnabled(environment)!!) {
                properties.putIfAbsent(
                    BackendSettings.backendKey(BackendSettings.TYPE),
                    ElasticsearchBackendSettings.TYPE_NAME
                )
            } else {
                properties.putIfAbsent(
                    BackendSettings.backendKey(BackendSettings.TYPE),
                    LuceneBackendSettings.TYPE_NAME
                )
            }
            if (properties[BackendSettings.backendKey(BackendSettings.TYPE)] == LuceneBackendSettings.TYPE_NAME) {
                properties.putIfAbsent(
                    BackendSettings.backendKey(LuceneIndexSettings.DIRECTORY_TYPE),
                    LocalFileSystemDirectoryProvider.NAME
                )
                properties.putIfAbsent(
                    BackendSettings.backendKey(LuceneIndexSettings.DIRECTORY_ROOT),
                    "target/lucenefiles"
                )
                properties.putIfAbsent(
                    BackendSettings.backendKey(LuceneBackendSettings.ANALYSIS_CONFIGURER),
                    HapiLuceneAnalysisConfigurer::class.java.name
                )
                properties.putIfAbsent(BackendSettings.backendKey(LuceneBackendSettings.LUCENE_VERSION), Version.LATEST)
            } else if (properties[BackendSettings.backendKey(BackendSettings.TYPE)] == ElasticsearchBackendSettings.TYPE_NAME) {
                val builder = ElasticsearchHibernatePropertiesBuilder()
                val requiredIndexStatus = environment.getProperty(
                    "elasticsearch.required_index_status",
                    IndexStatus::class.java
                )
                builder.setRequiredIndexStatus(Objects.requireNonNullElse(requiredIndexStatus, IndexStatus.YELLOW))
                builder.setHosts(getElasticsearchServerUrl(environment))
                builder.setUsername(getElasticsearchServerUsername(environment))
                builder.setPassword(getElasticsearchServerPassword(environment))
                builder.setProtocol(getElasticsearchServerProtocol(environment))
                val indexSchemaManagementStrategy = environment.getProperty(
                    "elasticsearch.schema_management_strategy",
                    SchemaManagementStrategyName::class.java
                )
                builder.setIndexSchemaManagementStrategy(
                    Objects.requireNonNullElse(
                        indexSchemaManagementStrategy,
                        SchemaManagementStrategyName.CREATE
                    )
                )
                val refreshAfterWrite = environment.getProperty(
                    "elasticsearch.debug.refresh_after_write",
                    Boolean::class.java
                )
                if (refreshAfterWrite == null || !refreshAfterWrite) {
                    builder.setDebugIndexSyncStrategy(AutomaticIndexingSynchronizationStrategyNames.ASYNC)
                } else {
                    builder.setDebugIndexSyncStrategy(AutomaticIndexingSynchronizationStrategyNames.READ_SYNC)
                }
                builder.apply(properties)
            } else {
                throw UnsupportedOperationException(
                    "Unsupported Hibernate Search backend: " + properties[BackendSettings.backendKey(
                        BackendSettings.TYPE
                    )]
                )
            }
        }
        return properties
    }

    fun getElasticsearchServerUrl(environment: ConfigurableEnvironment): String? {
        return environment.getProperty("elasticsearch.rest_url", String::class.java)
    }

    fun getElasticsearchServerProtocol(environment: ConfigurableEnvironment): String? {
        return environment.getProperty("elasticsearch.protocol", String::class.java, "http")
    }

    fun getElasticsearchServerUsername(environment: ConfigurableEnvironment): String? {
        return environment.getProperty("elasticsearch.username")
    }

    fun getElasticsearchServerPassword(environment: ConfigurableEnvironment): String? {
        return environment.getProperty("elasticsearch.password")
    }

    fun isElasticsearchEnabled(environment: ConfigurableEnvironment): Boolean? {
        return if (environment.getProperty("elasticsearch.enabled", Boolean::class.java) != null) {
            environment.getProperty("elasticsearch.enabled", Boolean::class.java)
        } else {
            false
        }
    }

    fun getPropertiesStartingWith(
        aEnv: ConfigurableEnvironment,
        aKeyPrefix: String?
    ): Map<String, Any?> {
        val result: MutableMap<String, Any?> = HashMap()
        val map = getAllProperties(aEnv)
        for ((key, value) in map) {
            if (key.startsWith(aKeyPrefix!!)) {
                result[key] = value
            }
        }
        return result
    }

    fun getAllProperties(aEnv: ConfigurableEnvironment): Map<String, Any?> {
        val result: MutableMap<String, Any?> = HashMap()
        aEnv.propertySources.forEach(Consumer { ps: PropertySource<*>? ->
            addAll(
                result,
                getAllProperties(ps)
            )
        })
        return result
    }

    fun getAllProperties(aPropSource: PropertySource<*>?): Map<String, Any?> {
        val result: MutableMap<String, Any?> = HashMap()
        if (aPropSource is CompositePropertySource) {
            aPropSource.propertySources.forEach(Consumer { ps: PropertySource<*>? ->
                addAll(
                    result,
                    getAllProperties(ps)
                )
            })
            return result
        }
        if (aPropSource is EnumerablePropertySource<*>) {
            val ps = aPropSource
            Arrays.asList(*ps.propertyNames).forEach(Consumer { key: String ->
                result[key] = ps.getProperty(key)
            })
            return result
        }
        return result
    }

    private fun addAll(aBase: MutableMap<String, Any?>, aToBeAdded: Map<String, Any?>) {
        for ((key, value) in aToBeAdded) {
            if (aBase.containsKey(key)) {
                continue
            }
            aBase[key] = value
        }
    }

}