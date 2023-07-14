package domain.common.data.info.local

import common.uuid2.UUID2
import domain.common.data.Model

/**
 * EntityInfo<br></br>
 * <br></br>
 * "Dumb" Data Transfer Object for Database EntityBookInfo<br></br>
 * <br></br>
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
open class EntityInfo protected constructor(id: UUID2<*>) : Model(id)
