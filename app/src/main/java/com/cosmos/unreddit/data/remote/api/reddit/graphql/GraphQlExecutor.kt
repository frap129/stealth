package com.cosmos.unreddit.data.remote.api.reddit.graphql

interface GraphQlExecutor {
    suspend fun execute(operation: GraphQlOperation, variables: Map<String, Any?>): String
}
