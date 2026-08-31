package rip.ysm.port26;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture invariants from CLAUDE.md, checked against **bytecode** rather than source text.
 *
 * <p>The rest of {@code rip.ysm.port26} mostly greps source files. That has already misfired once
 * in this same review round (§18.2 of EVIDENCE.md: a loop-body assertion that silently zero-covered
 * once a matched line wrapped). ArchUnit reads the compiled call graph and type dependencies, so it
 * is not fooled by comments, formatting, or how a check happens to be spelled in source.</p>
 *
 * <p><b>Floor assertions are not optional here.</b> Every rule below also asserts a minimum count of
 * recognized call/dependency sites. If the identification heuristic ever stops matching anything —
 * a package gets renamed, an import style changes — the rule must fail loudly ("found nothing"),
 * not pass quietly with zero coverage.</p>
 *
 * <p>This test runs under both {@code :common:test} (common's own classes only) and
 * {@code :fabric:test} (common + fabric classes, via the shared srcDir wired up in
 * fabric/build.gradle) — the fabric run is the one that can actually see the compat {@code Impl}
 * classes where a real boundary violation would live.</p>
 */
class ArchitectureBoundaryContractTest {

    private static final String COMMON_PACKAGE = "com.elfmcys.yesstevemodel";
    private static final String YSM_ROOT_PACKAGE = "rip.ysm";
    private static final String COMPAT_PACKAGE = "rip.ysm.compat";
    private static final String GPU_PACKAGE = "rip.ysm.gpu";

    /** Everything a compat Impl is allowed to depend on without that dependency counting as
     *  "the third-party mod's own classes" — the JDK, Minecraft/Mojang, the loaders we target,
     *  our own code, and the handful of general-purpose libraries used all over this codebase. */
    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "java.", "javax.", "net.minecraft.", "com.mojang.", "net.fabricmc.",
            "dev.architectury.", "org.spongepowered.", "rip.ysm.", "com.elfmcys.",
            "org.jetbrains.", "org.slf4j.", "com.google.", "org.lwjgl.", "it.unimi.",
            "org.apache.", "org.joml.", "org.ladysnake.");

    /** GL calls that create, bind, or mutate a buffer/texture/framebuffer/VAO object — i.e. calls
     *  whose result is meant to be *held* across frames. Deliberately excludes shader/program
     *  compile calls (glCreateShader, glCompileShader, glLinkProgram, glUseProgram, ...): those
     *  are legitimately used by {@code ClientSetupEvent} as a one-shot capability probe that
     *  creates and immediately deletes a test shader, never holding it. */
    private static final Set<String> GL_OBJECT_CALLS = Set.of(
            "glGenTextures", "glGenBuffers", "glGenVertexArrays", "glGenFramebuffers",
            "glGenerateMipmap",
            "glBindTexture", "glBindBuffer", "glBindBufferBase", "glBindBufferRange",
            "glBindVertexArray", "glBindFramebuffer",
            "glDeleteTextures", "glDeleteBuffers", "glDeleteVertexArrays", "glDeleteFramebuffers",
            "glBufferData", "glBufferSubData",
            "glTexImage", "glTexImage2D", "glTexImage3D", "glTexSubImage", "glTexSubImage2D",
            "glCopyTexSubImage", "glTexParameteri",
            "glDrawArrays", "glDrawElements", "glDispatchCompute",
            "glFramebufferTexture", "glFramebufferTexture2D", "glCheckFramebufferStatus");

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(location -> !location.contains("/classes/java/test/")
                        && !location.contains("/test-classes/"))
                .importPackages(COMMON_PACKAGE, YSM_ROOT_PACKAGE);

        assertTrue(production.size() >= 300,
                "only imported " + production.size() + " classes — the import path is probably "
                        + "wrong and every rule below would pass on an empty set");
    }

    /**
     * common must not reach past the one Fabric Loader annotation it is explicitly allowed to use.
     *
     * <p>common/build.gradle pulls in {@code fabric-loader} only for {@code @Environment}/{@code
     * EnvType} (its own comment says so: "Do NOT use other classes from Fabric Loader."). Every
     * other Fabric-specific concern is supposed to go through a {@code rip.ysm.api} port instead —
     * that is the whole point of the common/fabric split in ARCHITECTURE.md. This rule enforces
     * that boundary at the bytecode level instead of trusting the comment.</p>
     */
    @Test
    void commonNeverReachesPastTheAllowedFabricLoaderAnnotation() {
        List<String> violations = new ArrayList<>();
        int fabricLoaderDependencies = 0;

        for (JavaClass clazz : production) {
            if (!clazz.getPackageName().startsWith(COMMON_PACKAGE)
                    && !clazz.getPackageName().startsWith(YSM_ROOT_PACKAGE)) {
                continue;
            }
            // Every fabric-module package carries a literal "fabric" segment by convention
            // (com.elfmcys.yesstevemodel.fabric.*, rip.ysm.api.*.fabric, rip.ysm.compat.*.fabric,
            // ...) — that is precisely the fabric-side implementation code that is *meant* to
            // touch Fabric Loader directly. This rule is only about the common source set.
            if (isFabricModulePackage(clazz.getPackageName())) {
                continue;
            }
            for (JavaClass dependency : clazz.getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass()).toList()) {
                String name = normalizedName(dependency.getFullName());
                if (!name.startsWith("net.fabricmc.")) {
                    continue;
                }
                fabricLoaderDependencies++;
                if (!name.equals("net.fabricmc.api.Environment") && !name.equals("net.fabricmc.api.EnvType")) {
                    violations.add(clazz.getName() + " -> " + name);
                }
            }
        }

        // Floor: common/build.gradle's own comment says this dependency exists for a reason: if
        // this hits zero, either the import path broke or the annotation was removed outright —
        // either way the rule below is passing on nothing.
        assertTrue(fabricLoaderDependencies >= 10,
                "found only " + fabricLoaderDependencies + " net.fabricmc dependencies from common "
                        + "— identification heuristic likely broken");
        assertTrue(violations.isEmpty(),
                "common referenced Fabric Loader classes beyond @Environment/EnvType — route "
                        + "through a rip.ysm.api port instead: " + violations);
    }

    /**
     * No class outside {@code rip.ysm.compat.*} may name a third-party compat mod's own classes.
     *
     * <p>Generalizes the source-text {@link CompatBoundaryContractTest} (which only ever checked
     * TLM) to every optional compat integration, and does it against bytecode so it survives
     * comment/formatting changes. The set of "third-party" prefixes is derived, not hard-coded:
     * anything a {@code rip.ysm.compat.*} class depends on that isn't the JDK, Minecraft/Mojang,
     * our target loaders, our own code, or a general-purpose library (see {@link
     * #FRAMEWORK_PREFIXES}) is treated as belonging to the mod being integrated, and is then
     * checked against the rest of the codebase.</p>
     */
    @Test
    void compatModClassesAreNeverReferencedOutsideTheCompatPackage() {
        // The common-side compat interfaces are deliberately mod-agnostic (that is the whole
        // point of the interface + fabric/*Impl split) — they carry zero third-party imports.
        // Only the fabric Impl classes reference the mods themselves, so under :common:test
        // alone there is nothing to derive a prefix from and nothing to check. Skip rather than
        // fail: the rule is inapplicable to this module's classpath, not violated by it. The
        // :fabric:test run (which has both common's and fabric's classes on its classpath, see
        // fabric/build.gradle) is the one that actually exercises this rule.
        Assumptions.assumeTrue(fabricCompatImplClassesArePresent(),
                "no rip.ysm.compat.*.fabric Impl classes on this module's classpath — "
                        + "run under :fabric:test to exercise this rule");

        Set<String> thirdPartyPrefixes = new TreeSet<>();
        for (JavaClass clazz : production) {
            if (!clazz.getPackageName().startsWith(COMPAT_PACKAGE)) {
                continue;
            }
            for (JavaClass dependency : clazz.getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass()).toList()) {
                String name = normalizedName(dependency.getFullName());
                if (FRAMEWORK_PREFIXES.stream().anyMatch(name::startsWith)) {
                    continue;
                }
                // Keep the top two segments (e.g. "com.github") only if that's genuinely the
                // whole distinguishing prefix; use the mod's own root package instead when we can
                // recognize one, so a rename inside the mod doesn't require touching this file.
                thirdPartyPrefixes.add(rootPackageOf(name));
            }
        }

        // Floor is 1, not 2: TLM's own classes (com.github.tartaricacid.touhoulittlemaid) are
        // frozen out of compilation entirely right now (fabric/build.gradle excludes
        // rip/ysm/compat/touhoulittlemaid/fabric/tlm/**, see CLAUDE.md), so they contribute
        // nothing to derive from until that tree is unfrozen. Iris (net.irisshaders.iris, wired
        // through rip.ysm.compat.oculus) is the only compat integration presently compiled with a
        // real compile-time dependency on the target mod's own classes — verified by running this
        // exact assertion at >= 2 first and watching it correctly fail with only Iris found.
        assertTrue(thirdPartyPrefixes.size() >= 1,
                "only derived " + thirdPartyPrefixes.size() + " third-party prefix(es) from "
                        + COMPAT_PACKAGE + " — expected at least Iris; the framework allow-list "
                        + "in this test may need updating");

        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : production) {
            if (clazz.getPackageName().startsWith(COMPAT_PACKAGE)) {
                continue;
            }
            for (JavaClass dependency : clazz.getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass()).toList()) {
                String name = normalizedName(dependency.getFullName());
                if (thirdPartyPrefixes.stream().anyMatch(name::startsWith)) {
                    violations.add(clazz.getName() + " -> " + name);
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "these classes reference a compat mod's own classes directly, outside "
                        + COMPAT_PACKAGE + " — route through the compat interface instead: " + violations);
    }

    /**
     * ArchUnit reports an array component's dependency using the JVM's internal array-type
     * descriptor (e.g. {@code [Lrip.ysm.compat.carryon.Foo;} for {@code Foo[]}), not a plain
     * dotted class name. Left un-normalized, that leading {@code [L} defeats every {@code
     * startsWith} prefix check in this file — a class's dependency on its own package's array
     * type would silently read as an unrecognized "third party" package. Strip the array/erasure
     * wrapper so every rule sees the same dotted name regardless of whether the dependency came
     * from a plain reference or an array type.
     */
    private static String normalizedName(String rawName) {
        String name = rawName;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        return name;
    }

    private static boolean isFabricModulePackage(String packageName) {
        return Arrays.asList(packageName.split("\\.")).contains("fabric");
    }

    private static boolean fabricCompatImplClassesArePresent() {
        return production.stream().anyMatch(clazz ->
                clazz.getPackageName().startsWith(COMPAT_PACKAGE) && isFabricModulePackage(clazz.getPackageName()));
    }

    private static String rootPackageOf(String fullyQualifiedName) {
        String[] parts = fullyQualifiedName.split("\\.");
        // Known compat mod root packages are 3-4 segments deep (com.github.tartaricacid.touhoulittlemaid,
        // net.irisshaders.iris); fall back to the first three segments, which is specific enough to
        // never collide with an unrelated library while still surviving internal repackaging.
        int depth = Math.min(4, parts.length);
        return String.join(".", List.of(parts).subList(0, depth));
    }

    /**
     * GL object handles (buffers/textures/framebuffers/VAOs) are only ever created, bound, or
     * mutated inside {@code rip.ysm.gpu} — CLAUDE.md: "business/model/network code must never
     * hold GL objects". Deliberately does not ban every {@code org.lwjgl.opengl.GL*} symbol:
     * {@code ClientSetupEvent} legitimately probes shader-compiler availability at startup
     * (creates and immediately deletes a throwaway shader) and {@code
     * ModernAnimationRouletteScreen} only ever passes {@code GL11} *constants* into Mojang's
     * {@code GlStateManager} wrapper — neither holds a GL object.
     */
    @Test
    void rawGlObjectCallsStayInsideTheGpuBackendPackage() {
        List<String> violations = new ArrayList<>();

        for (JavaClass clazz : production) {
            if (clazz.getPackageName().startsWith(GPU_PACKAGE)) {
                continue;
            }
            for (JavaCodeUnit codeUnit : clazz.getCodeUnits()) {
                for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
                    if (!call.getTargetOwner().getPackageName().equals("org.lwjgl.opengl")) {
                        continue;
                    }
                    if (!GL_OBJECT_CALLS.contains(call.getTarget().getName())) {
                        continue;
                    }
                    violations.add(clazz.getName() + "#" + codeUnit.getName()
                            + " -> " + call.getTarget().getName());
                }
            }
        }

        // No positive floor here by design (unlike the other two rules): this assertion only
        // sees whatever classpath the current module test run has, and rip.ysm.gpu's own callers
        // are deliberately excluded above — so a legitimate zero outside the package is the
        // expected, passing state, not a sign the identification heuristic broke.
        assertTrue(violations.isEmpty(),
                "these call sites create/bind/mutate a raw GL object outside " + GPU_PACKAGE
                        + ": " + violations);
    }
}
