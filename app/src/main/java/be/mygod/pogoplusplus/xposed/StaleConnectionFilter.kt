package be.mygod.pogoplusplus.xposed

internal class StaleConnectionFilter(
    private val registeredAtMillis: Long,
    baselineAddresses: Set<String>,
    private val callbackWindowMillis: Long,
) {
    private val baselineAddresses = baselineAddresses.toSet()
    private val ignoredConnections = HashMap<Int, String>()

    @Synchronized
    fun shouldSuppress(connected: Boolean, connId: Int, address: String, nowMillis: Long): Boolean {
        if (connected) {
            if (address !in baselineAddresses || nowMillis - registeredAtMillis !in 0..callbackWindowMillis) {
                return false
            }
            ignoredConnections[connId] = address
            return true
        }
        if (ignoredConnections.remove(connId) != null) return true
        val iterator = ignoredConnections.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value == address) {
                iterator.remove()
                return true
            }
        }
        return false
    }
}
