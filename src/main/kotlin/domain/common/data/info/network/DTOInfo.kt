package domain.common.data.info.network

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model

/**
 * DTOInfo is a data holder class for transferring data to/from the Domain from API.<br></br>
 * <br></br>
 * Data Transfer Objects for APIs<br></br>
 * - Simple data holder class for transferring data to/from the Domain from API<br></br>
 * - Objects can be created from JSON<br></br>
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
open class DTOInfo protected constructor(id: UUID2<IUUID2>) : Model(id)
