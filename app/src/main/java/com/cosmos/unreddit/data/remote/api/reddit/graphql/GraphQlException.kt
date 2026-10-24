package com.cosmos.unreddit.data.remote.api.reddit.graphql

import java.io.IOException

class GraphQlException(val code: Int, val body: String) : IOException("GraphQL HTTP $code")

class RateLimitException(message: String) : IOException(message)
