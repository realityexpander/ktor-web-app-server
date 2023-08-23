package domain.common.data.info.local

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * InfoEntity
 *
 * "Dumb" Data Transfer Object for Database BookInfoEntity
 *
 * Adds `id` field & methods to Model
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
open class InfoEntity(
    @Transient            // prevent kotlinx serialization
    @kotlin.jvm.Transient // prevent gson serialization
    override val id: UUID2<*> = UUID2.randomUUID2<IUUID2>()
) : Model(id)
