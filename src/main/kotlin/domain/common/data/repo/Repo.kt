package domain.common.data.repo

import common.log.ILog

/**
 * IRepo is a marker interface for all **`{DomainInfo}Repo`** classes.
 *
 * * **`Repo`** is the base class for all {Domain}Info Repository classes.
 * * A Repo only accepts/returns **`{Domain}Info`** objects.
 * * Repos access the Data layer, and do conversions between the data layer and domain.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

open class Repo(open val log: ILog) : IRepo
