package com.ft.membership.monitoring;

import static com.google.common.base.Preconditions.checkNotNull;

public class Failure extends Parameters implements LoggingTerminal {
    private Operation operation;
    private Exception thrown;

    public Failure(final Operation operation) {
        this.operation = operation;
    }

    public Failure throwingException(final Exception e) {
        this.thrown = checkNotNull(e, "require exception");
        return this;
    }

    public Failure withMessage(final String message) {
        putWrapped("errorMessage", message);
        return this;
    }

    public Failure withDetail(final String key, final Object detail) {
        putWrapped(key, detail);
        return this;
    }

    boolean didThrow() {
        return thrown != null;
    }

    Exception getThrown() {
        return thrown;
    }

    public void log() {
        logError(operation.getActorOrLogger());
    }

    public void log(Object actorOrLogger) {
        logError(actorOrLogger);
    }

    public void logInfo(Object actorOrLogger) {
        new LogFormatter(actorOrLogger).logInfo(operation, this);
    }

    public void logError(Object actorOrLogger) {
        new LogFormatter(actorOrLogger).logError(operation, this);
    }

}