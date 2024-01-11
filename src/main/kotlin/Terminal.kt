import java.io.File
import java.net.InetAddress
import java.util.*
import kotlin.random.Random


@OptIn(ExperimentalStdlibApi::class)
fun readAndProcessInput(client: Client) {
    val input = Scanner(System.`in`)


    while(true) {
        print(">");
        val inputs = input.nextLine().split(' ')
        when (inputs[0]) {
            "join" -> {
                if(inputs.size == 1) {
                    client.join()
                    continue
                }
                val address = InetAddress.getByName(inputs[1])
                val port = inputs[2].toInt()
                client.join(Node(address, port))
            }
            "store" -> {
                if(inputs[1] == "-f") {
                    val path = inputs[2]
                    try {
                        val file = File(path)
                        val bytes = file.readBytes()
                        val hash = Key(bytes).toBytes()
                        println("Item Key: ${hash.toHexString()}")
                        client.store(bytes)
                    } catch (_: Exception) {
                        println("Couldn't open file")
                    }
                    continue
                }
                val value = inputs[1]
                val hash = Key(value).toBytes()
                println("Item Key: ${hash.toHexString()}")
                client.store(value.toByteArray(Charsets.UTF_8))
            }
            "get" -> {
                val hash = inputs[1]
                val key = Key.fromBytes(hash.hexToByteArray())
                client.get(key)
            }
            "list" -> {
                Globals.storage.keys().forEach { println(it) }
            }
            "toggle" -> {
                Globals.disabled = !Globals.disabled
                println("The node is now ${if(Globals.disabled) "disabled" else "enabled"}")
            }
            "stock" -> {
                val stock = inputs[1]
                val apiKey = "QYR9N3PDMYHLDH5R"
                val response = callEndpoint("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=$stock&apikey=$apiKey")
                if(response == null) {
                    println("Couldn't connected to service")
                    continue
                }
                println(response)
                val hash = Key(response).toBytes()
                println("Item Key: ${hash.toHexString()}")
                client.store(response.toByteArray(Charsets.UTF_8))
            }
            "random" -> {
                val size = if(inputs.size == 1) {
                Random.nextInt(100)
                } else {
                    inputs[1].toInt()
                }
                val bytes = Random.nextBytes(size)
                val hash = Key(bytes).toBytes()
                println("Item Key: ${hash.toHexString()}")
                client.store(bytes)
            }
            "rebuild" -> {
                client.rebuild()
            }
            else -> {
                println("Unknown Command")
            }
        }
    }
}