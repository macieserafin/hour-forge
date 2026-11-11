package pl.macieserafin.hourforge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses request bodies larger than any form of the application needs, before they are read, validated or logged.
 * File uploads have their own, larger limit (multipart settings) and are left to it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    public static final class BodyTooLargeException extends IOException {
        BodyTooLargeException() {
            super("The request body is too large");
        }
    }

    private final long limit;

    public RequestSizeLimitFilter(@Value("${app.limits.max-body:256KB}") DataSize limit) {
        this.limit = limit.toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var type = request.getContentType();
        if (type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > limit) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        // A body of unknown length (chunked) is counted while it is read.
        chain.doFilter(new Limited(request, limit), response);
    }

    private static final class Limited extends HttpServletRequestWrapper {
        private final long limit;

        Limited(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            var delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int value = delegate.read();
                    if (value >= 0) count(1);
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = delegate.read(buffer, offset, length);
                    if (count > 0) count(count);
                    return count;
                }

                private void count(int bytes) throws IOException {
                    read += bytes;
                    if (read > limit) throw new BodyTooLargeException();
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
