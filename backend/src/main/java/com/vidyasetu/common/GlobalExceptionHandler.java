package com.vidyasetu.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).body(pd);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> api(ApiException e) { return problem(e.getStatus(), e.getMessage()); }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> denied(Exception e) { return problem(HttpStatus.FORBIDDEN, "Access denied"); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).reduce((a, b) -> a + "; " + b).orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds 20 MB limit");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> illegal(IllegalArgumentException e) { return problem(HttpStatus.BAD_REQUEST, e.getMessage()); }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> noResource(NoResourceFoundException e) { return problem(HttpStatus.NOT_FOUND, "Not found"); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> other(Exception e) {
        log.error("Unhandled error: {}", e.toString(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }
}
