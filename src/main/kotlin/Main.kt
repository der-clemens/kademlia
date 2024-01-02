import kotlinx.coroutines.runBlocking
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

fun main(args: Array<String>) = runBlocking {
    val socket = DatagramSocket(0, InetAddress.getLocalHost())
    Globals.localNode = Node(socket.localAddress, socket.localPort)
    val server = Server(socket)
    println("Started on ${socket.localAddress} ${socket.localPort}")
    thread(start = true) {
        server.listen()
    }
    //start client and start listening
    val client = Client()
//    client.startReceiver()
    readAndProcessInput(client)
}
