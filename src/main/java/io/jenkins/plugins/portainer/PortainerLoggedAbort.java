package io.jenkins.plugins.portainer;

import hudson.AbortException;

/**
 * Distinct from a raw {@link AbortException} so {@code abort()} does not wrap the same failure twice.
 */
final class PortainerLoggedAbort extends AbortException {

    PortainerLoggedAbort(String message) {
        super(message == null || message.isBlank() ? "failed" : message);
    }
}
