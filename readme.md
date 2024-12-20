[![](https://jitpack.io/v/MarcinMoskala/kotlin-coroutines-recipes.svg)](https://jitpack.io/#MarcinMoskala/kotlin-coroutines-recipes)

# Kotlin Coroutines Recipes

This repository contains Kotlin Coroutines functions that are useful in everyday projects. Feel free to use them in your projects, both as a dependency, or by copy-pasting my code. Also feel free to share your own functions, that you find useful, but remamber that they need to be properly tested.

## Dependency

[![](https://jitpack.io/v/MarcinMoskala/kotlin-coroutines-recipes.svg)](https://jitpack.io/#MarcinMoskala/kotlin-coroutines-recipes)

Add the dependency in your module `build.gradle(.kts)`:

```
// build.gradle / build.gradle.kts
dependencies {
    implementation("com.github.MarcinMoskala.kotlin-coroutines-recipes:kotlin-coroutines-recipes:<version>")
}
```

Add it in your root `build.gradle(.kts)` at the repositories block:

```
// build.gradle
repositories {
    // ...
    maven { url 'https://jitpack.io' }
}

// build.gradle.kts
repositories {
    // ...
    maven("https://jitpack.io")
}
```

This library can currently be used on Kotlin/JVM, Kotlin/JS and in common modules

## Recipes

### `mapAsync`

Function `mapAsync` allows you to concurrently map a collection of elements to a collection of results of suspending functions. It is useful when you want to run multiple suspending functions in parallel. You can limit the number of concurrent operations by passing `concurrency` parameter.

```kotlin
suspend fun getCourses(requestAuth: RequestAuth?, user: User): List<UserCourse> =
    courseRepository.getAllCourses()
        .mapAsync { composeUserCourse(user, it) }
        .filterNot { courseShouldBeHidden(user, it) }
        .sortedBy { it.state.ordinal }
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/mapAsync.kt).

### `retryWhen`

Function `retryWhen` allows you to retry an operation when a given predicate is true. It is useful when you want to retry an operation multiple times under certain conditions.

```kotlin
// Example
suspend fun checkConnection(): Boolean = retryWhen(
    predicate = { _, retries -> retries < 3 },
    operation = { api.connected() }
)
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/retryWhen.kt).

### `retryBackoff`

Function `retryBackoff` implements exponential backoff algorithm for retrying an operation. It is useful when you want to retry an operation multiple times with increasing delay between retries.

```kotlin
fun observeUserUpdates(): Flow<User> = api
    .observeUserUpdates()
    .retryBackoff(
        minDelay = 1.seconds,
        maxDelay = 1.minutes, // optional
        maxAttempts = 30, // optional
        backoffFactor = 2.0, // optional
        jitterFactor = 0.1, // optional
        beforeRetry = { cause, _, _ -> // optional
            println("Retrying after $cause")
        },
        retriesExhausted = { cause -> // optional
            println("Retries exhausted after $cause")
        },
    )

suspend fun fetchUser(): User = retryBackoff(
    minDelay = 1.seconds,
    maxDelay = 10.seconds, // optional
    maxAttempts = 10, // optional
    backoffFactor = 1.5, // optional
    jitterFactor = 0.5, // optional
    beforeRetry = { cause, _, -> // optional
        println("Retrying after $cause")
    },
    retriesExhausted = { cause -> // optional
        println("Retries exhausted after $cause")
    },
) {
    api.fetchUser()
}
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/RetryBackoff.kt).

### `raceOf`

Function `raceOf` allows you to run multiple suspending functions in parallel and return the result of the first one that finishes. 

```kotlin
suspend fun fetchUserData(): UserData = raceOf(
    { service1.fetchUserData() },
    { service2.fetchUserData() }
)
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/raceOf.kt).

## `suspendLazy`

Function `suspendLazy` allows you to create a function that represents a lazy suspending property. It is useful when you want to create a suspending property that is initialized only once, but you don't want to block the thread that is accessing it.

```kotlin
val userData: suspend () -> UserData = suspendLazy {
    service.fetchUserData()
}

suspend fun getUserData(): UserData = userData()
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/suspendLazy.kt).

## `SharedDataSource`

Class `SharedDataSource` allows you to reuse the same flow data sources created based on a key. It makes sure there is no more than a single active shared flow for a given key. It is useful when you want to reuse the same shared flow for multiple subscribers.

```kotlin
val sharedDataSource = SharedDataSource<String, Flow<String>>(scope) { userId ->
    observeMessages(userId)
}

fun observeMessages(userId: String): Flow<String> = sharedDataSource.get(userId)
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/SharedDataSource.kt).

## `StateDataSource`

Class `StateDataSource` allows you to reuse the same state flow created based on a key. It makes sure there is no more than a single active state flow for a given key. It is useful when you want to reuse the same state flow for multiple subscribers.

```kotlin
val usersState = StateDataSource<String, Flow<String>>(scope) { userId ->
    observeUserState(userId)
}

fun observeUserState(userId: String): Flow<User> = usersState.get(userId)
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/StateDataSource.kt).

## `enqueueWithDelay`

Function `enqueueWithDelay` transforms flow into a queue, where there is a given wait-`period` between two consecutive emits from original flow. Useful for displaying fast-changing info to the user, like progress state.

```kotlin
val fastProgress = fastProgressFlow()
    .enqueueWithDelay(period = 1.seconds)
    .map(::toSomeUiState)

fun fastProgressFlow(): Flow<Int> = flow<Int> { repeat(10) { emit(it) } }
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/enqueueWithDelay.kt).
