package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class NetworkHelper {
    private static final Logger log = LoggerFactory.getLogger(NetworkHelper.class);

    // Simple cookie store for all requests (not domain/path specific)
    private final Map<String, String> cookieStore = new java.util.HashMap<>();

    public static class HttpResponse {
        public int status;                          // -1 for error
        public String body;                         // response body or error message
    }

    public HttpResponse getRequest(String urlStr) {
        // TODO: come up with some default headers
        return getRequest(urlStr, null);
    }

    public HttpResponse getRequest(String urlStr, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(false);

            addHeaders(conn, headers);
            addCookies(conn);

            response.status = conn.getResponseCode();
            log.debug("getRequest: {}, http:{}", urlStr, response.status);
            storeCookies(conn.getHeaderFields());
            // http:302 has no body
            if (conn.getContentLength() == 0) return response;

            InputStream inputStream = getInputStream(conn);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                response.body = sb.toString();
            }
        } catch (Exception e) {
            log.error("getRequest: error connecting to hub: {}, {}", urlStr, e.getMessage());
            response.status = -1;
            response.body = e.getMessage();
        }
        return response;
    }

    private InputStream getInputStream(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
        String encoding = conn.getContentEncoding();
        if ("gzip".equalsIgnoreCase(encoding)) {
            inputStream = new GZIPInputStream(inputStream);
        } else if ("deflate".equalsIgnoreCase(encoding)) {
            inputStream = new InflaterInputStream(inputStream);
        }
        return inputStream;
    }

    public HttpResponse postRequest(String urlStr, String body) {
        // TODO: come up with some default headers
        return postRequest(urlStr, body, null);
    }

    public HttpResponse postRequest(String urlStr, String body, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);

            addHeaders(conn, headers);
            addCookies(conn);

            // Send body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            response.status = conn.getResponseCode();
            log.debug("postRequest: {}, http:{}, body:{}", urlStr, response.status, body);
            storeCookies(conn.getHeaderFields());
            // http:302 has no body
            if (conn.getContentLength() == 0) return response;

            InputStream inputStream = getInputStream(conn);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line.trim());
                }
                response.body = sb.toString();
            }
            // only log body if error
            if (response.status != 200) {
                log.trace("postRequest: {}", response.body);
            }
        } catch (Exception e) {
            log.error("postRequest: error connecting to hub: {}, {}", urlStr, e.getMessage());
            response.status = -1;
            response.body = e.getMessage();
        }
        return response;
    }

    private void addHeaders(HttpURLConnection conn, Map<String, String> headers) {
        // Set request headers if provided
        boolean hasReferer = false;
        if (headers != null) {
            //log.trace("addHeaders: {}", GsonHelper.toJson(headers));
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                if ("Referer".equalsIgnoreCase(key)) {
                    hasReferer = true;
                }
                String value = entry.getValue();
                conn.setRequestProperty(key, value);
            }
        }
        if (!hasReferer) {
            // always add referer header
            conn.setRequestProperty("Referer", conn.getURL().toString());
            //log.trace("addHeaders: Referer: {}", conn.getURL().toString());
        }
    }

    private void addCookies(HttpURLConnection conn) {
        // Add cookies if present
        String cookieHeader = getCookieHeader();
        if (cookieHeader != null) {
            //log.trace("addCookies: cookie: " + cookieHeader);
            conn.setRequestProperty("Cookie", cookieHeader);
        }
    }

    private void storeCookies(Map<String, List<String>> headers) {
        if (headers == null) return;
        List<String> setCookies = headers.get("Set-Cookie");
        if (setCookies != null) {
            for (String cookie : setCookies) {
                int eq = cookie.indexOf('=');
                int semi = cookie.indexOf(';');
                if (eq > 0) {
                    String name = cookie.substring(0, eq).trim();
                    String value = semi > eq ? cookie.substring(eq + 1, semi) : cookie.substring(eq + 1);
                    //log.trace("storeCookies: name: {}, value: {}", name, value);
                    cookieStore.put(name, value);
                }
            }
        }
    }

    private String getCookieHeader() {
        if (cookieStore.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieStore.entrySet()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Ensure url starts with http/https and does not end with /
     */
    public String formatUrl(String url) {
        if (!TextUtils.startsWith(url, "http")) {
            url = "https://" + url;
        }
        if (TextUtils.endsWith(url, "/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
