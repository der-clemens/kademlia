import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.thread
import kotlinx.coroutines.sync.Semaphore
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class Client {
    private val socket = ReceiverSocket()

    fun join(node: Node) = runBlocking {
        val (msg, send_id) = encode(MessageType.PING)
        val channel = Channel<Boolean>()

        val job = socket.send(msg, send_id, node) {
            if(it.type != MessageType.PONG)
                channel.send(false)
            Globals.routingTable.addNode(Node(it.ip, it.port))
            channel.send(true)
        }

        val success = withTimeoutOrNull(1000) {channel.receive()}
        if (success == null) {
            println("Could not connect to network. Please try again")
            job.cancel()
            return@runBlocking
        }
        if(!success) {
            println("Error: Received invalid response")
            return@runBlocking
        }
        println("Connected successfully")
        //TODO Grab routing of known node by querying own key
    }

    fun store(value: String) = runBlocking {
        val nodes = recursiveNodeLookup(Key(value))

        val (msg, send_id) = encode(MessageType.STORE, value)



        nodes.map {
            socket.send(msg, send_id, it) {
                //Setup TCP connection
            }
        }.forEach {
            it.cancel() //We dont need the callbacks
        }
    }

    fun get(key: Key) = runBlocking {
        val result = recursiveValueLookup(key)
        if(result == null) {
            println("Could not locate file")
        }
        println("Found: $result")
    }

    private fun recursiveNodeLookup(key: Key): List<Node> = runBlocking {
        var lastClosest = Globals.routingTable.findClosest(key)

        while (true) {
            //Send FIND_NODE to closest not yet contacted node, wait for 1s if we get a reply and if not contact the next one and so on
            //If ANY of the contacted nodes reply, ALL currently outstanding replies are cancelled, and we continue working with the one response we got
            val channel = Channel<List<Node>>(1)
            try {
                coroutineScope {
                    val scope = this
                    for (closest in lastClosest) {
                        val (msg, send_id) = encode(MessageType.FIND_NODE, key)
                        val job = socket.send(msg, send_id, closest) {
                            if (it.type != MessageType.REPLY_FIND_NODE)
                                return@send
                            channel.send(it.content as List<Node>)
                            scope.cancel()
                        }
                        withTimeoutOrNull(1000) {
                            job.join()
                        }
                    }
                    channel.send(emptyList())
                    scope.cancel()
                }
            } catch (_: CancellationException) { }

            //There should always be a result available
            val nodes = channel.receive()
            //All nodes failed to respond in time
            if(nodes == emptyList<Node>()) {
                println("Failed")
                return@runBlocking emptyList()
            }

            //Merge new nodes with previous iteration ones
            val mergedNodes = (lastClosest + nodes).distinctBy { Pair(it.ip, it.port) }
                .sortedBy {
                    BigInteger(it.key.xor(key).toBytes()).abs()
                }.take(Globals.k)

            //Failed to return a new  closest node
            if(mergedNodes[0] == lastClosest[0]) {
                lastClosest = mergedNodes
                break
            }
            lastClosest = mergedNodes
        }
        return@runBlocking lastClosest
    }

    private fun recursiveValueLookup(key: Key): String? = runBlocking {
        var lastClosest = Globals.routingTable.findClosest(key)

        var result: String? = null

        while (true) {
            //Send FIND_NODE to closest not yet contacted node, wait for 1s if we get a reply and if not contact the next one and so on
            //If ANY of the contacted nodes reply, ALL currently outstanding replies are cancelled, and we continue working with the one response we got
            val channel = Channel<List<Node>>(Globals.k)
            try {
                coroutineScope {
                    val scope = this
                    for(closest in lastClosest) {
                        val (msg, send_id) = encode(MessageType.FIND_VALUE, key)
                        val job = socket.send(msg, send_id, closest) {
                            if(it.type == MessageType.REPLY_FIND_VALUE) {
                                result = it.content as String
                                scope.cancel()
                                return@send
                            }
                            if (it.type == MessageType.REPLY_FIND_NODE) {
                                channel.send(it.content as List<Node>)
                            }
                        }
                        withTimeoutOrNull(1000) {
                            job.join()
                        }
                    }
                    channel.send(emptyList())
                    scope.cancel()
                }
            } catch (_: CancellationException) {}


            if(result != null)
                return@runBlocking result

            //There should always be a result available
            val nodes = channel.receive()
            //All nodes failed to respond in time
            if(nodes == emptyList<Node>()) {
                println("Failed")
                return@runBlocking null
            }

            //Merge new nodes with previous iteration ones
            val mergedNodes = (lastClosest + nodes).distinctBy { Pair(it.ip, it.port) }
                .sortedBy {
                    BigInteger(it.key.xor(key).toBytes()).abs()
                }.take(Globals.k)

            //Failed to return a new  closest node
            if(mergedNodes[0] == lastClosest[0]) {
                lastClosest = mergedNodes
                break
            }
            lastClosest = mergedNodes
        }
        return@runBlocking null
    }
//
//    private fun recursiveLookup(key: Key) = runBlocking {
//
//        var lastClosest = Globals.routingTable.findClosest(key)
//
//        var newClosest: List<Node>? = null
//        //Send FIND_NODE to closest not yet contacted node, wait for 1s if we get a reply and if not contact the next one and so on
//        //If ANY of the contacted nodes reply, ALL currently outstanding replies are cancelled, and we continue working with the one response we got
//        coroutineScope {
//            val scope = this
//            for(closest in lastClosest) {
//                val (msg, send_id) = encode(MessageType.FIND_NODE, key)
//                val job = send(msg, send_id, closest, this) {
//                    if (it.type != MessageType.REPLY_FIND_NODE)
//                        return@send
//                    newClosest = it.content as List<Node>
//                    scope.cancel()
//                }
//
//                try {
//                    withTimeout(1000) {
//                        job.await()
//                    }
//                } catch (_: CancellationException) { }
//            }
//        }
//
//        //All nodes failed to respond in time
//        if(newClosest == null) {
//            return@runBlocking
//        }
//
//        //Diff new nodes with previous iteration ones
//        val mergedNodes = (lastClosest + (newClosest as List<Node>)).sortedBy {
//            BigInteger(it.key.xor(key).toBytes()).abs()
//        }.take(Globals.k)
//
//        //Failed to return a node closer
//        if(mergedNodes[0] == lastClosest[0]) {
//
//        }
//
//    }
//
//    private suspend fun staggeredSend(nodes: List<Node>) {
//        var newClosest: List<Node>? = null
//        coroutineScope {
//            val scope = this
//            for (closest in nodes) {
//                val (msg, send_id) = encode(MessageType.FIND_NODE, key)
//                val job = send(msg, send_id, closest, this) {
//                    if (it.type != MessageType.REPLY_FIND_NODE)
//                        return@send
//                    newClosest = it.content as List<Node>
//                    scope.cancel()
//                }
//                try {
//                    withTimeout(1000) {
//                        job.await()
//                    }
//                } catch (_: CancellationException) { }
//            }
//            return@coroutineScope
//        }
//        return newClosest
//    }
//
//    private suspend fun send(message: ByteArray, id: Long, target: Node, scope: CoroutineScope, callback: (Message) -> Unit): Deferred<Unit> {
//        val sem = Semaphore(1)
//        sem.acquire()
//        val job = scope.async {
//            sem.acquire()
//        }
//        job.invokeOnCompletion {
//            callbacks.remove(id)
//        }
//        callbacks[id] = {
//            callback(it)
//            sem.release()
//        }
//        socket.send(DatagramPacket(message, 1024, InetSocketAddress(target.ip, target.port)))
//        return job
//    }
}

