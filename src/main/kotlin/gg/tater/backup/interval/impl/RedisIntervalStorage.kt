package gg.tater.backup.interval.impl

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.SerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.unsafe.UnsafeInput
import com.esotericsoftware.kryo.unsafe.UnsafeOutput
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import com.esotericsoftware.kryo.util.Pool
import gg.tater.backup.config.ApplicationConfig
import gg.tater.backup.interval.IntervalStorageDao
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.channel.nio.NioEventLoopGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.objenesis.strategy.StdInstantiatorStrategy
import org.redisson.Redisson
import org.redisson.api.RFuture
import org.redisson.api.RMap
import org.redisson.api.RedissonClient
import org.redisson.client.codec.Codec
import org.redisson.client.protocol.Decoder
import org.redisson.client.protocol.Encoder
import org.redisson.config.Config
import org.redisson.config.TransportMode
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val REDIS_MAP_NAME = "backup_map"

class RedisBackupStorage(appConfig: ApplicationConfig) : IntervalStorageDao {

    private val client: RedissonClient

    init {
        this.client = Redisson.create(Config().apply {
            useSingleServer().apply {
                address = "redis://${appConfig.redisHost}:${appConfig.redisPort}"
                password = appConfig.redisPassword

                transportMode = TransportMode.NIO
                eventLoopGroup = NioEventLoopGroup(5)
                nettyThreads = 5
                executor = Executors.newCachedThreadPool()

                codec = kryoCodec(kryoPool {
                    setDefaultSerializer(SerializerFactory.FieldSerializerFactory().apply {
                        config.fieldsCanBeNull = true
                        config.serializeTransient = true
                        config.setFieldsAsAccessible(true)
                    })
                    isRegistrationRequired = false
                    instantiatorStrategy = DefaultInstantiatorStrategy(StdInstantiatorStrategy())
                    serialize(
                        { writeLong(it.mostSignificantBits); writeLong(it.leastSignificantBits) },
                        { UUID(readLong(), readLong()) }
                    )
                })
            }
        })
    }

    override suspend fun setLastBackup(input: String): Any = runBlocking (Dispatchers.Default) {
        val map: RMap<String, Instant> = client.getMap(REDIS_MAP_NAME)
        map.fastPutSuspend(input, Instant.now())
    }

    override suspend fun getLastBackup(input: String): Instant = runBlocking (Dispatchers.Default){
        val map: RMap<String, Instant> = client.getMap(REDIS_MAP_NAME)
        map.getAsync(input).awaitSuspend()
    }
}

private fun kryoPool(block: Kryo.() -> (Unit)) = object : Pool<Kryo>(true, true) {
    override fun create() = Kryo().apply(block)
}

fun kryoCodec(pool: Pool<Kryo>) = object : Codec {
    val decoder = Decoder { buffer, _ ->
        val kryo = pool.obtain()
        val read = kryo.run {
            UnsafeInput(ByteBufInputStream(buffer)).use {
                readClassAndObject(it)
            }
        }
        pool.free(kryo)
        read
    }
    val encoder = Encoder {
        pool.obtain().run {
            val buffer = ByteBufAllocator.DEFAULT.buffer()
            try {
                UnsafeOutput(ByteBufOutputStream(buffer)).use { out ->
                    writeClassAndObject(out, it)
                    buffer
                }
            } catch (exception: Exception) {
                buffer.release()
                throw RuntimeException(exception)
            } finally {
                pool.free(this)
            }
        }
    }

    override fun getMapValueDecoder() = decoder
    override fun getMapValueEncoder() = encoder
    override fun getMapKeyDecoder() = decoder
    override fun getMapKeyEncoder() = encoder
    override fun getValueDecoder() = decoder
    override fun getValueEncoder() = encoder
    override fun getClassLoader() = null
}

inline fun <reified T : Any> Kryo.serialize(
    crossinline to: Output.(T) -> (Unit),
    crossinline from: Input.() -> (T)
) {
    register(T::class.java, object : Serializer<T>() {
        override fun write(kryo: Kryo, output: Output, obj: T) = output.to(obj)
        override fun read(kryo: Kryo, input: Input, type: Class<out T>?) = input.from()
    })
}

suspend fun <Key, Value> RMap<Key, Value>.getSuspend(key: Key): Value? =
    getAsync(key).awaitSuspend()

suspend fun <Type> RFuture<Type>.awaitSuspend() = suspendCoroutine<Type> {
    handle { result, reason ->
        if (reason == null) it.resume(result)
        else it.resumeWithException(reason)
    }
}

suspend fun <Key, Value> RMap<Key, Value>.fastPutSuspend(key: Key, value: Value) =
    fastPutAsync(key, value).awaitSuspend()
