package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList

internal data class GameStrings(
    val disconnecting: Set<String>,
    val itemInventoryFull: Set<String>,
    val pokemonInventoryFull: Set<String>,
    val outOfPokeballs: Set<String>,
    val capturedPokemon: Set<String>,
    val escapedPokemon: Set<String>,
    val retrievedAnItem: Set<String>,
    val ignoredPokestop: Set<String>,
    val retrievedItems: Set<String>,
)

internal class GameStringsBuilder {
    private val disconnecting = mutableSetOf<String>()
    private val itemInventoryFull = mutableSetOf<String>()
    private val pokemonInventoryFull = mutableSetOf<String>()
    private val outOfPokeballs = mutableSetOf<String>()
    private val capturedPokemon = mutableSetOf<String>()
    private val escapedPokemon = mutableSetOf<String>()
    private val retrievedAnItem = mutableSetOf<String>()
    private val ignoredPokestop = mutableSetOf<String>()
    private val retrievedItems = mutableSetOf<String>()

    fun add(
        disconnectingCompanionDevice: String?,
        disconnectingGoPlus: String?,
        itemInventoryFull: String?,
        pokemonInventoryFull: String?,
        outOfPokeballs: String?,
        capturedPokemon: String?,
        escapedPokemon: String?,
        retrievedAnItem: String?,
        pokestopCooldown: String?,
        pokestopOutOfRange: String?,
        retrievedItems: String?,
    ) {
        disconnectingCompanionDevice?.let(disconnecting::add)
        disconnectingGoPlus?.let(disconnecting::add)
        itemInventoryFull?.let(this.itemInventoryFull::add)
        pokemonInventoryFull?.let(this.pokemonInventoryFull::add)
        outOfPokeballs?.let(this.outOfPokeballs::add)
        capturedPokemon?.let(this.capturedPokemon::add)
        escapedPokemon?.let(this.escapedPokemon::add)
        retrievedAnItem?.let(this.retrievedAnItem::add)
        pokestopCooldown?.let(ignoredPokestop::add)
        pokestopOutOfRange?.let(ignoredPokestop::add)
        retrievedItems?.let(this.retrievedItems::add)
    }

    fun build() = GameStrings(
        disconnecting = disconnecting.toSet(),
        itemInventoryFull = itemInventoryFull.toSet(),
        pokemonInventoryFull = pokemonInventoryFull.toSet(),
        outOfPokeballs = outOfPokeballs.toSet(),
        capturedPokemon = capturedPokemon.toSet(),
        escapedPokemon = escapedPokemon.toSet(),
        retrievedAnItem = retrievedAnItem.toSet(),
        ignoredPokestop = ignoredPokestop.toSet(),
        retrievedItems = retrievedItems.toSet(),
    )
}

internal data class GamePackageRevision(
    val packageName: String,
    val longVersionCode: Long,
    val lastUpdateTime: Long,
    val publicSourceDir: String?,
    val splitPublicSourceDirs: List<String>,
)

internal class GameStringsCache(private val loader: (String) -> GameStrings?) {
    private data class Entry(val revision: GamePackageRevision, val strings: GameStrings)

    private val entries = mutableMapOf<String, Entry>()

    fun get(revision: GamePackageRevision): GameStrings? {
        entries[revision.packageName]?.takeIf { it.revision == revision }?.let { return it.strings }
        val strings = loader(revision.packageName) ?: run {
            entries.remove(revision.packageName)
            return null
        }
        entries[revision.packageName] = Entry(revision, strings)
        return strings
    }

    fun clear() = entries.clear()
}

@SuppressLint("DiscouragedApi")
internal fun Context.loadGameStrings(): GameStrings {
    val baseResources = resources
    fun id(name: String) = baseResources.getIdentifier(name, "string", packageName)
    val disconnectingCompanionDevice = id("Disconnecting_Companion_Device")
    val disconnectingGoPlus = id("Disconnecting_GO_Plus")
    val itemInventoryFull = id("Item_Inventory_Full")
    val pokemonInventoryFull = id("Pokemon_Inventory_Full")
    val outOfPokeballs = id("Out_Of_Pokeballs")
    val capturedPokemon = id("Captured_Pokemon")
    val escapedPokemon = id("Pokemon_Escaped")
    val retrievedAnItem = id("Retrieved_an_Item")
    val pokestopCooldown = id("Pokestop_Cooldown")
    val pokestopOutOfRange = id("Pokestop_Out_Of_Range")
    val retrievedItems = id("Retrieved_Items")
    val builder = GameStringsBuilder()

    fun Resources.string(id: Int, vararg formatArgs: Any) = when {
        id == 0 -> null
        formatArgs.isEmpty() -> getString(id)
        else -> getString(id, *formatArgs)
    }
    fun add(resources: Resources) = builder.add(
        disconnectingCompanionDevice = resources.string(disconnectingCompanionDevice),
        disconnectingGoPlus = resources.string(disconnectingGoPlus),
        itemInventoryFull = resources.string(itemInventoryFull),
        pokemonInventoryFull = resources.string(pokemonInventoryFull),
        outOfPokeballs = resources.string(outOfPokeballs),
        capturedPokemon = resources.string(capturedPokemon),
        escapedPokemon = resources.string(escapedPokemon),
        retrievedAnItem = resources.string(retrievedAnItem, ""),
        pokestopCooldown = resources.string(pokestopCooldown),
        pokestopOutOfRange = resources.string(pokestopOutOfRange),
        retrievedItems = resources.string(retrievedItems),
    )

    add(baseResources)
    val baseConfiguration = Configuration(baseResources.configuration)
    for (localeTag in assets.locales.orEmpty().asSequence().filter(String::isNotEmpty).distinct()) {
        add(createConfigurationContext(Configuration(baseConfiguration).apply {
            setLocales(LocaleList.forLanguageTags(localeTag))
        }).resources)
    }
    return builder.build()
}
