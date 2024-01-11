import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Message structure:
 * 1st Byte is message type
 * 2-21 is source socket
 * 22-29 is messageID
 * Rest is message dependent content
 * 1005-1024 is hash of all previous bytes
 *
 * Message Codes:
 * 1 Ping
 * 2 Store
 * 3 Find Node
 * 4 Find Value
 * 11 Pong
 * 12 Reply Find Node
 */
data class Message(val type: MessageType, val ip: InetAddress, val port: Int, val content: Any?)
enum class MessageType(val id: Byte) {
    PING(1),STORE(2),FIND_NODE(3),FIND_VALUE(4),PONG(11),REPLY_FIND_NODE(12),REPLY_FIND_VALUE(13),GET_VALUE(14)
}

val random = SecureRandom()

fun decode(bytes: ByteArray): Pair<Message, Long>? {
    if(bytes.size != 1024) {
        TODO("Invalid Message handling")
    }
    val md = MessageDigest.getInstance("SHA-1")
    val hash = md.digest(bytes.dropLast(20).toByteArray()).toList()
    val expectedHash = bytes.takeLast(20)
    if(hash != expectedHash) {
        TODO("Invalid Message handling")
    }

    val source = bytes.drop(1).take(20)
    //TODO IPv4 addresses must be 4 bytes, so need to detect og size
    val ipBytes = source.take(16)

    val ip = if (ipBytes.take(12) == ByteArray(12).toList())
        InetAddress.getByAddress(ipBytes.takeLast(4).toByteArray())
    else InetAddress.getByAddress(ipBytes.toByteArray())
    val port = ByteBuffer.wrap(source.takeLast(4).toByteArray()).getInt()

    val id = ByteBuffer.wrap(bytes.drop(21).take(8).toByteArray()).getLong()

    val msg = when(bytes[0].toInt()) {
        1 -> Message(MessageType.PING, ip, port, null)
        2 -> {
            val tcpSocket = Node.fromBytes(bytes.drop(29).take(20).toByteArray())
            Message(MessageType.STORE, ip, port, tcpSocket)
        }
        3 -> {
            val targetKey = Key.fromBytes(bytes.drop(29).take(20).toByteArray())
            Message(MessageType.FIND_NODE, ip, port, targetKey)
        }
        4 -> {
            val targetKey = Key.fromBytes(bytes.drop(29).take(20).toByteArray())
            Message(MessageType.FIND_VALUE, ip, port, targetKey)
        }
        11 -> Message(MessageType.PONG, ip, port, null)
        12 -> {
            val amount = bytes[29].toInt()
            val nodes = List<Node>(amount) {
                Node.fromBytes(bytes.drop(30 + it * 20).take(20).toByteArray())
            }
            Message(MessageType.REPLY_FIND_NODE, ip, port, nodes)
        }
        13 -> {
            val value = bytesToString(bytes.drop(29))
            Message(MessageType.REPLY_FIND_VALUE, ip, port, value)
        }
        14 -> {
            val soc = Node.fromBytes(bytes.drop(29).take(20).toByteArray())
            Message(MessageType.GET_VALUE, ip, port, soc)
        }
        else -> {
            return null
        }
    }
    return Pair(msg, id)
}

fun encode(type: MessageType, content: Any? = null, id: Long? = null): Pair<ByteArray, Long> {
    val bytes = ByteArray(1024)
    bytes[0] = type.id

    val nodeSocket = Globals.localNode.toBytes()
    replaceSection(bytes, nodeSocket, 1)

    val messageID = id ?: random.nextLong()
    replaceSection(bytes, ByteBuffer.allocate(8).putLong(messageID).array(), 21)

    val contentBytes = when(type) {
        MessageType.PING -> ByteArray(0)
        MessageType.STORE -> {
            (content as Node).toBytes()
        }
        MessageType.FIND_NODE -> {
            (content as Key).toBytes()
        }
        MessageType.FIND_VALUE -> {
            (content as Key).toBytes()
        }
        MessageType.PONG -> ByteArray(0)
        MessageType.REPLY_FIND_NODE -> {
            val nodes = content as List<Node>
            val b = ByteArray(1)
            b[0] = nodes.size.toByte()
            b + toBytes(nodes)
        }
        MessageType.REPLY_FIND_VALUE -> {
            ByteArray(0)
        }
        MessageType.GET_VALUE -> {
            (content as Node).toBytes()
        }
    }
    replaceSection(bytes, contentBytes, 29)
    val md = MessageDigest.getInstance("SHA-1")
    val hash = md.digest(bytes.dropLast(20).toByteArray())
    replaceSection(bytes, hash, 1004)
    return Pair(bytes, messageID)
}