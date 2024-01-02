import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset

fun toBytes(ip: InetAddress, port: Int): ByteArray {
    val addressBytes = if (ip.address.size == 4) ByteArray(12) + ip.address else ip.address
    return addressBytes + ByteBuffer.allocate(4).putInt(port).array()
}

fun toBytes(nodes: List<Node>) =
    nodes
        .map { it.toBytes() }
        .reduce { acc, bytes ->  acc + bytes}

fun bytesToString(bytes: List<Byte>): String {
    val end = bytes.indexOf(0.toByte())
    return bytes.take(end).toByteArray().toString(Charsets.UTF_8)
}

fun replaceSection(dest: ByteArray, source: ByteArray, offset: Int) {
    if (dest.size < source.size+offset) {
        TODO("Handle invalid size match")
    }
    for(i in source.indices) {
        dest[i+offset] = source[i]
    }
}