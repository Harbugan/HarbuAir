package dk.harbuair.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import dk.harbuair.diag.DiagLog
import java.net.Inet4Address
import java.util.concurrent.Executors

/**
 * mDNS/DNS-SD discovery of _airplay._tcp and _raop._tcp via Android NsdManager (API 34+).
 * Needs no runtime permission; a Wi-Fi multicast lock is held only while running.
 * Callbacks arrive on a private executor thread; [onChange] is invoked from there.
 */
class AirPlayDiscovery(context: Context, private val onChange: (List<Receiver>) -> Unit) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val exec = Executors.newSingleThreadExecutor()
    private var lock: WifiManager.MulticastLock? = null

    private data class Rec(val type: String, val instance: String, val host: String?, val port: Int?, val txt: Map<String, String>)

    private val records = HashMap<String, Rec>()          // key: type|instance
    private val infoCallbacks = HashMap<String, NsdManager.ServiceInfoCallback>()
    private val discoveryListeners = ArrayList<NsdManager.DiscoveryListener>()
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        lock = wifi.createMulticastLock("harbuair-mdns").apply { setReferenceCounted(false); acquire() }
        for (type in listOf("_airplay._tcp", "_raop._tcp")) {
            val l = listenerFor(type)
            discoveryListeners += l
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l)
        }
        DiagLog.add("discovery", "started (_airplay._tcp, _raop._tcp)")
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        discoveryListeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListeners.clear()
        infoCallbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        infoCallbacks.clear()
        records.clear()
        runCatching { lock?.release() }
        lock = null
        DiagLog.add("discovery", "stopped")
    }

    private fun listenerFor(type: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, code: Int) = DiagLog.add("discovery", "start failed $t code=$code")
        override fun onStopDiscoveryFailed(t: String, code: Int) = DiagLog.add("discovery", "stop failed $t code=$code")
        override fun onServiceFound(info: NsdServiceInfo) = track(type, info)
        override fun onServiceLost(info: NsdServiceInfo) = untrack(type, info.serviceName)
    }

    @Synchronized
    private fun track(type: String, info: NsdServiceInfo) {
        if (!running) return
        val k = "$type|${info.serviceName}"
        if (infoCallbacks.containsKey(k)) return
        val cb = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(code: Int) =
                DiagLog.add("discovery", "resolve failed ${info.serviceName} code=$code")
            override fun onServiceUpdated(s: NsdServiceInfo) = update(type, s)
            override fun onServiceLost() = untrack(type, info.serviceName)
            override fun onServiceInfoCallbackUnregistered() {}
        }
        infoCallbacks[k] = cb
        nsd.registerServiceInfoCallback(info, exec, cb)
    }

    @Synchronized
    private fun update(type: String, s: NsdServiceInfo) {
        val ipv4 = s.hostAddresses.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
        val txt = s.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8) ?: "" }
        records["$type|${s.serviceName}"] = Rec(type, s.serviceName, ipv4, s.port.takeIf { it > 0 }, txt)
        publish()
    }

    @Synchronized
    private fun untrack(type: String, instance: String) {
        val k = "$type|$instance"
        infoCallbacks.remove(k)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        if (records.remove(k) != null) publish()
    }

    private fun publish() = onChange(merge(records.values.map { it }))

    private fun merge(recs: List<Rec>): List<Receiver> {
        // _raop instance names look like "AABBCCDDEEFF@Living Room"
        fun display(r: Rec) = if (r.type == "_raop._tcp") r.instance.substringAfter('@') else r.instance
        return recs.groupBy { display(it).lowercase() }.map { (_, g) ->
            val ap = g.firstOrNull { it.type == "_airplay._tcp" }
            val ra = g.firstOrNull { it.type == "_raop._tcp" }
            Receiver(
                name = display(ap ?: ra!!),
                host = ap?.host ?: ra?.host,
                airplayPort = ap?.port, raopPort = ra?.port,
                airplayTxt = ap?.txt ?: emptyMap(), raopTxt = ra?.txt ?: emptyMap(),
            )
        }.sortedBy { it.name.lowercase() }
    }
}
