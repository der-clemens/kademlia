import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*
import java.nio.ByteBuffer

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

fun getLocalAddress(): InetAddress {
    val int = NetworkInterface.getNetworkInterfaces().toList().filter{ it.displayName == "en0" }
        .flatMap{ it.interfaceAddresses }
        .map { it.address }
        .filterIsInstance<Inet4Address>()
    return int[0]
}

fun callEndpoint(url: String): String? {
    val url = URI.create(url).toURL()
    val con: HttpURLConnection = url.openConnection() as HttpURLConnection
    con.setRequestMethod("GET")
    val status = con.responseCode
    if(status != 200) {
        return null
    }
    val stream = BufferedReader(InputStreamReader(con.inputStream))
    var inputLine: String?
    val content = StringBuffer()
    while ((stream.readLine().also { inputLine = it }) != null) {
        content.append(inputLine)
    }
    stream.close()
    con.disconnect()
    return content.toString()
}
