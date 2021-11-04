/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.graphql.web.webflux;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.GraphQlTestUtils;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlHttpHandler}.
 * @author Rossen Stoyanchev
 */
public class GraphQlHttpHandlerTests {

	@Test
	void locale() {
		GraphQlHttpHandler handler = createHttpHandler(
				"type Query { greeting: String }", "Query", "greeting",
				(env) -> "Hello in " + env.getLocale());

		MockServerHttpRequest httpRequest =
				MockServerHttpRequest.post("/").acceptLanguageAsLocales(Locale.FRENCH).build();

		MockServerHttpResponse httpResponse = handleRequest(
				httpRequest, handler, Collections.singletonMap("query", "{greeting}"));

		assertThat(httpResponse.getBodyAsString().block())
				.isEqualTo("{\"data\":{\"greeting\":\"Hello in fr\"}}");
	}

	private GraphQlHttpHandler createHttpHandler(
			String schemaContent, String type, String field, DataFetcher<Object> dataFetcher) {

		GraphQlSource source = GraphQlTestUtils.graphQlSource(schemaContent, type, field, dataFetcher).build();
		GraphQlService service = new ExecutionGraphQlService(source);
		return new GraphQlHttpHandler(WebGraphQlHandler.builder(service).build());
	}

	private MockServerHttpResponse handleRequest(
			MockServerHttpRequest httpRequest, GraphQlHttpHandler handler, Map<String, String> body) {

		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);

		MockServerRequest serverRequest = MockServerRequest.builder()
				.exchange(exchange)
				.uri(((ServerWebExchange) exchange).getRequest().getURI())
				.method(((ServerWebExchange) exchange).getRequest().getMethod())
				.headers(((ServerWebExchange) exchange).getRequest().getHeaders())
				.body(Mono.just((Object) body));

		handler.handleRequest(serverRequest)
				.flatMap(response -> response.writeTo(exchange, new DefaultContext()))
				.block();

		return exchange.getResponse();
	}


	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return Collections.singletonList(new EncoderHttpMessageWriter<>(new Jackson2JsonEncoder()));
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return Collections.emptyList();
		}

	}

}