package com.example.component;

import com.example.utils.SubMillis;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import static com.example.utils.LongStringUtils.formatLongString;
import static java.time.temporal.ChronoField.*;

@Slf4j
@Aspect
@Component
public class ExecutionTimeLogger {

    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendLiteral('.')
            .appendFraction(MILLI_OF_SECOND, 0, 3, false)
            .optionalStart()
            .appendLiteral("ms ")
            .optionalStart()
            .appendFraction(SubMillis.MICROS_OF_MILLI_SECOND, 0, 3, false)
            .appendLiteral("us ")
            .appendFraction(SubMillis.NANOS_OF_MICRO_SECOND, 0, 3, false)
            .appendLiteral("ns")
            .toFormatter(Locale.ROOT);

    @Pointcut("within(@com.example.annotation.TimedMethod *)")
    public void beanAnnotatedWithTimed() {
        // Pointcut
    }

    @Pointcut("execution(public * *(..))")
    public void publicMethod() {
        // Pointcut
    }

    @Pointcut("publicMethod() && beanAnnotatedWithTimed()")
    public void publicMethodInsideAClassMarkedWithAtTimed() {
        // Pointcut
    }

    @Pointcut("execution(* *(..)) && @annotation(com.example.annotation.TimedMethod)")
    public void methodMarkedWithAtTimed() {
        // Pointcut
    }

    @Around("publicMethodInsideAClassMarkedWithAtTimed() || methodMarkedWithAtTimed()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long start = System.nanoTime();
        Object result = point.proceed();

        if (log.isInfoEnabled()) {
            long nanos = System.nanoTime() - start;
            String duration = formatDuration(nanos);
            Signature signature = point.getSignature();
            Class declaringType = signature.getDeclaringType();

            if (log.isDebugEnabled()) {
                log.debug("class: {}, method: {}, time: {} ({}), args {}, result {}", declaringType.getSimpleName(),
                        signature.getName(), duration, nanos, formatLongString(point.getArgs()), formatLongString(result));
            } else {
                log.info("class: {}, method: {}, time: {} ({})", declaringType.getSimpleName(), signature.getName(),
                        duration, nanos);
            }
        }

        return result;
    }

    public static String formatDuration(long nanos) {
        return TIME_FORMATTER.format(LocalTime.ofNanoOfDay(nanos));
    }

}
