/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.core.http.client.vertx;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.definition.model.Proxy;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.http.client.AsyncResponseHandler;
import io.gravitee.gateway.core.definition.Api;
import io.gravitee.gateway.core.http.client.AbstractHttpClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
public class VertxHttpClient extends AbstractHttpClient {

    private final Logger LOGGER = LoggerFactory.getLogger(VertxHttpClient.class);

    private HttpClient httpClient;

    @Autowired
    private Vertx vertx;

    public VertxHttpClient(Api api) {
        super(api);
    }

    @Override
    public void invoke(Request serverRequest, AsyncResponseHandler clientResponseHandler) {
        URI rewrittenURI = rewriteURI(serverRequest);
        String url = rewrittenURI.toString();
        LOGGER.debug("{} rewriting: {} -> {}", serverRequest.id(), serverRequest.uri(), url);

        HttpClientRequest clientRequest = httpClient.request(convert(serverRequest.method()), url,
                clientResponse -> handleClientResponse(clientResponse, clientResponseHandler));

        clientRequest.exceptionHandler(event -> {
            LOGGER.error(serverRequest.id() + " server proxying failed", event);

            if (event instanceof TimeoutException) {
                clientResponseHandler.onStatusReceived(HttpStatusCode.GATEWAY_TIMEOUT_504);
            } else {
                clientResponseHandler.onStatusReceived(HttpStatusCode.BAD_GATEWAY_502);
            }

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.set(HttpHeaders.CONNECTION, HttpHeadersValues.CONNECTION_CLOSE);

            clientResponseHandler.onHeadersReceived(httpHeaders);
            clientResponseHandler.onComplete();
        });

        copyRequestHeaders(serverRequest, clientRequest);

        if (hasContent(serverRequest)) {
            String transferEncoding = serverRequest.headers().getFirst(HttpHeaders.TRANSFER_ENCODING);
            if (HttpHeadersValues.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncoding)) {
                clientRequest.setChunked(true);
            }

            serverRequest.bodyHandler(bodyPart -> {
                ByteBuffer byteBuffer = bodyPart.getBodyPartAsByteBuffer();
                LOGGER.debug("{} proxying content to upstream: {} bytes", serverRequest.id(), byteBuffer.remaining());
                clientRequest.write(byteBuffer.toString());
            });
        }

        serverRequest.endHandler(result -> {
            LOGGER.debug("{} proxying complete", serverRequest.id());
            clientRequest.end();
        });
    }

    private void handleClientResponse(HttpClientResponse clientResponse,
                                      AsyncResponseHandler clientResponseHandler){
        // Copy HTTP status
        clientResponseHandler.onStatusReceived(clientResponse.statusCode());

        // Copy HTTP headers
        HttpHeaders httpHeaders = new HttpHeaders();
        clientResponse.headers().forEach(header ->
                httpHeaders.add(header.getKey(), header.getValue()));

        clientResponseHandler.onHeadersReceived(httpHeaders);

        // Copy body content
        clientResponse.handler(buffer -> {
            clientResponseHandler.onBodyPartReceived(new VertxBufferBodyPart(buffer));
        });

        // Signal end of the response
        clientResponse.endHandler((v) -> clientResponseHandler.onComplete());
    }

    protected void copyRequestHeaders(Request clientRequest, HttpClientRequest httpClientRequest) {
        for (Map.Entry<String, List<String>> headerValues : clientRequest.headers().entrySet()) {
            String headerName = headerValues.getKey();
            String lowerHeaderName = headerName.toLowerCase(Locale.ENGLISH);

            // Remove hop-by-hop headers.
            if (HOP_HEADERS.contains(lowerHeaderName))
                continue;

            headerValues.getValue().forEach(headerValue -> httpClientRequest.putHeader(headerName, headerValue));
        }

        httpClientRequest.putHeader(HttpHeaders.HOST, api.getProxy().getTarget().getHost());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOGGER.info("Starting HTTP Client for API {}", api);

        initialize(api.getProxy());
    }

    private io.vertx.core.http.HttpMethod convert(io.gravitee.common.http.HttpMethod httpMethod) {
        switch (httpMethod) {
            case CONNECT:
                return HttpMethod.CONNECT;
            case DELETE:
                return HttpMethod.DELETE;
            case GET:
                return HttpMethod.GET;
            case HEAD:
                return HttpMethod.HEAD;
            case OPTIONS:
                return HttpMethod.OPTIONS;
            case PATCH:
                return HttpMethod.PATCH;
            case POST:
                return HttpMethod.POST;
            case PUT:
                return HttpMethod.PUT;
            case TRACE:
                return HttpMethod.TRACE;
        }

        return null;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOGGER.info("Close Vert.x HTTP Client for {}", api);
        httpClient.close();
    }

    private void initialize(Proxy proxyDefinition) {
        Objects.requireNonNull(proxyDefinition, "Proxy must not be null");
        Objects.requireNonNull(proxyDefinition.getTarget(), "Proxy target must not be null");

        LOGGER.info("Initializing Vert.x HTTP Client with {}", proxyDefinition.getHttpClient());

        HttpClientOptions options = new HttpClientOptions();
        options.setKeepAlive(true);
        options.setUsePooledBuffers(true);
        options.setIdleTimeout(10);
        options.setConnectTimeout(5000);
        options.setMaxPoolSize(100);
        options.setDefaultHost(proxyDefinition.getTarget().getHost());
        int port = proxyDefinition.getTarget().getPort();

        if (port != -1) {
            options.setDefaultPort(port);
        }

        httpClient = vertx.createHttpClient(options);
        LOGGER.info("Vert.x HTTP Client created {}", httpClient);
    }
}
