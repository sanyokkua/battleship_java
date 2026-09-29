package ua.kostenko.battleship.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private static final DescribedPredicate<JavaClass> FORBIDDEN_DOMAIN_TYPES =
            DescribedPredicate.describe("framework, clock, random, logging or I/O types", type -> {
                String packageName = type.getPackageName();
                return packageName.startsWith("org.springframework.")
                        || packageName.startsWith("com.fasterxml.")
                        || packageName.startsWith("jakarta.")
                        || packageName.equals("java.io")
                        || packageName.startsWith("java.io.")
                        || packageName.startsWith("org.slf4j.")
                        || packageName.startsWith("ch.qos.logback.")
                        || packageName.startsWith("java.util.logging.")
                        || type.getName().equals("java.time.Clock")
                        || type.getName().equals("java.util.Random");
            });

    private static final JavaClasses MODULE_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .withImportOption(location -> !location.asURI().toString().contains("-tests.jar"))
            .importPackages(
                    "ua.kostenko.battleship.domain",
                    "ua.kostenko.battleship.application",
                    "ua.kostenko.battleship.app");

    @Test
    void domainDoesNotDependOnFrameworkOrIoTypes() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat(FORBIDDEN_DOMAIN_TYPES)
                .check(MODULE_CLASSES);
    }

    @Test
    void applicationDoesNotDependOnFrameworkTypes() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "com.fasterxml..", "jakarta..")
                .check(MODULE_CLASSES);
    }

    @Test
    void domainDoesNotDependOnHigherModules() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..application..", "..app..")
                .check(MODULE_CLASSES);
    }

    @Test
    void applicationDoesNotDependOnAppModule() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..app..")
                .check(MODULE_CLASSES);
    }
}
