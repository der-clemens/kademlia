import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread


class Server(private val socket: DatagramSocket) {
    //private val tcpConnections = ConcurrentHashMap<>()
    private val channel = Channel<Node>()

    fun listen() {
        val receiveData = ByteArray(1024)
        while (true) {
            val receiverPacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receiverPacket)
            handle(receiveData, receiverPacket.address, receiverPacket.port)
            Arrays.fill(receiveData, 0)
        }
    }

    private fun tcpHandler() {
        thread(start = true) {
            runBlocking {
                while(true) {
                    val node = channel.receive()
                    launch {
                        //Connect to node from message
                        val socket = Socket()

                    }
                }
            }
        }
    }

    private fun send(bytes: ByteArray, ip: InetAddress, port: Int) {
        socket.send(DatagramPacket(bytes ,1024, InetSocketAddress(ip, port)))
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handle(receivedData: ByteArray, ip: InetAddress, port: Int) {
        val (message, id) = decode(receivedData) ?: return
        println(message)
        Globals.routingTable.addNode(Node(message.ip, message.port))
        runBlocking{
            delay(100)
        }
        val reply = when (message.type) {
            MessageType.PING -> {
                encode(MessageType.PONG, null, id)
            }
            MessageType.STORE -> {
                Globals.storage.put(message.content as String)
//                runBlocking {
//                    channel.send(Pair(message.))
//                }

                return
            }
            MessageType.FIND_NODE -> {
                val key = message.content as Key
                val nodes = Globals.routingTable.findClosest(key)
                encode(MessageType.REPLY_FIND_NODE, nodes, id)
            }
            MessageType.FIND_VALUE -> {
                val key = message.content as Key
                val value = Globals.storage.get(key)
                if(value != null) {
                    encode(MessageType.REPLY_FIND_VALUE, value, id)
                } else {
                    val nodes = Globals.routingTable.findClosest(key)
                    encode(MessageType.REPLY_FIND_NODE, nodes, id)
                }
            }
            else -> {
                return
            }
        }
        send(reply.first, ip, port)
    }
}