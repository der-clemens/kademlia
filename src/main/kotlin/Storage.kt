import java.util.concurrent.ConcurrentHashMap

class Storage {
    private val storage = ConcurrentHashMap<Key, String>()

    fun put(value: String) {
        storage[Key(value)] = value
    }

    fun get(key: Key): String? {
        return storage[key]
    }

    fun keys() =
        storage.keys().toList()
}