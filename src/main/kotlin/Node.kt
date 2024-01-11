import java.net.InetAddress
import java.nio.ByteBuffer

class Node(val ip: InetAddress, val port: Int): Comparable<Node> {
    var lastSeen: Long = System.currentTimeMillis()
    val key: Key = Key(ip, port)

    companion object {
        fun fromBytes(bytes: ByteArray): Node {
            val ipBytes = bytes.take(16)
            val ip = if (ipBytes.take(12) == ByteArray(12).toList())
                InetAddress.getByAddress(ipBytes.takeLast(4).toByteArray())
            else InetAddress.getByAddress(ipBytes.toByteArray())
            val port = ByteBuffer.wrap(bytes.takeLast(4).toByteArray()).getInt()
            return Node(ip, port)
        }
    }

    fun toBytes(): ByteArray {
        val addressBytes = if (ip.address.size == 4) ByteArray(12) + ip.address else ip.address
        return addressBytes + ByteBuffer.allocate(4).putInt(port).array()
    }

    override fun compareTo(other: Node): Int {
        if(this === other ) {
            return 0
        }
        return if (lastSeen > other.lastSeen) 1 else -1
    }

    override fun equals(other: Any?): Boolean {
        if(other !is Node)
            return false
        return other.key == key
    }
}