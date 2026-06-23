package io.livelattice.search.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuthHeaderRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> headers;

    public AuthHeaderRequestWrapper(HttpServletRequest request, Map<String, String> headers) {
        super(request);
        this.headers = headers;
    }

    @Override
    public String getHeader(String name) {
        String value = headers.get(name.toLowerCase());
        return value != null ? value : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = headers.get(name.toLowerCase());
        if (value != null) {
            return Collections.enumeration(List.of(value));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> current = super.getHeaderNames();
        while (current.hasMoreElements()) {
            names.add(current.nextElement());
        }
        names.addAll(headers.keySet());
        return Collections.enumeration(names);
    }
}
