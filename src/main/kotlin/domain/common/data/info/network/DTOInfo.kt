package domain.common.data.info.network

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

/**
 * DTOInfo is a data holder class for transferring data to/from the Domain from API.<br></br>
 * <br></br>
 * Data Transfer Objects for APIs<br></br>
 * - Simple data holder class for transferring data to/from the Domain from API<br></br>
 * - Objects can be created from JSON<br></br>
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
open class DTOInfo(
    @Transient
    override val id: UUID2<*> = UUID2(UUID2.randomUUID2(UUID2::class.java))
) : Model(id) {
    constructor() : this(UUID2(UUID2.randomUUID2(UUID2::class.java)))
}