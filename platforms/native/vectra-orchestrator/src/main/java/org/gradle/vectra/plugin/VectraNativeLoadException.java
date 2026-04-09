package org.gradle.vectra.plugin;

public class VectraNativeLoadException extends RuntimeException {

    public VectraNativeLoadException(String message) {
        super(message);
    }

    public VectraNativeLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
