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

package org.springframework.graphql.test.tester;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.ExecutionResult;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link WebGraphQlTester}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultWebGraphQlTester extends DefaultGraphQlTester implements WebGraphQlTester {


	DefaultWebGraphQlTester(WebTestClient client) {
		super(new WebTestClientRequestStrategy(client, initJsonPathConfig()));
	}

	DefaultWebGraphQlTester(WebGraphQlHandler handler) {
		super(new WebGraphQlHandlerRequestStrategy(handler, initJsonPathConfig()));
	}


	/**
	 * {@link RequestStrategy} that works as an HTTP client with requests executed through
	 * {@link WebTestClient} that in turn may work connect with or without a live server
	 * for Spring MVC and WebFlux.
	 */
	private static class WebTestClientRequestStrategy implements RequestStrategy {

		private final WebTestClient client;

		private final Configuration jsonPathConfig;

		WebTestClientRequestStrategy(WebTestClient client, Configuration jsonPathConfig) {
			this.client = client;
			this.jsonPathConfig = jsonPathConfig;
		}

		@Override
		public ResponseSpec execute(RequestInput requestInput) {
			EntityExchangeResult<byte[]> result = this.client.post().contentType(MediaType.APPLICATION_JSON)
					.bodyValue(requestInput).exchange().expectStatus().isOk().expectHeader()
					.contentType(MediaType.APPLICATION_JSON).expectBody().returnResult();

			byte[] bytes = result.getResponseBodyContent();
			Assert.notNull(bytes, "Expected GraphQL response content");
			String content = new String(bytes, StandardCharsets.UTF_8);
			DocumentContext documentContext = JsonPath.parse(content, this.jsonPathConfig);

			return new DefaultResponseSpec(documentContext, result::assertWithDiagnostics);
		}

		@Override
		public SubscriptionSpec executeSubscription(RequestInput requestInput) {
			FluxExchangeResult<TestExecutionResult> exchangeResult = this.client.post()
					.contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).bodyValue(requestInput)
					.exchange().expectStatus().isOk().expectHeader().contentType(MediaType.TEXT_EVENT_STREAM)
					.returnResult(TestExecutionResult.class);

			return new DefaultSubscriptionSpec(exchangeResult.getResponseBody().cast(ExecutionResult.class),
					this.jsonPathConfig, exchangeResult::assertWithDiagnostics);
		}

	}

	/**
	 * {@link RequestStrategy} that performs requests directly on
	 * {@link WebGraphQlHandler}, i.e. Web request testing without a transport.
	 */
	private static class WebGraphQlHandlerRequestStrategy extends AbstractDirectRequestStrategy {

		private static final URI DEFAULT_URL = URI.create("http://localhost:8080/graphql");

		private static final HttpHeaders DEFAULT_HEADERS = new HttpHeaders();

		private final WebGraphQlHandler graphQlHandler;

		WebGraphQlHandlerRequestStrategy(WebGraphQlHandler handler, Configuration jsonPathConfig) {
			super(jsonPathConfig);
			this.graphQlHandler = handler;
		}

		protected ExecutionResult executeInternal(RequestInput input) {
			WebInput webInput = new WebInput(DEFAULT_URL, DEFAULT_HEADERS, input.toMap(), null);
			ExecutionResult result = this.graphQlHandler.handle(webInput).block(DEFAULT_TIMEOUT);
			Assert.notNull(result, "Expected ExecutionResult");
			return result;
		}
	}

}