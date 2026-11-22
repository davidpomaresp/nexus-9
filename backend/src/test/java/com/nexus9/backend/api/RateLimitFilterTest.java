package com.nexus9.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:30Z"), ZoneOffset.UTC);

    @Test
    void rejectsWritesOverTheLimitPerClient() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, clock);

        assertThat(post(filter, "1.1.1.1")).isEqualTo(200);
        assertThat(post(filter, "1.1.1.1")).isEqualTo(200);
        assertThat(post(filter, "1.1.1.1")).isEqualTo(429);
        assertThat(post(filter, "2.2.2.2")).isEqualTo(200);
    }

    @Test
    void readsAreNotLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, clock);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/simulations/x");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void theCountersResetEveryMinute() throws Exception {
        MutableClock time = new MutableClock(Instant.parse("2026-11-22T10:00:30Z"));
        RateLimitFilter filter = new RateLimitFilter(1, time);
        assertThat(post(filter, "1.1.1.1")).isEqualTo(200);
        assertThat(post(filter, "1.1.1.1")).isEqualTo(429);

        time.now = Instant.parse("2026-11-22T10:01:00Z");

        assertThat(post(filter, "1.1.1.1")).isEqualTo(200);
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void usesTheAddressAddedByTheProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/simulations");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 1.1.1.1");

        assertThat(RateLimitFilter.client(request)).isEqualTo("1.1.1.1");
    }

    private static int post(RateLimitFilter filter, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/simulations");
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
