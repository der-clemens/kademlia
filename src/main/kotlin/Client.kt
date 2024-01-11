import kotlinx.coroutines.*
import kotlin.concurrent.thread
import kotlinx.coroutines.channels.Channel
import java.math.BigInteger
import java.net.*
import java.util.*


class Client {
    private val socket = ReceiverSocket()

    fun getBroadcast(): InetAddress? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback) continue  // Do not want to use the loopback interface.

            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val broadcast = interfaceAddress.broadcast ?: continue
                return broadcast
            }
        }
        return null
    }

    fun join() = runBlocking {
        val (msg, send_id) = encode(MessageType.PING)
        val channel = Channel<Boolean> (1)

        val address = getBroadcast()
        if(address == null) {
            println("Could not locate broadcast address")
            return@runBlocking
        }

        val socket = DatagramSocket()

        socket.broadcast = true
        socket.send(DatagramPacket(msg, 1024, InetSocketAddress(address, 55555)))


        val t = thread(start = true) {
            try {
                while (true) {
                    val data = ByteArray(1024)
                    val packet = DatagramPacket(data, data.size)
                    socket.receive(packet)
                    val (response, response_id) = decode(data) ?: continue
                    if (Node(response.ip, response.port) == Globals.localNode) continue
                    if (response.type != MessageType.PONG)
                        continue
                    runBlocking { channel.send(true) }
                    break
                }
            } catch (_: Exception) {}
        }

        val result = withTimeoutOrNull(1000) { channel.receive() }
        socket.close()
        channel.close()
        t.interrupt()
        if(result == null) {
            println("Could not connect to network. Please try again")
            return@runBlocking
        }
        println("Connected successfully")
    }

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

    fun store(bytes: ByteArray) = runBlocking {
        val key = Key(bytes)
        val nodes = recursiveNodeLookup(key)

        nodes.map {
            Pair(it, ServerSocket(0))
        }.forEach {
            val (msg, send_id) = encode(MessageType.STORE, Node(Globals.localNode.ip, it.second.localPort))
            val job = socket.send(msg, send_id, it.first) {}
            job.cancel()//We dont need the callbacks
            launch {
                val localSocket = it.second.accept()
                val output = localSocket.getOutputStream()
                output.write(key.toBytes())
                output.write(bytes)
                output.flush()
                localSocket.close()
                it.second.close()
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun get(key: Key) = runBlocking {
        val result = recursiveValueLookup(key)
        if(result == null) {
            println("Could not locate file")
            return@runBlocking
        }
        val serverSocket = ServerSocket(0)
        val (msg, send_id) = encode(MessageType.GET_VALUE, Node(Globals.localNode.ip, serverSocket.localPort))
        val job = socket.send(msg, send_id, result) {}
        job.cancel()//We dont need the callbacks
        val localSocket = serverSocket.accept()
        localSocket.getOutputStream().write(key.toBytes())
        localSocket.getOutputStream().flush()
        Globals.storage.put(key.toBytes().toHexString(), localSocket.getInputStream())
        localSocket.close()
        serverSocket.close()

        println("Found")
    }

    fun rebuild() {
        val keys = Globals.storage.keys()
        println("Items that will be rebuild:")
        keys.forEach { println(it) }
        Globals.storage.wipe()
        keys.forEach { get(Key.fromString(it)) }
        println("Local storage rebuilt")
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
            nodes.forEach {  Globals.routingTable.addNode(it)}
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

    private fun recursiveValueLookup(key: Key): Node? = runBlocking {
        var lastClosest = Globals.routingTable.findClosest(key)

        var result: Node? = null

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
                                result = Node(it.ip, it.port)
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
            nodes.forEach {  Globals.routingTable.addNode(it)}
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

    suspend fun startHeartbeat() = coroutineScope {
        while(true) {
            delay(Globals.heartbeatInterval)
            Globals.storage.keys()
                .mapNotNull {
                    Globals.storage.get(Key.fromString(it))
                }
                .forEach {
                    launch {
                        val data = it.readAllBytes()
                        store(data)
                        it.close()
                    }
            }
        }

    }
}

