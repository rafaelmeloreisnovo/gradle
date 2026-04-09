package org.gradle.vectra.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.gradle.vectra.runtime.VectraNativeLoadException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectraRuntimeServiceTest {

    private final TestVectraRuntimeService service = new TestVectraRuntimeService();

    @AfterEach
    void clearOverride() {
        System.clearProperty("org.gradle.vectra.native.available");
    }

    @Test
    void fallsBackFromAsmToCWhenAssemblerIsUnavailableButCIsConfigured() {
        VectraBackend backend = service.resolveBackend("asm", true, "", "clang", "");

        assertEquals(VectraBackend.C, backend);
    }

    @Test
    void fallsBackToJavaWhenNoNativeBackendIsAvailable() {
        VectraBackend backend = service.resolveBackend("asm", true, "", "", "");

        assertEquals(VectraBackend.JAVA, backend);
    }

    @Test
    void usesExplicitAssemblerToolWhenConfigured() {
        VectraBackend backend = service.resolveBackend("asm", true, "as", "", "");

        assertEquals(VectraBackend.ASM, backend);
    }

    @Test
    void throwsSpecificExceptionForInvalidBackendValues() {
        assertThrows(VectraNativeLoadException.class, () -> service.resolveBackend("gpu", true, "", "", ""));
    }

    @Test
    void resolvesBackendFromExplicitToolchainDirectory() throws Exception {
        java.io.File toolchainDir = new java.io.File("build/tmp/vectra-toolchain-test");
        toolchainDir.mkdirs();
        new java.io.File(toolchainDir, "cc").createNewFile();

        VectraBackend backend = service.resolveBackend("c", true, "", "", toolchainDir.getAbsolutePath());

        assertEquals(VectraBackend.C, backend);
    }

    @Test
    void honorsGlobalNativeOverrideProperty() {
        System.setProperty("org.gradle.vectra.native.available", "true");

        VectraBackend backend = service.resolveBackend("c", true, "", "", "");

        assertEquals(VectraBackend.C, backend);
    }

    private static class TestVectraRuntimeService extends VectraRuntimeService {
    }
}
