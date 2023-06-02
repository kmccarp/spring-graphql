/*
 * Copyright 2020-2022 the original author or authors.
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

package org.springframework.graphql.docs.graalvm.client;

import reactor.core.publisher.Mono;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.stereotype.Component;

@Component
@RegisterReflectionForBinding(Project.class) // <2>
public class ProjectService {

	private final GraphQlClient graphQlClient;

	public ProjectService(GraphQlClient graphQlClient) {
		this.graphQlClient = graphQlClient;
	}

	public Mono<Project> project(String projectSlug) {
		String document = """
				query projectWithReleases($projectSlug: ID!) {
					project(slug: $projectSlug) {
						name
						releases {
							version
						}
					}
				}
				""";

		return this.graphQlClient.document(document)
	.variable("projectSlug", projectSlug)
	.retrieve("project")
	.toEntity(Project.class); // <1>
	}
}
