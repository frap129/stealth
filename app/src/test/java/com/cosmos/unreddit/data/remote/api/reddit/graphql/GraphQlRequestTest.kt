package com.cosmos.unreddit.data.remote.api.reddit.graphql

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphQlRequestTest {
    @Test
    fun `serializes a persisted query request`() {
        val inputOperation = RedditGraphQlOperations.PostComments
        val inputVariables = mapOf<String, Any?>("id" to "t3_1wzvqsb")
        val request = GraphQlRequest.from(inputOperation, inputVariables)
        val actualJson = Moshi.Builder().build().adapter(GraphQlRequest::class.java).toJson(request)
        val expectedJson =
            """{"operationName":"PostComments","variables":{"id":"t3_1wzvqsb"},"extensions":{"persistedQuery":{"version":1,"sha256Hash":"314f29c2494fd78c7844ef2c6b09acb4bef5b37ab50b4ddc01691d6d5c8433f8"}}}"""
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `omits null variables during serialization`() {
        val inputOperation: GraphQlOperation = RedditGraphQlOperations.PostComments
        val inputVariables: Map<String, Any?> = mapOf("after" to null, "id" to "t3_1")
        val request: GraphQlRequest = GraphQlRequest.from(inputOperation, inputVariables)
        val actualJson: String = Moshi.Builder().build().adapter(GraphQlRequest::class.java).toJson(request)
        val expectedVariablesJson: String = """"variables":{"id":"t3_1"}"""
        assertTrue(actualJson.contains(expectedVariablesJson))
    }

    @Test
    fun `contains every operation hash used by the migration`() {
        assertEquals("314f29c2494fd78c7844ef2c6b09acb4bef5b37ab50b4ddc01691d6d5c8433f8", RedditGraphQlOperations.PostComments.hash)
        assertEquals("29a8d4c6e135fffb95fc4cd303c57ad926c6c5ff582f4c4577c8a69b7f322d91", RedditGraphQlOperations.PostsByIds.hash)
        assertEquals("fcc9854f03d7829810b2b6f607bae27407d2bbb576b09d8ee59869f80b13ca10", RedditGraphQlOperations.SubredditFeedSdui.hash)
        assertEquals("5abe205dd071054dec10310876ca8ca1034f5a09a344659cc7e641ced2e799c2", RedditGraphQlOperations.PopularFeedSdui.hash)
        assertEquals("8c1815b07b762aee3c086cc84306f14b3907018e9ee152046c8e4d08ad4c7b88", RedditGraphQlOperations.SubredditInfoByName.hash)
        assertEquals("4822704f40348a38e37626b62f7f2a4e4af88aa5ab90f271a0c539db7ab2ac2f", RedditGraphQlOperations.UserProfileDetails.hash)
        assertEquals("8bcd9296341ba003625abe814251e57437c42f0272d88b166c499f8bebc4cadb", RedditGraphQlOperations.SubmittedPostsFeedSdui.hash)
        assertEquals("e1b37f521fb40af2120946b70e7fbf6a3d924db2abaf28ff2164d4cfacca28db", RedditGraphQlOperations.SubmittedComments.hash)
        assertEquals("51ab9f2e0eb87b6dac9aac02012ba7046049e516ec55ec442abfed0f19c6f311", RedditGraphQlOperations.LiveCommentsByIds.hash)
        assertEquals("db3607c1a7deea8441614a053c5f434f60da0c8c931db952f819a59d534d9743", RedditGraphQlOperations.SearchPeople.hash)
    }
}
