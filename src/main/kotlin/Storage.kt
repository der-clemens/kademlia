import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class Storage {
    private var data = mutableListOf<String>()
    private val dir = "store${Random.nextInt(100)}"
    init {
        if(!File(dir).isDirectory)
            File(dir).mkdir()
    }

    fun put(name: String, inputStream: InputStream) {
        val output = FileOutputStream("$dir/$name")
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.close()
        data.add(name)
        data = data.distinct().toMutableList()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun get(key: Key): FileInputStream? {
        try {
            val file = File("$dir/${key.toBytes().toHexString()}")
            return FileInputStream(file)
        } catch (_: Exception) {
            return null
        }
    }

    fun keys() = data

    fun wipe() {
        val directory = File(dir)
        directory.listFiles()?.forEach { it.delete() }
        data = mutableListOf()
    }
}