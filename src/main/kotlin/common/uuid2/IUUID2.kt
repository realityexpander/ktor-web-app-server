package common.uuid2

/**
 * **`IUUID2`** Marker interface.
 *
 * Marker interface for any Domain **`Role`** class for use in **`UUID2<{Domain}>`** identifiers.
 *
 * This interface is also used to get the type of the UUID2 as a String.
 *
 * _Note: Keep this interface in global namespace to reduce wordiness at declaration sites_
 *  - ie: to avoid `UUID2<UUID2.hasUUID2>` wordiness
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IUUID2 {
    fun uuid2TypeStr(): String // Returns the Type of the UUID2 as a String.
                                // - Usually the class inheritance hierarchy path of the object
                                // - ie: "Model.DomainInfo.BookInfo" or "Role.Book"
}
