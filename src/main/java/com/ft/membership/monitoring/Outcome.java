package com.ft.membership.monitoring;

import com.ft.membership.common.types.userid.UserId;
import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class Outcome {

    public enum DomainObjectKey {
        UserId("userId"),
        UserEmail("userEmail"),
        ErightsId("erightsId"),
        ErightsGroupId("erightsGroupId");

        private final String key;

        DomainObjectKey(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public static Operation operation(final String operation) {
        return new Operation(operation);
    }
    
    public static class Operation extends Parameters implements AutoCloseable {

        private final String operation;
        private boolean terminated;
        private Object actorOrLogger;

        public Operation(final String operation) {
            checkNotNull(operation, "require operation");
            this.operation = operation;
        }
        
        public Operation with(final UserId id) {
            putNoWrap(DomainObjectKey.UserId.getKey(), id);
            return this;
        }

        public Operation with(final DomainObjectKey key, final Object value) {
            return with(key.getKey(),value);
        }

        public Operation with(final String key, final Object value) {
            putWrapped(key, value);
            return this;
        }

        public Operation started(final Object actorOrLogger) {
            this.actorOrLogger = actorOrLogger;
            new LogFormatter(actorOrLogger).logInfo(this);
            return this;
        }

        public Yield wasSuccessful() {
            return new Yield(this);
        }

        public Failure wasFailure() {
            return new Failure(this);
        }

        protected void terminated() {
            this.terminated = true;
        }

        protected String getName() {
            return operation;
        }

        @Override
        public void close() {
            if(!terminated) {
                this.wasFailure()
                        .throwingException(new RuntimeException("operation auto-closed")) // so we at least get a stack-trace
                        .log(Optional.fromNullable(actorOrLogger).or(this));
            }
        }
    }

    public static interface LoggingTerminal {
        public void log(Object actorOrLogger);
    }

    public static class Yield extends Parameters implements LoggingTerminal {
        private final Operation operation;

        public Yield(final Operation operation) {
            this.operation = operation;
        }

        public Yield yielding(final UserId userId) {
            putNoWrap("userId", userId);
            return this;
        }

        public Yield yielding(final String key, final Object value) {
            putWrapped(key, value);
            return this;
        }

        public Yield yielding(final DomainObjectKey key, final Object value) {
            putWrapped(key.getKey(), value);
            return this;
        }

        public void log(final Object actorOrLogger) {
            logInfo(actorOrLogger);
        }
        
        public void logInfo(Object actorOrLogger) {
            new LogFormatter(actorOrLogger).logInfo(operation, this);
        }

    }

    public static class Failure extends Parameters implements LoggingTerminal {
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
            putWrapped(key,detail);
            return this;
        }

        public boolean didThrow() {
            return thrown != null;
        }

        public Exception getThrown() {
            return thrown;
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

    protected static class Parameters {
        private Map<String,Object> params = new LinkedHashMap<>();

        protected void putWrapped(String key, Object value) {
            checkNotNull(key, "require key");
            if(value instanceof Number) {
                putNoWrap(key, value);
            } else {
                params.put(key, new ToStringWrapper(value));
            }
        }

        protected void putNoWrap(String key, Object value) {
            checkNotNull(key, "require key");
            params.put(key,value);
        }

        protected Map<String, Object> getParameters() {
            return params;
        }
    }


    protected static class LogFormatter {
        private static final String OUTCOME_IS_SUCCESS = "success";
        private static final String OUTCOME_IS_FAILURE = "failure";

        private final Logger logger;

        protected LogFormatter(Object actorOrLogger) {
            checkNotNull("require actor or logger");
            if(actorOrLogger instanceof Logger) {
                logger = (Logger) actorOrLogger;
            } else {
                logger = LoggerFactory.getLogger(actorOrLogger.getClass());
            }
        }

        public void logInfo(final Operation operation) {
            final Map<String, Object> all = new LinkedHashMap<>();
            addOperation(operation, all);

            logger.info(buildFormatString(all), buildArgumentArray(all));
        }

        protected void logInfo(final Operation operation, Yield yield) {
            operation.terminated();

            final Map<String, Object> all = new LinkedHashMap<>();
            addOperation(operation, all);
            addOutcome(OUTCOME_IS_SUCCESS, all);
            addYield(yield, all);

            logger.info(buildFormatString(all), buildArgumentArray(all));
        }

        protected void logError(final Operation operation, Yield yield) {
            operation.terminated();

            final Map<String, Object> all = new LinkedHashMap<>();
            addOperation(operation, all);
            addOutcome(OUTCOME_IS_SUCCESS, all);
            addYield(yield, all);

            logger.error(buildFormatString(all), buildArgumentArray(all));
        }
        
        protected void logInfo(Operation operation, Failure failure) {
            operation.terminated();
            if (logger.isInfoEnabled()) {
                String failureMessage = buildFailureMessage(operation, failure);

                if(failure.didThrow()) {
                    logger.info(failureMessage, failure.getThrown());
                } else {
                    logger.info(failureMessage);
                }
            }
        }

        protected void logError(final Operation operation, Failure failure) {
            operation.terminated();
            if (logger.isErrorEnabled()) {
                String failureMessage = buildFailureMessage(operation, failure);
                
                if(failure.didThrow()) {
                    logger.error(failureMessage, failure.getThrown());
                } else {
                    logger.error(failureMessage);
                }
            }
        }

        private String buildFormatString(final Map<String, Object> formatParameters) {
            final StringBuilder format = new StringBuilder();
            int i = formatParameters.size();
            for(String key : formatParameters.keySet()) {
                format.append(key).append("={}");
                i--;
                if(i > 0) format.append(" ");
            }
            return format.toString();
        }

        private Object[] buildArgumentArray(final Map<String, Object> formatParameters) {
            return formatParameters.values().toArray(new Object[formatParameters.size()]);
        }
        
        private String buildFailureMessage(final Operation operation, Failure failure) {
            final Map<String, Object> all = new LinkedHashMap<>();
            addOperation(operation, all);
            addOutcome(OUTCOME_IS_FAILURE, all);
            addFailure(failure, all);
            return flatten(all);
        }

        private void addOperation(final Operation operation, final Map<String, Object> msgParams) {
            msgParams.put("operation", operation.getName());
            msgParams.putAll(operation.getParameters());
        }

        private void addOutcome(String outcome, final Map<String, Object> msgParams) {
            msgParams.put("outcome", outcome);
        }

        private void addYield(Yield yield, final Map<String, Object> all) {
            all.putAll(yield.getParameters());
        }

        private void addFailure(Failure failure, final Map<String, Object> msgParams) {
            msgParams.putAll(failure.getParameters());
            
            if(failure.didThrow()) {
                msgParams.put("exception",new ToStringWrapper(failure.getThrown().toString()));
            }
        }

        private String flatten(final Map<String, Object> msgParameters) {
            final StringBuilder flattened = new StringBuilder();
            int i = 0;
            for (Map.Entry<String, Object> entry : msgParameters.entrySet()) {
                if(i > 0) flattened.append(" ");
                flattened
                        .append(entry.getKey())
                        .append("=")
                        .append(entry.getValue());
                i++;
            }
            return flattened.toString();
        }
    }

}