package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import ru.netology.coroutines.dto.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


private val gson = Gson()
private val BASE_URL = "http://176.196.11.226:9999"
private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor(::println).apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)
                        .map { post ->
                            async {
                                val author = getAuthor(client, post.authorId)
                                val comments = getComments(client, post.id)
                                        .map { comment ->
                                            async {
                                                CommentWithAuthor(comment, getAuthor(client, comment.authorId))
                                            }
                                        }.awaitAll()
                                PostWithAuthorComments(
                                        post, author, comments)
                            }
                        }.awaitAll()
                println(posts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
                .url(url)
                .build()
                .let(::newCall)
                .enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }
                })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
        withContext(Dispatchers.IO) {
            client.apiCall(url)
                    .let { response ->
                        if (!response.isSuccessful) {
                            response.close()
                            throw RuntimeException(response.message)
                        }
                        val body = response.body ?: throw RuntimeException("response body is null")
                        gson.fromJson(body.string(), typeToken.type)
                    }
        }

suspend fun getAuthor(client: OkHttpClient, id: Long): Author =
        makeRequest("$BASE_URL/api/slow/authors/$id", client, object : TypeToken<Author>() {})

suspend fun getPosts(client: OkHttpClient): List<Post> =
        makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
        makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})






//1
// fun main() = runBlocking {
//    val job = CoroutineScope(EmptyCoroutineContext).launch {
//        launch {
//            delay(500)
//            println("ok") // <--
//        }
//        launch {
//            delay(500)
//            println("ok2")
//        }
//    }
//    delay(100)
//    job.cancelAndJoin()
//    println("активен - ${job.isActive}")
//    println("завершен - ${job.isCompleted}")
//    println("отменен - ${job.isCancelled}")
////
//////    Ответ: нет, поскольку из-за делая в корутинах они успевают отмениться до создания (благодаря join.cancelAndJoin() происходит их корректное завершение).
//}


//2
// fun main() = runBlocking {
//    val job = CoroutineScope(EmptyCoroutineContext).launch {
//        val child = launch {
//            delay(500)
//            println("ok1") // <--
//        }
//        launch {
//            delay(500)
//            println("ok2")
//            println("дет2.активен - ${child.isActive}")
//            println("дет2.завершен - ${child.isCompleted}")
//            println("дет2.отменен - ${child.isCancelled}")
//        }
////        delay(100)
//        child.cancel()
//        println("дет.активен - ${child.isActive}")
//        println("дет.завершен - ${child.isCompleted}")
//        println("дет.отменен - ${child.isCancelled}")
//    }
//    delay(100)
//    job.join()
//    println("род.активен - ${job.isActive}")
//    println("род.завершен - ${job.isCompleted}")
//    println("род.отменен - ${job.isCancelled}")
//
//
//    //    Ответ: Здесь все хорошо, - родительский поток создатся, отработает и спокойно завершится.
////    А чилдрен из-за делая раньше был отменен, поэтому и не будет создан.
//}
//
//fun main() = runBlocking {
//    val job = CoroutineScope(EmptyCoroutineContext).launch {
//        val child = launch {
//            delay(500)
//            println("ok1") // <--
//        }
//        launch {
//            delay(500)
//            println("ok2")
//        }
//        delay(100)
//        child.cancel()
//    }
//    delay(100)
//    job.join()
//}



////3
//fun main() {
//    with(CoroutineScope(EmptyCoroutineContext)) {
//        try {
//            launch {
//                throw Exception("something bad happened")
//            }
//        } catch (e: Exception) {
//            e.printStackTrace() // <--
//        }
//    }
//    Thread.sleep(1000)
//}
////    Ответ: Да, все норм, main поток спит (кстати использовать Thread.sleep() небезопасно, поскольку поток прерывается) и корутиа
//// успевает запустится. В трай и кэтч корутина обернута правильно.

//4
//fun main() {
//    val d = CoroutineScope(EmptyCoroutineContext).launch {
//        try {
//            coroutineScope {
//                throw Exception("something bad happened")
//            }
//        } catch (e: Exception) {
//            e.printStackTrace() // <--
//        }
//
//    }
//    Thread.sleep(1000)
//    println(d)
//}

////    Ответ: Да, все норм, супеет выполнится, - main поток спит 1 сек.


//5
//fun main() {
//    val d = CoroutineScope(EmptyCoroutineContext).launch {
//        try {
////            supervisorScope {
//                throw Exception("something bad happened")
////            }
//        } catch (e: Exception) {
//            e.printStackTrace() // <--
//        }
//    }
//    println(d)
//    Thread.sleep(1000)
//    println(d)
//}

//fun main() {
//    val d = CoroutineScope(EmptyCoroutineContext).launch {
//        try {
//            supervisorScope {
//                launch {
//                    delay(500)
//                    throw Exception("something bad happened1") // <--
//                }
//                launch {
//                    throw Exception("something bad happened2")
//                }
//            }
//        } catch (e: Exception) {
//            e.printStackTrace() // <--
//        }
//    }
//    println(d)
//    Thread.sleep(1000)
//    println(d)
//}


//Ответ: ?


//6
//fun main() {
//    CoroutineScope(EmptyCoroutineContext).launch {
//        try {
//            val d = coroutineScope {
//                launch {
////                    delay(500)
//                    throw Exception("something bad happened-1") // <--
//                }
//                launch {
//                    throw Exception("something bad happened-2")
//                }
//            }
//            println(d)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//    Thread.sleep(1000)
//}
////Ответ: Нет, из-за 0,5 задержки корутина закончит свое выполнение


//7
//fun main() {
//    CoroutineScope(EmptyCoroutineContext).launch {
//        try {
//            supervisorScope {
//                launch {
//                    delay(500)
//                    throw Exception("something bad happened-1") // <--
//                }
//                launch {
//                    throw Exception("something bad happened-2")
//                }
//            }
//        } catch (e: Exception) {
//            e.printStackTrace() // <--
//        }
//    }
//    Thread.sleep(1000)
//}
//Ответ: Да (обе строки будут выполенны), Делай меньше слипа, а supervisorScope позволяет грамотно завершить все корутины.




////8
//fun main() {
//    val d = CoroutineScope(EmptyCoroutineContext).launch {
//        val f = CoroutineScope(EmptyCoroutineContext).launch {
//            launch {
//                delay(1000)
//                println("ok-1") // <--
//            }
//            launch {
//                delay(500)
//                println("ok-2")
//            }
//            throw Exception("something bad happened")
//        }
//        println("f $f")
//    }
//    println(d)
//    Thread.sleep(1000)
//    println(d)
//}
//Ответ: Ну тут ситуация понятна: внутри родительнской корутины 2 дочки с делаями.
// Поэтому, выполняется только родительский поток.


////9
//fun main() {
//    val d = CoroutineScope(EmptyCoroutineContext + SupervisorJob()).launch {
//        val f = CoroutineScope(EmptyCoroutineContext + SupervisorJob()).launch {
//            launch {
//                delay(1000)
//                println("ok-1") // <--
////                throw Exception("something bad happened")
//            }
//            launch {
//                delay(500)
//                println("ok-2")
//            }
//            throw Exception("something bad happened")
//        }
//        println("f $f")
//    }
//    println(d)
//    Thread.sleep(2000)
//    println(d)
//}
//
////Ответ: ?

//*/
/*
private val gson = Gson()
private val BASE_URL = "http://176.196.11.226:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        async {
                            PostWithComments(post, getComments(client, post.id))
                        }
                    }.awaitAll()
                println(posts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})
*/
