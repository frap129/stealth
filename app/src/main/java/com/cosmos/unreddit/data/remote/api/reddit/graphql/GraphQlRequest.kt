package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GraphQlRequest(
    val operationName: String,
    /**
     * Null values are omitted by Moshi's default serialization. The server treats omitted and null
     * optional variables alike.
     */
    val variables: Map<String, Any?>,
    val extensions: GraphQlExtensions,
) {
    companion object {
        fun from(operation: GraphQlOperation, variables: Map<String, Any?>): GraphQlRequest =
            GraphQlRequest(
                operationName = operation.name,
                variables = variables,
                extensions = GraphQlExtensions(PersistedQuery(version = 1, sha256Hash = operation.hash)),
            )
    }
}

@JsonClass(generateAdapter = true)
data class GraphQlExtensions(
    val persistedQuery: PersistedQuery,
)

@JsonClass(generateAdapter = true)
data class PersistedQuery(
    val version: Int,
    val sha256Hash: String,
)
