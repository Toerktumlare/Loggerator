package com.github.tandolf.loggerator;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tandolf.loggerator.core.LogTransaction;
import com.github.tandolf.loggerator.core.Loggerator;
import com.github.tandolf.loggerator.models.LogEvent;
import com.github.tandolf.loggerator.models.MethodData;
import com.github.tandolf.loggerator.models.SpringMethodLogEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class SpringAOPMethodDataTests {

    private static ObjectMapper objectMapper;
    private static Loggerator loggerator;
    private static TestConsoleAppender appender;
    private Signature signature;
    private ProceedingJoinPoint joinPoint;

    @BeforeClass
    public static void beforeClass() {
        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(NON_NULL);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);

        appender = getTestAppender();

        loggerator = Loggerator.builder()
                .setAppender(appender)
                .setObjectMapper(objectMapper)
                .build();
    }

    @Before
    public void before() {
        signature = Mockito.mock(Signature.class);
        joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        appender.logs.clear();
    }

    @Test
    public void shouldLogFullName() throws Throwable {
        final LogTransaction logTransaction = loggerator.createTransaction();
        final String packageName = this.getClass().getPackageName();
        final String name = "someName";

        when(signature.getName()).thenReturn(name);
        when(signature.getDeclaringTypeName()).thenReturn(packageName);

        final LogEvent logEvent = new SpringMethodLogEvent(joinPoint);

        logTransaction.execute(logEvent);

        final MethodData actual = getLogs();

        final String fullName = packageName.concat(".").concat(name);

        assertEquals(fullName, actual.getName());
    }

    @Test
    public void shouldLogInputArguments() throws Throwable {
        final LogTransaction logTransaction = loggerator.createTransaction();

        final String[] args = {"arg1, arg2"};

        when(joinPoint.getArgs()).thenReturn(args);

        final LogEvent logEvent = new SpringMethodLogEvent(joinPoint);

        logTransaction.execute(logEvent);

        final MethodData actual = getLogs();

        assertArrayEquals(args, actual.getArgs());
    }

    @Test
    public void shouldLogDataContainingTimeStamps() throws Throwable {
        final LogTransaction transaction = loggerator.createTransaction();
        transaction.execute(new SpringMethodLogEvent(joinPoint));

        final MethodData actual = getLogs();

        assertNotNull(actual.getStart());
        assertNotNull(actual.getEnd());
        assertNotNull(actual.getDuration());
    }

    @Test
    public void shouldLogReturnValueInLogData() throws Throwable {
        final LogTransaction transaction = loggerator.createTransaction();

        when(joinPoint.proceed()).thenReturn("someReturnValue");

        transaction.execute(new SpringMethodLogEvent(joinPoint));

        final MethodData actual = getLogs();

        assertEquals("someReturnValue", actual.getReturnValue());
    }

    @Test
    public void shouldLogExceptionIfProceedThrowsException() throws Throwable {
        final LogTransaction transaction = loggerator.createTransaction();

        when(joinPoint.proceed())
                .thenThrow(new AccessDeniedException("Some exception")
                        .initCause(new NullPointerException("There was a random null pointer")));

        try {
            transaction.execute(new SpringMethodLogEvent(joinPoint));
        } catch (Throwable throwable) {
            final MethodData actual = getLogs();
            assertTrue(actual.getReturnValue().toString().contains("NullPointerException: There was a random null pointer"));
            assertFalse(actual.isReturnStatus());

        }
    }

    @Test
    public void shouldLogThreeLogsNested() throws Throwable {
        final LogTransaction logTransaction = loggerator.createTransaction();

        final String firstMethodName = "first";
        final String secondMethodName = "second";
        final String thirdMethodName = "third";
        final String packageName = this.getClass().getPackageName();

        when(signature.getName())
                .thenReturn(firstMethodName)
                .thenReturn(secondMethodName)
                .thenReturn(thirdMethodName);
        when(signature.getDeclaringTypeName())
                .thenReturn(packageName);

        final LogEvent firstMethod = new SpringMethodLogEvent(joinPoint);
        final LogEvent secondMethod = new SpringMethodLogEvent(joinPoint);
        final LogEvent thirdMethod = new SpringMethodLogEvent(joinPoint);

        when(joinPoint.proceed())
                .then(invocationOnMock -> logTransaction.execute(secondMethod))
                .then(invocationOnMock -> logTransaction.execute(thirdMethod))
                .thenReturn("");

        logTransaction.execute(firstMethod);

        final MethodData actual = getLogs();

        assertEquals(1, actual.getMethods().size());
        assertEquals(1, actual.getMethods().getFirst().getMethods().size());
        assertNull(actual.getMethods().getFirst().getMethods().getFirst().getMethods());
    }

    @Test
    public void shouldLogThreadName() throws Throwable {
        final Thread thread = Thread.currentThread();
        final LogTransaction logTransaction = loggerator.createTransaction();
        final SpringMethodLogEvent logEvent = new SpringMethodLogEvent(joinPoint);
        logTransaction.execute(logEvent);
        final MethodData logs = getLogs();
        assertEquals("main", logs.getThread());
    }

    private static TestConsoleAppender getTestAppender() {

        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        final PatternLayoutEncoder pattern = new PatternLayoutEncoder();
        pattern.setPattern("%msg");
        pattern.setContext(lc);
        pattern.start();

        final TestConsoleAppender testConsoleAppender = new TestConsoleAppender();
        testConsoleAppender.setEncoder(pattern);
        testConsoleAppender.setContext(lc);
        testConsoleAppender.setName("Transactions");
        testConsoleAppender.start();

        return testConsoleAppender;
    }

    private static class TestConsoleAppender extends ConsoleAppender<ILoggingEvent> {

        private List<String> logs = new ArrayList<>();

	    @Override
        protected void append(ILoggingEvent eventObject) {
	        logs.add(eventObject.getFormattedMessage());
            super.append(eventObject);
        }
    }

    private MethodData getLogs() throws IOException {
        return objectMapper.readValue(appender.logs.get(0), MethodData.class);
    }
}
