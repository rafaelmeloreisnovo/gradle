package org.gradle.vectra.plugin;

public class VectraStateCorruptedException extends RuntimeException {

    public VectraStateCorruptedException(String message) {
        super(message);
    }

    public VectraStateCorruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
