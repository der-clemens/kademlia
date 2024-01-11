import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.BindException
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

@OptIn(DelicateCoroutinesApi::class)
fun main(args: Array<String>) = runBlocking {
    val localAddress = getLocalAddress()
    val socket = try {
        DatagramSocket(55555, localAddress)
    } catch (_: BindException) {
        DatagramSocket(0, localAddress)
    }
    Globals.localNode = Node(socket.localAddress, socket.localPort)

    val server = Server(socket)
    println("Started on ${socket.localAddress} ${socket.localPort}")
    thread(start = true) {
        server.listen()
    }
    //start client and start listening
    val client = Client()
    GlobalScope.launch {
        client.startHeartbeat()
    }
//    client.startReceiver()
    readAndProcessInput(client)
}
