package io.jenkins.plugins.portainer;

import hudson.AbortException;
import hudson.util.StreamTaskListener;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortainerConnectionsAbortOnTest {

    @Test
    void abort_throwsLoggedAbortWithoutConsoleError() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        StreamTaskListener listener = new StreamTaskListener(buf, StandardCharsets.UTF_8);
        PortainerBuildLogger log =
                new PortainerBuildLogger(Logger.getLogger("AbortOnTest"), listener, false);

        AbortException first = PortainerConnections.abort(log, "  boom  ");
        assertInstanceOf(PortainerLoggedAbort.class, first);
        assertEquals("boom", first.getMessage());
        assertFalse(log.hasLoggedError());
        assertFalse(buf.toString(StandardCharsets.UTF_8).contains("[ERROR]"));

        AbortException withCause = PortainerConnections.abort(log, "with cause", new IOException("root"));
        assertEquals("with cause", withCause.getMessage());
        assertFalse(log.hasLoggedError());
        assertFalse(buf.toString(StandardCharsets.UTF_8).contains("[ERROR]"));

        AbortException empty = PortainerConnections.abort(quietLog(), "   ");
        assertEquals("failed", empty.getMessage());

        PortainerLoggedAbort already = new PortainerLoggedAbort("already");
        assertSame(already, PortainerConnections.abort(log, "other", already));
        assertEquals("failed", new PortainerLoggedAbort(null).getMessage());
        assertEquals("failed", new PortainerLoggedAbort("  ").getMessage());
    }

    @Test
    void abortOn_wrapsIllegalArgumentAndAbort() throws Exception {
        assertEquals(7, PortainerConnections.abortOn(quietLog(), () -> 7));
        AbortException arg = assertThrows(
                AbortException.class,
                () -> PortainerConnections.abortOn(quietLog(), () -> {
                    throw new IllegalArgumentException("bad field");
                }));
        assertTrue(arg.getMessage().contains("bad field"));
        AbortException abort = assertThrows(
                AbortException.class,
                () -> PortainerConnections.abortOn(quietLog(), () -> {
                    throw new AbortException("step failed");
                }));
        assertTrue(abort.getMessage().contains("step failed"));
    }

    private static PortainerBuildLogger quietLog() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        StreamTaskListener listener = new StreamTaskListener(buf, StandardCharsets.UTF_8);
        return new PortainerBuildLogger(Logger.getLogger("AbortOnTest"), listener, false);
    }
}
