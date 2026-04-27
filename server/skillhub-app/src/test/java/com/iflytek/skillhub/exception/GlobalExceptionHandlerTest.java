package com.iflytek.skillhub.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.security.SensitiveLogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private SensitiveLogSanitizer sensitiveLogSanitizer;

    @Mock
    private SkillHubMetrics metrics;

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.request.timeout", java.util.Locale.getDefault(), "Request timed out");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-20T00:00:00Z"), ZoneOffset.UTC)
        );
        handler = new GlobalExceptionHandler(responseFactory, sensitiveLogSanitizer, metrics);
    }

    @Test
    void handleAsyncRequestTimeout_shouldReturnNoContentForSseRequests() {
        when(request.getRequestURI()).thenReturn("/api/v1/notifications/sse");

        ResponseEntity<?> response = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void handleAsyncRequestTimeout_shouldReturnApiEnvelopeForNonSseRequests() {
        when(request.getRequestURI()).thenReturn("/api/v1/publish");
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/publish");

        ResponseEntity<?> response = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.code()).isEqualTo(408);
        assertThat(body.msg()).isEqualTo("Request timed out");
    }

    @Test
    void handleValidation_returnsFirstFieldErrorMessageAs400() throws Exception {
        Method dummyMethod = DummyTarget.class.getMethod("create", DummyTarget.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DummyTarget(), "dummy");
        bindingResult.addError(new FieldError("dummy", "name", "name must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(dummyMethod, 0), bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo(400);
        assertThat(body.msg()).isEqualTo("name must not be blank");
    }

    static class DummyTarget {
        public DummyTarget create(DummyTarget body) {
            return body;
        }
    }
}
