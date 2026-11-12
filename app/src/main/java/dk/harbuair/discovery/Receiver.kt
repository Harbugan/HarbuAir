package dk.harbuair.discovery

/** A discovered AirPlay endpoint, merged from _airplay._tcp and _raop._tcp records. */
data class Receiver(
    /** Display name (instance name of _airplay._tcp; _raop prefix "MAC@" stripped). */
    val name: String,
    val host: String?,                    // first IPv4 address, if resolved
    val airplayPort: Int?,                // _airplay._tcp (AirPlay 2 control port, usually 7000)
    val raopPort: Int?,                   // _raop._tcp
    val airplayTxt: Map<String, String>,
    val raopTxt: Map<String, String>,
) {
    val model: String? get() = airplayTxt["model"] ?: raopTxt["am"]
    val deviceId: String? get() = airplayTxt["deviceid"]
    val features: String? get() = airplayTxt["features"] ?: raopTxt["ft"]
    val statusFlags: String? get() = airplayTxt["flags"] ?: raopTxt["sf"]
    val passwordRequired: Boolean get() = airplayTxt["pw"] == "true" || raopTxt["pw"] == "true"
    /** Group fields are reported RAW: how a HomePod stereo pair advertises itself is a hypothesis to verify. */
    val groupId: String? get() = airplayTxt["gid"]
    val groupLeaderFlag: String? get() = airplayTxt["gcgl"] ?: airplayTxt["igl"]

    val type: DeviceType get() = DeviceType.fromModel(model)

    /** Connection port: AirPlay 2 control port if advertised, else RAOP port. */
    val port: Int? get() = airplayPort ?: raopPort

    val key: String get() = name.lowercase()
}

enum class DeviceType(val label: String) {
    HOMEPOD("HomePod"), APPLE_TV("Apple TV"), MAC("Mac"), OTHER("Other/unknown");

    companion object {
        /** model strings seen in TXT: AudioAccessory5,1 (HomePod mini), AppleTV14,1, MacBookPro..., etc. */
        fun fromModel(model: String?): DeviceType = when {
            model == null -> OTHER
            model.startsWith("AudioAccessory") -> HOMEPOD
            model.startsWith("AppleTV") -> APPLE_TV
            model.startsWith("Mac") -> MAC
            else -> OTHER
        }
    }
}
