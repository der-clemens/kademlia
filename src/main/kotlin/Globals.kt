object Globals {
    val KEY_SIZE = 160
    val k = 20
    val alpha = 3
    lateinit var localNode: Node
    val routingTable = RoutingTable()
    val storage = Storage()
    var disabled = false
    const val heartbeatInterval: Long = 1000*10
}