package ru.netology.coroutines.dto

data class PostWithAuthorComments(
        val post: Post,
        val author: Author,
        val comments: List<CommentWithAuthor>,
)
