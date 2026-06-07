package com.fsc.secengine;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {

    ApplicationModules modules = ApplicationModules.of(SecEngineApplication.class);

    @Test
    void verifiesModularStructure() {
        // Verifica se os módulos não quebram as regras de isolamento (ex: repositórios de outros módulos)
        modules.verify();
    }

    @Test
    void createModuleDocumentation() {
        // Gera a documentação arquitetural (PlantUML, C4 Model) na pasta target/spring-modulith-docs
        new Documenter(modules)
            .writeDocumentation()
            .writeIndividualModulesAsPlantUml();
    }
}
