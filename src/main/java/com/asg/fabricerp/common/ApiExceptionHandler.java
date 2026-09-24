package com.asg.fabricerp.common;

import com.asg.fabricerp.security.SelfGrantException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.UrlPathHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns the exceptions services throw on purpose into a status and a sentence the screen can
 * show, for {@code /api/**} only.
 *
 * <p>Without this every refusal — a duplicate username, a self-grant, a role still in use —
 * reached the browser as a bare 500 with the message stripped (Spring Boot's default, correctly:
 * an arbitrary exception message can carry internals). The services here throw
 * {@link IllegalArgumentException} / {@link IllegalStateException} with messages written for
 * the person at the screen, so those are exactly the ones worth passing through.
 *
 * <p>Page requests are rethrown untouched. Rethrowing the same exception from an
 * {@code @ExceptionHandler} is Spring's documented way to decline it: the original propagates
 * to the security filter chain (a denied page still becomes a 403) and the normal error page.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        declineUnlessApi(ex, request);
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** A rule refusing the current state — a role still assigned, a second marketing team. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex, HttpServletRequest request) {
        declineUnlessApi(ex, request);
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Includes {@code SelfGrantException}'s sentence — the reason is the useful part. */
    @ExceptionHandler({AccessDeniedException.class, SelfGrantException.class})
    public ResponseEntity<Map<String, Object>> forbidden(RuntimeException ex, HttpServletRequest request) {
        declineUnlessApi(ex, request);
        return body(HttpStatus.FORBIDDEN, ex.getMessage() == null || ex.getMessage().isBlank()
            ? "You do not have permission to do that." : ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> staleEdit(ObjectOptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        declineUnlessApi(ex, request);
        return body(HttpStatus.CONFLICT,
            "Somebody else changed this record while you were editing it. Reload and try again.");
    }

    /** The constraint's own text names tables and columns; the caller gets a sentence instead. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException ex,
                                                         HttpServletRequest request) {
        declineUnlessApi(ex, request);
        log.info("Integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT,
            "That change conflicts with existing data (a duplicate, or a record still in use).");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException ex,
                                                       HttpServletRequest request) throws MethodArgumentNotValidException {
        if (!isApi(request)) throw ex;
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(e -> e.getField(), e -> String.valueOf(e.getDefaultMessage()),
                (first, second) -> first, LinkedHashMap::new));
        String summary = fields.entrySet().stream()
            .map(e -> e.getKey() + " " + e.getValue())
            .collect(Collectors.joining("; "));
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.BAD_REQUEST, "Please check: " + summary);
        response.getBody().put("fields", fields);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        declineUnlessApi(ex, request);
        return body(HttpStatus.BAD_REQUEST, "The request could not be read. Check the values entered.");
    }

    /**
     * Anything unplanned: logged in full with a reference, answered with only the reference —
     * enough to find the stack trace, nothing about the internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (!isApi(request)) throw ex;
        // Spring's own 4xx (missing parameter, wrong method, unknown path) keep their status.
        if (ex instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            String detail = error.getBody().getDetail();
            return body(status, detail == null ? status.getReasonPhrase() : detail);
        }
        ResponseStatus annotated = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (annotated != null) {
            return body(annotated.code(), ex.getMessage());
        }
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled error [{}] on {} {}", reference, request.getMethod(), request.getRequestURI(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong on the server. Reference: " + reference);
    }

    private static void declineUnlessApi(RuntimeException ex, HttpServletRequest request) {
        if (!isApi(request)) throw ex;
    }

    /** Path within the application, not the servlet path, which is empty under some mappings. */
    private static boolean isApi(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request).startsWith("/api/");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
