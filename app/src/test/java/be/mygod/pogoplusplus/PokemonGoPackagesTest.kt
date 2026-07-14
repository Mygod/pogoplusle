package be.mygod.pogoplusplus

import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonGoPackagesTest {
    @Test
    fun supportedPackages() = assertEquals(listOf(
        "com.nianticlabs.pokemongo",
        "com.nianticlabs.pokemongo.ares",
    ), POKEMON_GO_PACKAGES)
}
