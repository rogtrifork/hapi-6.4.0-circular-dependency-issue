package com.example.demo

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig
import ca.uhn.fhir.context.ConfigurationException
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.jpa.api.config.DaoConfig
import ca.uhn.fhir.jpa.api.config.ThreadPoolFactoryConfig
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config
import ca.uhn.fhir.jpa.config.JpaConfig
import ca.uhn.fhir.jpa.config.util.HapiEntityManagerFactoryUtil
import ca.uhn.fhir.jpa.dao.r4.FhirSystemDaoR4
import ca.uhn.fhir.jpa.dao.r4.TransactionProcessorVersionAdapterR4
import ca.uhn.fhir.jpa.model.config.PartitionSettings
import ca.uhn.fhir.jpa.model.entity.ModelConfig
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig
import ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig
import ca.uhn.fhir.jpa.subscription.submit.config.SubscriptionSubmitterConfig
import ca.uhn.fhir.jpa.term.TermVersionAdapterSvcR4
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Meta
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import javax.sql.DataSource

@Configuration
@Import(
    JpaConfig::class,
    JpaBatch2Config::class,
    Batch2JobsConfig::class,
    SubscriptionChannelConfig::class,
    SubscriptionSubmitterConfig::class,
    SubscriptionProcessorConfig::class,
    ThreadPoolFactoryConfig::class
)
class MockJpaConfig {
    @Autowired
    lateinit var configurableEnvironment: ConfigurableEnvironment

    @Bean
    fun fhirCtx() = FhirContext.forR4()

    @Bean
    fun termVersionAdapterSvc() = TermVersionAdapterSvcR4()

    @Bean
    fun partitionSettings() = PartitionSettings()

    @Bean
    fun databaseBackedPagingProvider(): DatabaseBackedPagingProvider = DatabaseBackedPagingProvider()

    @Bean
    fun transactionProcessorVersionFacade() = TransactionProcessorVersionAdapterR4()

    @Bean(name = ["mySystemDaoR4"])
    fun systemDaoR4(): IFhirSystemDao<Bundle, Meta> = FhirSystemDaoR4()


    @Primary
    @Bean
    fun entityManagerFactory(
        myDataSource: DataSource,
        myConfigurableListableBeanFactory: ConfigurableListableBeanFactory,
        theFhirContext: FhirContext
    ): LocalContainerEntityManagerFactoryBean? {
        val retVal =
            HapiEntityManagerFactoryUtil.newEntityManagerFactory(myConfigurableListableBeanFactory, theFhirContext)
        retVal.persistenceUnitName = "HAPI_PU"
        try {
            retVal.dataSource = myDataSource
        } catch (e: Exception) {
            throw ConfigurationException("Could not set the data source due to a configuration issue", e)
        }
        retVal.setJpaProperties(
            EnvironmentHelper.getHibernateProperties(
                configurableEnvironment,
                myConfigurableListableBeanFactory
            )
        )
        return retVal
    }
    @Bean
    fun daoConfig() = DaoConfig()

    @Bean
    fun modelConfig(daoConfig: DaoConfig): ModelConfig = daoConfig.modelConfig

}