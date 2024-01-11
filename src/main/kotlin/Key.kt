import java.net.InetAddress
import java.security.MessageDigest
import java.util.*

class Key {
    private var keyBits: BitSet

    companion object {
        fun fromString(key: String): Key {
            return fromBytes(key.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray())
        }

        fun fromBytes(key: ByteArray): Key {
            return Key("").apply {
                keyBits = BitSet.valueOf(key)
            }
        }
    }

    constructor(ip: InetAddress, port: Int) {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(toBytes(ip, port))
        keyBits = BitSet.valueOf(hash)
    }

    constructor(value: String) {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(value.toByteArray())
        keyBits = BitSet.valueOf(hash)
    }

    constructor(bytes: ByteArray) {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(bytes)
        keyBits = BitSet.valueOf(hash)
    }

    fun toBytes() =
        keyBits.toByteArray()

    fun xor(key: Key): Key {
        val res2 = Key("").apply {
            val res = keyBits.clone() as BitSet
            res.xor(key.keyBits)
            this.keyBits = res
        }
        return res2
    }

    fun getDistance(key: Key) =
        Globals.KEY_SIZE - this.xor(key).keyBits.nextSetBit(0)

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return this.toBytes().toHexString()
    }

    override fun equals(other: Any?): Boolean {
        if(other !is Key)
            return false
        return other.keyBits == keyBits
    }

    override fun hashCode(): Int {
        return keyBits.hashCode()
    }
}