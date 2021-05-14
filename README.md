# http-caching-client

![](https://img.shields.io/github/v/release/hmrc/http-caching-client)
[![Apache-2.0 license](http://img.shields.io/badge/license-Apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)



This Play! plugin enables users to read from and write one or more [mongo-caching](https://github.com/hmrc/mongo-caching) microservice instances. Two different types of mongo-caching are available:

* ```SessionCache``` - used for session caching e.g. storing HTTP forms between multiple requests
* ```ShortLivedCache``` - used for short-term caching with encryption

For example, the HMRC Multi Digital Tax Platform has a SessionCache instance called Keystore, and a ShortLivedCache called Save4Later.

## Installation

``` scala
libraryDependencies += "uk.gov.hmrc" %% "http-caching-client" % "[INSERT_VERSION]"
```

## Using a mongo-caching instance

A mongo-caching instance is a cache accessible via REST calls. To identify the single piece of data that is cached three keys are used:

- `source`: The name of the service using the cache
- `cacheId`: A unique identifier for the stored data. Can be the sessionId, userId or any other value.
- `key`: The name of an entry inside the cache

### Using a SessionCache

Implement the client

```scala
    import uk.gov.hmrc.http.cache.client.SessionCache

    object SessionCache extends SessionCache {
        // implement the client
    }
```

Cache the session's data use ```SessionCache#cache```.

Please note that implicit Writes have to be provided in order to serialize the objects into json. For example:

```scala
    val f1 = FormOnPage1("value1", true)
    implicit val formatsF1 = Json.format[FormOnPage1]
    SessionCache.cache[FormOnPage1]("formOnPage1", f1)
```

Note: The call to ```cache``` returns a Future, so the action must be completed before the same cache is read.

When storing multiple objects to the same combination of source and cacheId, (these default to the application name and sessionId), the cache is combined and can be read together with one single REST call.

Read from the full cache use ```SessionCache#fetch```. If no cache is found, None is returned. Once the full cached object is available in the returned CacheMap object, the single cached objects can be retrieved. This will not produce any further REST calls. The method to use is the following:

```scala
    def getEntry[T](key: String)(implicit fjs: Reads[T]): Option[T]
```

For example:

```scala
    val form1 = data.getEntry[FormOnPage1]("formOnPage1")
```

If the `key` is not found in the cache, None is returned

### Using a ShortLivedCache

Implement the client

```scala
    import uk.gov.hmrc.http.cache.client.{ShortLivedCache,ShortLivedHttpCaching}

    object ShortLivedHttpCaching extends ShortLivedHttpCaching {
      // implement the client
    }

    object ShortLivedCache extends ShortLivedCache {
      override implicit lazy val crypto = ApplicationCrypto.JsonCrypto
      override lazy val shortLiveCache = ShortLivedHttpCaching
    }
```

For ```ShortLivedCache``` examples see the above examples for ```SessionCache```, with the only API difference being that the functions require a ```cacheId``` in the method signatures.

A ```ShortLivedCache``` requires JSON encryption, which means a ```json.encryption.key``` configuration property must be present to use encryption and decryption.

When a user session reliant upon a ```ShortLivedCache``` instance ends, a delete command should be issued on the shorted data otherwise it will be cached until the TTL expires.

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
