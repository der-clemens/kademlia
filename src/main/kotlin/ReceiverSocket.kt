import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext

class ReceiverSocket {
    private val socket = DatagramSocket()
    private val callbacks = ConcurrentHashMap<Long, suspend (Message) -> Unit>()

    init {
        startReceiver()
    }

    private fun startReceiver() {
        thread(start = true) {
            runBlocking {
                while (true) {
                    val data = ByteArray(1024)
                    val packet = DatagramPacket(data, data.size)
                    socket.receive(packet)
                    val (response, response_id) = decode(data) ?: continue
                    val callback = callbacks.remove(response_id) ?: continue
                    try {
                        callback.invoke(response)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun toggleBroadcast() {
        socket.broadcast = !socket.broadcast
    }

    suspend fun send(message: ByteArray, id: Long, target: Node, callback: suspend (Message) -> Unit): Job {
        val scope = CoroutineScope(coroutineContext)
        val sem = Semaphore(1)
        sem.acquire()
        val job = scope.launch {
            sem.acquire()
        }
        job.invokeOnCompletion {
            callbacks.remove(id)
        }
        callbacks[id] = {
            callback(it)
            sem.release()
        }

        try {
            socket.send(DatagramPacket(message, 1024, InetSocketAddress(target.ip, target.port)))
        } catch (_: Exception) {
            Globals.routingTable.evictNode(Node(target.ip, target.port))
        }

        return job
    }

//    suspend fun <T> send(message: ByteArray, id: Long, target: Node, channel: Channel<T>,callback: (Message) -> T): Unit {
//        val scope = CoroutineScope(coroutineContext)
//        val channel = Channel<T>()
//
//        val job = scope.async {
//            channel.receive()
//        }
//        job.invokeOnCompletion {
//            callbacks.remove(id)
//        }
//        callbacks[id] = {
//            val result = callback(it)
//            channel.send(result)
//        }
//
//        socket.send(DatagramPacket(message, 1024, InetSocketAddress(target.ip, target.port)))
//        return job
//    }
}