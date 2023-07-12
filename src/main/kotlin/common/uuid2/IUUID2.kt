package common.uuid2

/**
 * **`IUUID2`** Marker interface.<br></br>
 *
 * Marker interface for any Domain **`Role`** class for use in **`UUID2<{Domain}>`** identifiers.<br></br>
 *
 * This interface is also used to get the type of the UUID2 as a String.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

// Keep this in global namespace to reduce wordiness at declaration sites
// - (avoiding: UUID2<UUID2.hasUUID2> wordiness)
interface IUUID2 {

    fun uuid2TypeStr(): String // Returns the Type of the UUID2 as a String.
                                // - Usually the class inheritance hierarchy path of the object
                                // - ie: "Model.DomainInfo.BookInfo" or "Role.Book"
}
