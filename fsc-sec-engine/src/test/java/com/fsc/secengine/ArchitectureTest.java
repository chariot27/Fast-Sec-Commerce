package com.fsc.secengine;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {

    ApplicationModules modules = ApplicationModules.of(SecEngineApplication.class);

    @Test
    void verifiesModularStructure() {
        // Verifica se os modulos nao violam as regras de isolamento do Spring Modulith
        modules.verify();
    }

    @Test
    void createModuleDocumentation() {
        // Gera documentacao arquitetural em target/spring-modulith-docs
        Documenter documenter = new Documenter(modules);
        documenter.writeDocumentation();
        documenter.writeIndividualModulesAsPlantUml();
    }
}
