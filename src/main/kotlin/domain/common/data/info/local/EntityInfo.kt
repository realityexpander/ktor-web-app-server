package domain.common.data.info.local

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model

/**
 * EntityInfo
 *
 * "Dumb" Data Transfer Object for Database EntityBookInfo
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

open class EntityInfo protected constructor(id: UUID2<*>) : Model(id)
