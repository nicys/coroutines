package ru.netology.coroutines.dto

data class AuthorWithPosts(
        val author: Author,
        val posts: List<Post>,
)
