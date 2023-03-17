/*
 * Copyright 2002-2023 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.pagination.ConnectionFieldTypeVisitor;
import org.springframework.graphql.data.query.ScrollPositionCursorStrategy;
import org.springframework.graphql.data.query.ScrollRequest;
import org.springframework.graphql.data.query.WindowConnectionAdapter;
import org.springframework.graphql.execution.ConnectionTypeGenerator;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphQL paginated requests handled through {@code @SchemaMapping} methods.
 *
 * @author Rossen Stoyanchev
 */
public class SchemaMappingPaginationTests {

	private static final String SCHEMA = """
			type Query {
				books(first:Int, after:String): BookConnection
			}
			type Book {
				id: ID
				name: String
			}
			""";


	@Test
	void forwardPagination() throws Exception {

		String document = """
			{
				books(first:2, after:"O_3") {
					edges {
						cursor,
						node {
							id
						name
						}
					}
					pageInfo {
						startCursor,
						endCursor,
						hasPreviousPage,
						hasNextPage
					}
				}
			}
			""";

		ExecutionGraphQlService graphQlService = graphQlService((configurer, setup) -> {

			ConnectionTypeGenerator typeGenerator = new ConnectionTypeGenerator();
			setup.typeDefinitionRegistryConfigurer(typeGenerator::generateConnectionTypes);

			ScrollPositionCursorStrategy cursorStrategy = new ScrollPositionCursorStrategy();
			WindowConnectionAdapter connectionAdapter = new WindowConnectionAdapter(cursorStrategy);
			setup.typeVisitor(ConnectionFieldTypeVisitor.create(List.of(connectionAdapter)));

			configurer.setCursorStrategy(cursorStrategy);
		});

		ExecutionGraphQlResponse response =
				graphQlService.execute(TestExecutionRequest.forDocument(document)).block();

		assertThat(new ObjectMapper().writeValueAsString(response.getData()))
				.as("Errors: " + response.getErrors()).isEqualTo(
						"{\"books\":{" +
								"\"edges\":[" +
								"{\"cursor\":\"O_0\",\"node\":{\"id\":\"4\",\"name\":\"To The Lighthouse\"}}," +
								"{\"cursor\":\"O_1\",\"node\":{\"id\":\"5\",\"name\":\"Animal Farm\"}}" +
								"]," +
								"\"pageInfo\":{" +
								"\"startCursor\":\"O_0\"," +
								"\"endCursor\":\"O_1\"," +
								"\"hasPreviousPage\":false," +
								"\"hasNextPage\":false" +
								"}}}");
	}

	private ExecutionGraphQlService graphQlService(BiConsumer<AnnotatedControllerConfigurer, GraphQlSetup> consumer) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BookController.class);
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(context);

		GraphQlSetup setup = GraphQlSetup.schemaContent(this.SCHEMA).runtimeWiring(configurer);
		consumer.accept(configurer, setup);

		configurer.afterPropertiesSet();

		return setup.toGraphQlService();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BookController {

		@QueryMapping
		public Window<Book> books(ScrollRequest request) {
			int offset = (int) ((OffsetScrollPosition) request.position().get()).getOffset();
			int count = request.count().get();
			List<Book> books = BookSource.books().subList(offset, offset + count);
			return Window.from(books, OffsetScrollPosition::of);
		}

	}

}