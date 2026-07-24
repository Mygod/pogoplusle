package be.mygod.pogoplusplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GameStringsTest {
    private fun revision(
        packageName: String = "com.nianticlabs.pokemongo",
        longVersionCode: Long = 1,
        lastUpdateTime: Long = 2,
        publicSourceDir: String = "/data/app/base.apk",
        splitPublicSourceDirs: List<String> = listOf("/data/app/split_config.en.apk"),
    ) = GamePackageRevision(
        packageName = packageName,
        longVersionCode = longVersionCode,
        lastUpdateTime = lastUpdateTime,
        publicSourceDir = publicSourceDir,
        splitPublicSourceDirs = splitPublicSourceDirs,
    )

    private fun strings(value: String) = GameStrings(
        disconnecting = setOf(value),
        itemInventoryFull = emptySet(),
        pokemonInventoryFull = emptySet(),
        outOfPokeballs = emptySet(),
        capturedPokemon = emptySet(),
        escapedPokemon = emptySet(),
        retrievedAnItem = emptySet(),
        ignoredPokestop = emptySet(),
        retrievedItems = emptySet(),
    )

    @Test
    fun unchangedRevisionLoadsOnce() {
        var loads = 0
        val cache = GameStringsCache { strings("load-${++loads}") }

        val first = cache.get(revision())
        val second = cache.get(revision())

        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun packageResourceChangesReload() {
        var loads = 0
        val cache = GameStringsCache { strings("load-${++loads}") }

        cache.get(revision())
        cache.get(revision(longVersionCode = 2))
        cache.get(revision(longVersionCode = 2, lastUpdateTime = 3))
        cache.get(revision(longVersionCode = 2, lastUpdateTime = 3, publicSourceDir = "/data/app/new-base.apk"))
        cache.get(revision(
            longVersionCode = 2,
            lastUpdateTime = 3,
            publicSourceDir = "/data/app/new-base.apk",
            splitPublicSourceDirs = listOf("/data/app/split_config.ja.apk"),
        ))

        assertEquals(5, loads)
    }

    @Test
    fun packagesAreCachedIndependently() {
        val loads = mutableMapOf<String, Int>()
        val cache = GameStringsCache { packageName ->
            strings("$packageName-${loads.merge(packageName, 1, Int::plus)}")
        }
        val global = revision()
        val galaxy = revision(packageName = "com.nianticlabs.pokemongo.ares")

        val globalStrings = cache.get(global)
        val galaxyStrings = cache.get(galaxy)

        assertSame(globalStrings, cache.get(global))
        assertSame(galaxyStrings, cache.get(galaxy))
        assertEquals(mapOf(global.packageName to 1, galaxy.packageName to 1), loads)
    }

    @Test
    fun clearForcesReload() {
        var loads = 0
        val cache = GameStringsCache { strings("load-${++loads}") }

        cache.get(revision())
        cache.clear()
        cache.get(revision())

        assertEquals(2, loads)
    }

    @Test
    fun failedLoadIsNotCached() {
        var loads = 0
        val cache = GameStringsCache {
            if (++loads == 1) null else strings("loaded")
        }

        assertNull(cache.get(revision()))
        assertEquals(strings("loaded"), cache.get(revision()))
        assertEquals(2, loads)
    }

    @Test
    fun builderCombinesEquivalentStatesAndDeduplicatesTranslations() {
        val builder = GameStringsBuilder()
        builder.add(
            disconnectingCompanionDevice = "Disconnecting",
            disconnectingGoPlus = "Disconnecting",
            itemInventoryFull = "Items full",
            pokemonInventoryFull = null,
            outOfPokeballs = null,
            capturedPokemon = null,
            escapedPokemon = null,
            retrievedAnItem = "You received an item",
            pokestopCooldown = "Try again later",
            pokestopOutOfRange = "Out of range",
            retrievedItems = "You received %s items",
        )
        builder.add(
            disconnectingCompanionDevice = "切断中",
            disconnectingGoPlus = null,
            itemInventoryFull = "Items full",
            pokemonInventoryFull = null,
            outOfPokeballs = null,
            capturedPokemon = null,
            escapedPokemon = null,
            retrievedAnItem = null,
            pokestopCooldown = null,
            pokestopOutOfRange = null,
            retrievedItems = null,
        )

        val result = builder.build()

        assertEquals(setOf("Disconnecting", "切断中"), result.disconnecting)
        assertEquals(setOf("Items full"), result.itemInventoryFull)
        assertEquals(setOf("You received an item"), result.retrievedAnItem)
        assertEquals(setOf("Try again later", "Out of range"), result.ignoredPokestop)
        assertEquals(setOf("You received %s items"), result.retrievedItems)
        assertEquals(emptySet<String>(), result.pokemonInventoryFull)
    }
}
