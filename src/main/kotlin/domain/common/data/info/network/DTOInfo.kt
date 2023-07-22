package domain.common.data.info.network

import common.uuid2.UUID2
import domain.common.data.Model
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

/**
 * DTOInfo is a data holder class for transferring data to/from the Domain from API.
 *
 * Data Transfer Objects for APIs
 * - Simple data holder class for transferring data to/from the Domain from API
 * - Objects can be created from JSON
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
open class DTOInfo(
    @Transient            // prevent kotlinx serialization
    @kotlin.jvm.Transient // prevent gson serialization
    override val id: UUID2<*> = UUID2.randomUUID2(UUID2::class.java)
) : Model(id) {
    constructor() : this(UUID2.randomUUID2(UUID2::class.java))
}