import java.math.BigInteger
import java.util.TreeSet

class RoutingTable {
    private val buckets: List<Bucket> = List(Globals.KEY_SIZE) { Bucket(it) }

    //Bucket ID is based on Key distance
    private fun getBucket(key: Key) =
        buckets[(Globals.localNode.key.getDistance(key)-1).coerceAtLeast(0)]

    fun addNode(node: Node) {
        if(Globals.localNode.key == node.key) {
            return
        }
        getBucket(node.key).addNode(node)
    }

    fun findClosest(key: Key): List<Node> =
        buckets.flatMap { it.getNodes() }
            .sortedBy { BigInteger(it.key.xor(key).toBytes()).abs() }
            .take(Globals.k)
}


class Bucket(private val bucketId: Int) {
    private val nodes = TreeSet<Node>()


    fun addNode(node: Node) {
        nodes.add(node)
//        if(nodes.size < Globals.k) {
//            nodes.add(node)
//            return
//        }
//        val last = nodes.last
//        TODO("PING last seen node")
    }

    fun evictNode(node: Node) {

    }

    fun getNodes(): TreeSet<Node> =
        TreeSet<Node>().apply { addAll(nodes) }
}