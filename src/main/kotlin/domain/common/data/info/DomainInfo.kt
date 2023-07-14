package domain.common.data.info

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model

/**
 * DomainInfo is a base class for all DomainInfo classes.<br></br>
 * <br></br>
 * Domain object encapsulate this DomainInfo class to provide mutable information about the domain object.<br></br>
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
open class DomainInfo protected constructor(id: UUID2<IUUID2>) : Model(id)
