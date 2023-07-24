package domain.common.data.info

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

/**
 * DomainInfo is a base class for all DomainInfo classes.
 *
 * Domain object encapsulate this DomainInfo class to provide mutable information about the domain object.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion`
 */

@Serializable
open class DomainInfo(
    @Transient            // prevent kotlinx serialization
    @kotlin.jvm.Transient // prevent gson serialization
    override val id: UUID2<*> = UUID2(UUID2.randomUUID2()),
) : Model(id)