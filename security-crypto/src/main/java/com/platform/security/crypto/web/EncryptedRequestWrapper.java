package com.platform.security.crypto.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Swaps in decrypted bytes for the request body - everything downstream
 * (Jackson's {@code @RequestBody} binding, any other filter/interceptor
 * that reads the body) sees the decrypted plaintext transparently, with no
 * awareness that decryption happened at all.
 */
public class EncryptedRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] decryptedBody;

    public EncryptedRequestWrapper(HttpServletRequest request, String decryptedBody) {
        super(request);
        this.decryptedBody = decryptedBody.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(decryptedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // no-op - this wrapper serves an in-memory, already-fully-available byte array
            }

            @Override
            public int read() {
                return byteStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return decryptedBody.length;
    }

    @Override
    public long getContentLengthLong() {
        return decryptedBody.length;
    }
}
