package com.contenthub.arch;

import com.contenthub.shared.arch.ContentHubArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ADR-0009: each module's persistence adapters are private to that module.
 * Tests run in the app module because it is the only module that has all domain
 * classes simultaneously on the classpath.
 */
@AnalyzeClasses(
        packages = "com.contenthub",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule workspace_persistence_not_accessed_by_others =
            ContentHubArchRules.persistenceAdaptersArePrivateTo("workspace",
                    "media", "transcription", "analytics");

    @ArchTest
    static final ArchRule media_persistence_not_accessed_by_others =
            ContentHubArchRules.persistenceAdaptersArePrivateTo("media",
                    "workspace", "transcription", "analytics");

    @ArchTest
    static final ArchRule transcription_persistence_not_accessed_by_others =
            ContentHubArchRules.persistenceAdaptersArePrivateTo("transcription",
                    "workspace", "media", "analytics");

    @ArchTest
    static final ArchRule analytics_persistence_not_accessed_by_others =
            ContentHubArchRules.persistenceAdaptersArePrivateTo("analytics",
                    "workspace", "media", "transcription");

    // Scope: prevents domain classes from extending/importing Spring Data repository interfaces.
    // Jakarta Persistence annotations (@Entity, @Column) and MongoDB annotations (@Document)
    // are present on domain models as an accepted Phase 0 trade-off (no separate JPA entity layer).
    @ArchTest
    static final ArchRule domain_does_not_extend_spring_data_repositories =
            noClasses()
                    .that().resideInAPackage("com.contenthub.*.domain..")
                    .should().accessClassesThat()
                    .resideInAPackage("org.springframework.data.jpa.repository..")
                    .because("Domain model must not extend Spring Data repositories (hexagonal layering)");
}
