package org.gradle.vectra.plugin;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class VectraOrchestratorExtension {
    @Inject
    public VectraOrchestratorExtension(ObjectFactory objects) {
        getEnabled().convention(true);
        getDeterministic().convention(true);
        getBackend().convention(VectraBackend.JAVA.name().toLowerCase());
        getAssemblerTool().convention("");
        getCCompilerTool().convention("");
    }

    public abstract Property<Boolean> getEnabled();

    public abstract Property<Boolean> getDeterministic();

    public abstract Property<String> getBackend();

    /**
     * Optional explicit assembler command/path (for example: as, clang, ml64, or an absolute path).
     */
    public abstract Property<String> getAssemblerTool();

    /**
     * Optional explicit C compiler command/path (for example: cc, clang, cl, gcc, or an absolute path).
     */
    public abstract Property<String> getCCompilerTool();

    /**
     * Optional toolchain directory to prioritize over PATH-based probing.
     */
    public abstract DirectoryProperty getToolchainDirectory();
}
