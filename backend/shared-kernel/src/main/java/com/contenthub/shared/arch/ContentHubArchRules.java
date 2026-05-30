package com.contenthub.shared.arch;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reusable ArchUnit rule factory enforcing ADR-0009 module boundaries.
 * Instantiated in ModuleBoundaryTest (app module) which has all domain classes
 * on its classpath simultaneously.
 */
public final class ContentHubArchRules {

	private ContentHubArchRules() {
	}

	public static ArchRule persistenceAdaptersArePrivateTo(String module, String... otherModules) {
		String[] forbiddenCallers = new String[otherModules.length];
		for (int i = 0; i < otherModules.length; i++) {
			forbiddenCallers[i] = "com.contenthub." + otherModules[i] + "..";
		}
		return noClasses().that().resideInAnyPackage(forbiddenCallers).should().accessClassesThat()
				.resideInAPackage("com.contenthub." + module + ".adapter.out.persistence..")
				.because("Module '" + module + "' owns its persistence layer (ADR-0009). "
						+ "Cross-module access must go through published application ports or events.");
	}
}
