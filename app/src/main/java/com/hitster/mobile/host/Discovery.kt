package com.hitster.mobile.host

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/** A session advertised by another phone on the local network. */
data class FoundSession(val code: String, val hostName: String, val address: String, val port: Int) {
    val url get() = "ws://$address:$port"
}

/**
 * Zero‑config discovery of sessions on the local network (mDNS / Android NSD).
 * The host registers `_hitster._tcp` with the room code in the service name; guests browse for it.
 * Manual entry (IP:port shown in the host's lobby) is always available as a fallback.
 */
class Discovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val _sessions = MutableStateFlow<List<FoundSession>>(emptyList())
    val sessions = _sessions.asStateFlow()

    fun advertise(code: String, hostName: String, port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "HITSTER-$code"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("code", code)
            setAttribute("host", hostName.take(18))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "advertised ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, err: Int) { Log.w(TAG, "advertise failed $err") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, err: Int) {}
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { Log.w(TAG, "registerService", it) }
    }

    fun stopAdvertising() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun startDiscovery() {
        stopDiscovery()
        _sessions.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onStartDiscoveryFailed(t: String, err: Int) { Log.w(TAG, "discovery failed $err") }
            override fun onStopDiscoveryFailed(t: String, err: Int) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                if (!i.serviceName.startsWith("HITSTER-")) return
                resolve(i)
            }
            override fun onServiceLost(i: NsdServiceInfo) {
                val code = i.serviceName.removePrefix("HITSTER-")
                _sessions.value = _sessions.value.filterNot { it.code == code }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { Log.w(TAG, "discoverServices", it) }
    }

    // NsdManager allows only one resolve at a time ("already active" otherwise) – queue them.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private fun resolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.addLast(info)
            if (!resolving) resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val info = synchronized(resolveQueue) { resolveQueue.removeFirstOrNull()?.also { resolving = true } ?: run { resolving = false; null } } ?: return
        val cb = object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, err: Int) { Log.w(TAG, "resolve failed $err"); resolveNext() }
            override fun onServiceResolved(i: NsdServiceInfo) {
                val addr = (if (Build.VERSION.SDK_INT >= 34) i.hostAddresses.firstOrNull { it is Inet4Address } ?: i.hostAddresses.firstOrNull() else i.host)?.hostAddress
                if (addr != null) {
                    val attrs = i.attributes.orEmpty().mapValues { String(it.value ?: ByteArray(0)) }
                    val code = attrs["code"] ?: i.serviceName.removePrefix("HITSTER-")
                    val found = FoundSession(code = code, hostName = attrs["host"] ?: "Anfitrião", address = addr, port = i.port)
                    _sessions.value = (_sessions.value.filterNot { it.code == code } + found).sortedBy { it.code }
                }
                resolveNext()
            }
        }
        if (runCatching { nsd.resolveService(info, cb) }.isFailure) resolveNext()
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }

    companion object {
        private const val TAG = "Discovery"
        const val SERVICE_TYPE = "_hitster._tcp."

        /** Best guess of this phone's LAN IPv4 (Wi‑Fi or hotspot interface). */
        fun localIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { n -> if (n.name.startsWith("wlan") || n.name.startsWith("ap") || n.name.startsWith("swlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
