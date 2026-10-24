package com.cosmos.unreddit.ui.postdetails

import com.cosmos.unreddit.data.model.Comment
import com.cosmos.unreddit.data.model.Comment.CommentEntity

internal object CommentHierarchy {
    fun restore(comments: List<Comment>, depth: Int): List<Comment> {
        val restored = mutableListOf<Comment>()

        for (i in comments.indices) {
            val comment = comments[i]

            if (comment.depth > depth) {
                continue
            } else if (comment.depth < depth) {
                break
            }

            val nextComment = comments.getOrNull(i + 1)

            if (comment is CommentEntity && nextComment != null && nextComment.depth > depth) {
                comment.replies.addAll(restore(comments.subList(i + 1, comments.size), depth + 1))
                comment.visibleReplyCount = comment.replies.size
            }

            restored.add(comment)
        }

        return restored
    }
}
