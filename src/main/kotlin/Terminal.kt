import java.net.InetAddress
import java.util.*

@OptIn(ExperimentalStdlibApi::class)
fun readAndProcessInput(client: Client) {
    val input = Scanner(System.`in`)


    while(true) {
        print(">");
        val inputs = input.nextLine().split(' ')
        when (inputs[0]) {
            "join" -> {
                println(InetAddress.getLocalHost().hostAddress)
                val address = InetAddress.getByName(inputs[1])
                val port = inputs[2].toInt()
                val hash = Key(address, port).toBytes()

                client.join(Node(address, port))
            }
            "store" -> {
                val value = inputs[1]
                val hash = Key(value).toBytes()
                println("Item Key: ${hash.toHexString()}")
                client.store(value)
            }
            "get" -> {
                val hash = inputs[1]
                val key = Key(hash.hexToByteArray())
                client.get(key)
            }
            "list" -> {
                Globals.storage.keys().forEach { println(it) }
            }
            else -> {
                println("Unknown Command")
            }
        }
    }
}