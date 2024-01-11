import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import java.net.*
import java.util.*
import kotlin.concurrent.thread


class Server(private val socket: DatagramSocket) {
    private val channel = Channel<Node>()
    private val outgoingChannel = Channel<Node>()

    fun listen() {
        tcpIncomingHandler()
        tcpOutgoingHandler()
        val receiveData = ByteArray(1024)
        while (true) {
            val receiverPacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receiverPacket)
            if(Globals.disabled){
                continue
            }
            handle(receiveData, receiverPacket.address, receiverPacket.port)
            Arrays.fill(receiveData, 0)
        }
    }

    private fun tcpOutgoingHandler() {
        thread(start = true) {
            runBlocking {
                while (true) {
                    val node = outgoingChannel.receive()
                    launch {
                        val socket = Socket(node.ip, node.port)
                        val hash = ByteArray(20)
                        socket.getInputStream().read(hash)
                        val value = Globals.storage.get(Key.fromBytes(hash))
                        if(value == null) {
                            socket.close()
                            return@launch
                        }
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while ((value.read(buffer).also { bytesRead = it }) != -1) {
                            socket.getOutputStream().write(buffer, 0, bytesRead);
                        }
                        value.close()
                        socket.close()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun tcpIncomingHandler() {
        thread(start = true) {
            runBlocking {
                while(true) {
                    val node = channel.receive()
                    launch {
                        try {
                            //Connect to node from message
                            val socket = Socket(node.ip, node.port)
                            val hash = ByteArray(20)
                            socket.getInputStream().read(hash)
                            Globals.storage.put(hash.toHexString(), socket.getInputStream())
                            socket.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun send(bytes: ByteArray, ip: InetAddress, port: Int) {
        socket.send(DatagramPacket(bytes ,1024, InetSocketAddress(ip, port)))
    }

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
                runBlocking {
                    channel.send(message.content as Node)
                }
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
                    encode(MessageType.REPLY_FIND_VALUE, null, id)
                } else {
                    val nodes = Globals.routingTable.findClosest(key)
                    encode(MessageType.REPLY_FIND_NODE, nodes, id)
                }
            }
            MessageType.GET_VALUE -> {
                val socket = message.content as Node
                runBlocking { outgoingChannel.send(socket) }
                return
            }
            else -> {
                return
            }
        }
        send(reply.first, ip, port)
    }
}