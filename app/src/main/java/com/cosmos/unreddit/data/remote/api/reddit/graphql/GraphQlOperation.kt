package com.cosmos.unreddit.data.remote.api.reddit.graphql

data class GraphQlOperation(
    val name: String,
    val hash: String,
)

/**
 * Persisted GraphQL operation hashes extracted from Reddit Android 2026.42.0 (versionCode 2642001).
 * Refresh every hash together when targeting a new Reddit build.
 */
object RedditGraphQlOperations {
    val PostComments = GraphQlOperation("PostComments", "314f29c2494fd78c7844ef2c6b09acb4bef5b37ab50b4ddc01691d6d5c8433f8")
    val PostsByIds = GraphQlOperation("PostsByIds", "29a8d4c6e135fffb95fc4cd303c57ad926c6c5ff582f4c4577c8a69b7f322d91")
    val SubredditFeedSdui = GraphQlOperation("SubredditFeedSdui", "fcc9854f03d7829810b2b6f607bae27407d2bbb576b09d8ee59869f80b13ca10")
    val SubredditInfoByName = GraphQlOperation("SubredditInfoByName", "8c1815b07b762aee3c086cc84306f14b3907018e9ee152046c8e4d08ad4c7b88")
    val UserProfileDetails = GraphQlOperation("UserProfileDetails", "4822704f40348a38e37626b62f7f2a4e4af88aa5ab90f271a0c539db7ab2ac2f")
    val SubmittedPostsFeedSdui = GraphQlOperation("SubmittedPostsFeedSdui", "8bcd9296341ba003625abe814251e57437c42f0272d88b166c499f8bebc4cadb")
    val SubmittedComments = GraphQlOperation("SubmittedComments", "e1b37f521fb40af2120946b70e7fbf6a3d924db2abaf28ff2164d4cfacca28db")
    val LiveCommentsByIds = GraphQlOperation("LiveCommentsByIds", "51ab9f2e0eb87b6dac9aac02012ba7046049e516ec55ec442abfed0f19c6f311")
    val SearchPosts = GraphQlOperation("SearchPosts", "08c50e2f9868ea1b8c4411cca57cd58353c729b4fa714cef66448600f76b0922")
    val SearchPeople = GraphQlOperation("SearchPeople", "db3607c1a7deea8441614a053c5f434f60da0c8c931db952f819a59d534d9743")
    val CommunitySearch = GraphQlOperation("CommunitySearch", "ea096984f5a2caf0122cca8f06039c71a0c8ecc99516a7e3782f71b249572133")
}
