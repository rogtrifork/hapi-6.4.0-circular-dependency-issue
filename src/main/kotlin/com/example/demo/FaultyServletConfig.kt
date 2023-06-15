package com.example.demo

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.jpa.config.util.ValidationSupportConfigUtil
import ca.uhn.fhir.jpa.term.api.ITermReadSvc
import ca.uhn.fhir.jpa.validation.JpaValidationSupportChain
import ca.uhn.fhir.rest.server.RestfulServer
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport
import org.springframework.context.annotation.*

@Configuration
@Import(MockJpaConfig::class)
class FaultyServletConfig {

    @Primary
    @Bean(name = ["validationSupportChain"])
//    @DependsOn("terminologyService")
    fun validationSupportChain(theJpaValidationSupportChain: JpaValidationSupportChain): CachingValidationSupport{
        return ValidationSupportConfigUtil.newCachingValidationSupport(theJpaValidationSupportChain)
    }

    @Bean
    fun restfulServer(fhirCtx: FhirContext,
                  validationSupport: CachingValidationSupport,
                  termReadSvc: ITermReadSvc
    ) : RestfulServer {
        val servlet = RestfulServer(fhirCtx).apply {

        }

        return servlet
    }


}