package common.uuid2

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import kotlinx.serialization.json.JsonElement
import org.bson.json.JsonParseException
import java.lang.reflect.Type
import java.util.*

/**
 * **`UUID2`** is a type-safe wrapper for an **`UUID`**, and it can be used in place
 * of an **`UUID`**.
 *
 *  Benefits:
 *
 *  * Used to enforce type-constrained **`UUID`s** for Objects that expect a specific
 *    type of **`UUID`**.
 *  * Allows for easier debugging and defining of types of objects that the **`UUID`** is
 *    referencing in Json payloads.
 *  * Includes implementation of type-safe **`HashMap`** for **`UUID2`s**.
 *  * **`UUID2`** is immutable.
 *
 *  *  **`IUUID2`** is a marker interface for classes that will be used with **`UUID2`**.
 *  *  Domain objects must be marked with the **`IUUID2`** marker interface to be used with
 *     **`UUID2`**.
 *
 * _Note on Class Inheritance Path:_
 *
 * The **`UUID2Type`** is derived from the "Class **Inheritance** Path", **NOT** the "Class Path" (also
 * called "Package" Path), like so:
 *
 * **`Model.Domain.BookInfo`** ⬅︎ *"Class Inheritance path" is the **`UUID2Type`.**
 *
 * ⩥⩥⩥ _instead of:_ ⩥⩥⩥
 *
 * **`com.realityexpander.App.domain.book.BookInfo`** ⬅︎ *"Class Path"**
 *
 * *Design note: The java "Class Path" of a Class changes if the package or package structure of a Class changes,
 * so **`UUID2`** uses the **"Class Inheritance Path"** to have more stable types.*
 *
 *
 * **UUID2 String Format example:**
 * ```
 * ・UUID2:Object.Role.User@00000000-0000-0000-0000-000000000001
 * ・▲▲▲▲▲⸻Always prefixed with `UUID2`
 * ・     ▲⸻ `:` divides between Prefix and Type
 * ・      ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲⸻ UUID2Type
 * ・                      ▲⸻ `@` divides the UUID2Type and UUID Value
 * ・                       ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲⸻ UUID Value
 * ```
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

class UUID2<TUUID2 : IUUID2> : IUUID2 {
    private val uuid: UUID
    private var _uuid2Type: String // Class Inheritance Path of the object the UUID refers to. '.' separated.

    // NOT final due to need for it to be set for creating objects via JSON deserialization. :( // todo - is there a way around this? Maybe reflection?
    @JvmOverloads
    constructor(uuid2: TUUID2, uuid2TypeStr: String? = uuid2.uuid2TypeStr()) {
        uuid = (uuid2 as UUID2<*>).uuid()
        _uuid2Type = if (uuid2TypeStr != null) {
            getNormalizedUuid2TypeString(uuid2TypeStr)
        } else {
            "UUID" // Default to untyped UUID
        }
    }
    @Suppress("UNCHECKED_CAST")
    constructor(uuid2: UUID2<*>, clazz: Class<*>?) : this(uuid2 as TUUID2, calcUUID2TypeStr(clazz))
    constructor(uuid: UUID, clazz: Class<*>) {
        this.uuid = uuid
        _uuid2Type = calcUUID2TypeStr(clazz)
    }
    constructor(uuid: UUID, uuid2Str: String) {
        this.uuid = uuid
        _uuid2Type = getNormalizedUuid2TypeString(uuid2Str)
    }
    constructor(uuid: UUID) {
        this.uuid = uuid
        _uuid2Type = "UUID" // untyped UUID
    }
    @Suppress("UNCHECKED_CAST")
    constructor(uuid2: UUID2<*>) : this(uuid2 as TUUID2, uuid2.uuid2TypeStr())
    constructor(clazz: Class<*>) : this(UUID.randomUUID(), clazz)

    ////////////////////////////////
    // Published Getters          //
    ////////////////////////////////
    fun uuid(): UUID {
        return uuid
    }

    override fun uuid2TypeStr(): String {
        return _uuid2Type
    }

    override fun toString(): String {
        return "UUID2:$_uuid2Type@$uuid"
    }

    override fun hashCode(): Int {
        return uuid().hashCode()
    }

    fun isOnlyUUIDEqual(other: UUID2<*>): Boolean {
        return other.uuid() == uuid()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return if (other !is UUID2<*>) false else other.uuid() == uuid() && uuid2TypeStr() == other.uuid2TypeStr()
    }

    fun isMatchingUUID2Type(checkUUID2: UUID2<*>): Boolean {
        return uuid2TypeStr() == checkUUID2.uuid2TypeStr()
    }

    fun isMatchingUUID2TypeStr(checkUUID2TypeStr: String): Boolean {
        return uuid2TypeStr() == checkUUID2TypeStr
    }

    fun toUUID(): UUID {
        return UUID(uuid.mostSignificantBits, uuid.leastSignificantBits)
    }

    fun toDomainUUID2(): UUID2<IUUID2> {
        return UUID2(this, uuid2TypeStr())
    }

    fun toUUID2(): UUID2<*> {
        return this
    }

    // Note: Should only be used when importing JSON
    fun _setUUID2TypeStr(uuid2TypeStr: String?): Boolean {
        _uuid2Type = getNormalizedUuid2TypeString(uuid2TypeStr)
        return true // always return `true` instead of a `void` return type
    }

    /**
     * Utility `HashMap` class for mapping `UUID2<TUUID2>` to `TEntity` Objects.<br></br>
     *
     * This class is a wrapper for `java.util.HashMap` where the `key` hash value used is
     * the hash of the `UUID` value s of  `UUID2<TUUID2>`'s embedded `UUID` object.
     *
     *  * **Problem:** The `java.util.HashMap` class uses the `hashCode()` of the `UUID2<TUUID2>`
     * object itself, which is ***not*** consistent between `UUID2<TUUID2>` objects
     * with the `UUID` same value.
     *
     *  * **Solution:** `UUID2.HashMap` uses the `hashCode()` from the embedded `UUID` object and:
     *
     *    1. The `hashCode()` is calculated from the `UUID`.
     *    2. The `UUID hashCode()` is consistent between `UUID2<TUUID2>` objects
     *       with the same UUID value.
     * ```
     * <TUUID2>  the type of the class that implements the IUUID2 interface, ie: `Book` or `Account`
     * <TEntity> the type of the object to be stored.
     * ```
     */
    class HashMap<TUUID2 : UUID2<*>?, TEntity> {
        // We have use 2 HashMaps because the .hashCode() of UUID2<T> is not consistent between UUID2<T> objects.
        // - The hash of UUID2<T> objects includes the "type" of the UUID2<T> object, which is not consistent between
        //   UUID2<T> objects.
        // - The .hashCode() of UUID's is consistent between UUID objects of the same value.
        val uuid2ToEntityMap = java.util.HashMap<TUUID2, TEntity>() // keeps the mapping of UUID2<T> to TEntity

        @Transient
        private val _uuidToEntityMap = HashMap<UUID, TEntity>()

        constructor()
        constructor(sourceDatabase: HashMap<TUUID2, TEntity>) { // Creates a copy of another UUID2.HashMap
            this.putAll(sourceDatabase)
        }

        constructor(sourceDatabase: java.util.HashMap<TUUID2, TEntity>) { // Creates a copy of another Database
            for ((uuid2, entity) in sourceDatabase) {
                put(uuid2!!, entity)
            }
        }

        override fun toString(): String {
            return uuid2ToEntityMap.toString()
        }

        operator fun get(uuid2: TUUID2): TEntity? {
            return _uuidToEntityMap[uuid2!!.uuid()]
        }

        fun put(uuid2: UUID2<*>, value: TEntity): TEntity? {
            @Suppress("UNCHECKED_CAST")
            uuid2ToEntityMap[uuid2 as TUUID2] = value
            return _uuidToEntityMap.put(uuid2.uuid(), value)
        }

        fun putAll(sourceDatabase: HashMap<TUUID2, TEntity>): ArrayList<TEntity> {
            val entities = ArrayList<TEntity>()
            for ((uuid2, entity) in sourceDatabase.uuid2ToEntityMap) {
                uuid2ToEntityMap[uuid2] = entity
                _uuidToEntityMap[uuid2!!.uuid()] = entity
                entities.add(entity)
            }
            return entities
        }

        fun remove(uuid2: TUUID2): TEntity? {
            uuid2ToEntityMap.remove(uuid2)
            return _uuidToEntityMap.remove(uuid2!!.uuid())
        }

        fun containsKey(uuid2: TUUID2): Boolean {
            return _uuidToEntityMap.containsKey(uuid2!!.uuid())
        }

        fun containsValue(entity: TEntity): Boolean {
            return _uuidToEntityMap.containsValue(entity)
        }

        @Throws(RuntimeException::class)
        fun keySet(): Set<TUUID2> {
            val uuid2Set: MutableSet<TUUID2> = HashSet()
            try {
                for (uuid2 in uuid2ToEntityMap.keys) {
                    uuid2Set.add(uuid2)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("HashMap.keySet(): Failed to convert UUID to UUID2<TDomainUUID>, uuid2ToEntityMap: " + uuid2ToEntityMap.keys)
            }
            return uuid2Set
        }

        @Throws(RuntimeException::class)
        fun entrySet(): Set<Map.Entry<TUUID2, TEntity?>> {
            val uuid2Set: MutableSet<Map.Entry<TUUID2, TEntity?>> = HashSet()
            try {
                for ((uuid2) in uuid2ToEntityMap) {
                    val uuid = uuid2!!.uuid()
                    val entity = _uuidToEntityMap[uuid2.uuid()]
                    uuid2Set.add(AbstractMap.SimpleEntry(uuid2, entity))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("HashMap.entrySet(): Failed to convert UUID to UUID2<TDomainUUID>, uuid2ToEntityMap: " + uuid2ToEntityMap.keys)
            }
            return uuid2Set
        }

        @Throws(RuntimeException::class)
        fun values(): ArrayList<TEntity> {
            val entityValues = ArrayList<TEntity>()
            try {
                for ((_, entity) in _uuidToEntityMap) {
                    entityValues.add(entity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("HashMap.values(): Failed to convert UUID to UUID2<TDomainUUID>, uuid2ToEntityMap: " + uuid2ToEntityMap.keys)
            }
            return entityValues
        }

        companion object {
            private const val serialVersionUID = 0x2743L
        }
    }

    ////////////////////////////
    ///// Private helpers //////
    ////////////////////////////

    private fun getNormalizedUuid2TypeString(uuid2TypeStr: String?): String {
        if (uuid2TypeStr == null) {
            return "UUID" // unspecified-type
        }

        // Change any '$' in the path of `uuid2TypeStr` into a '.'
        // - For some(?) reason Java returns delimiter `$` with: Model.Domain.BookInfo.class.getName();
        //   And returns returns `.` with: this.getClass().getName();
        val normalizedTypeStr = StringBuilder()
        for (i in uuid2TypeStr.indices) {
            if (uuid2TypeStr[i] == '$') {
                normalizedTypeStr.append('.')
            } else {
                normalizedTypeStr.append(uuid2TypeStr[i])
            }
        }
        return normalizedTypeStr.toString()
    }

    class Uuid2HashMapJsonDeserializer : JsonDeserializer<HashMap<*, *>?> {
        // Note: Deserializes all JSON Numbers to Longs for all UUID2.HashMap Entity Number values.
        // - For consistent number deserialization bc GSON defaults to Doubles.
        @Throws(JsonParseException::class)
        fun deserialize(
            jsonElement: JsonElement,
            type: Type?,
            jsonDeserializationContext: JsonDeserializationContext?
        ): HashMap<*, *> {
            val uuid2HashMapFromJson: HashMap<*, *> =
                deserialize(jsonElement, HashMap::class.java, jsonDeserializationContext!!)
            val uuid2ToUuidMap: HashMap<out UUID2<*>, Any> = HashMap()
            try {

                // Rebuild the UUID2<?> to Entity map
                for ((key, value) in uuid2HashMapFromJson.uuid2ToEntityMap) {
                    val uuid2Key = fromUUID2String<IUUID2>(key.toString()) // todo fix this to use the correct type
                    var entity = value
                        ?: throw RuntimeException("Uuid2HashMapGsonDeserializer.deserialize(): entity is null, uuid2Key=$uuid2Key")

                    // Convert any Numbers to Longs
                    if (entity is Number) {
                        entity = entity.toLong()
                    }
                    uuid2ToUuidMap.put(uuid2Key, entity)
                }
            } catch (e: IllegalArgumentException) {
                System.err.println(e.message)
                throw RuntimeException(e)
            }
            return uuid2ToUuidMap
        }

        override fun deserialize(
            json: com.google.gson.JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): HashMap<*, *>? {
            //return deserialize(json!!, typeOfT, context)

//            return UUID2HashMapGsonDeserializer.deserialize(json!!, typeOfT, context!!)
            return deserialize(json!!, typeOfT, context!!) // todo fix
        }
    }

    companion object {

        @Throws(IllegalArgumentException::class)
        fun <TDomainUUID2: IUUID2> fromUUID2String(uuid2FormattedString: String): UUID2<TDomainUUID2> {
            // format example:
            //
            // UUID2:Object.Role.User@00000000-0000-0000-0000-000000000001
            // ▲▲▲▲▲-- Always prefixed with `UUID2`
            //      ▲-- `:` divides between Prefix and Type
            //       ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲-- UUID2Type
            //                       ▲-- `@` divides the Type block and Value
            //                        ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲-- UUID Value

            val segments =
                uuid2FormattedString
                    .split("@".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            require(segments.size == 2) { "Invalid UUID2 formatted string, invalid number of segments: $uuid2FormattedString" }

            val typeSegments =
                segments[0]
                    .split(":".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            require(typeSegments[0] == "UUID2") { "Invalid UUID2 formatted string, no `UUID2` prefix: $uuid2FormattedString" }

            val uuid2TypeStr = typeSegments[1] // ie: Model.DomainInfo.BookInfo
            val uuidStr = segments[1] // ie: 00000000-0000-0000-0000-000000000001

            return UUID2(UUID.fromString(uuidStr), uuid2TypeStr)
        }

        fun isMatchingUUID2TypeStr(firstUuid2Str: String?, secondUuid2Str: String?): Boolean {
            if (firstUuid2Str == null) return false // note: null checks are acceptable for static methods.

            return if (secondUuid2Str == null) false
            else
                try {
                    val firstUUID2 = fromUUID2String<IUUID2>(firstUuid2Str)   // todo fix this to use the correct type
                    val secondUUID2 = fromUUID2String<IUUID2>(secondUuid2Str) // todo fix this to use the correct type
                    firstUUID2.isMatchingUUID2Type(secondUUID2)
                } catch (e: Exception) {
                    System.err.println("Error: Unable to find class for UUID2: $firstUuid2Str")
                    e.printStackTrace()
                    false
                }
        }

        ////////////////////////////////
        // Converters                 //
        ////////////////////////////////

        fun fromUUID2(id: UUID2<*>, clazz: Class<*>): UUID2<*> {
            return UUID2<UUID2<*>>(id, clazz) // todo fix this to use the correct type
        }

        fun fromUUID(uuid: UUID?): UUID2<IUUID2> {
            return UUID2(uuid ?: UUID.randomUUID())
        }

        fun fromUUIDString(uuidStr: String?): UUID2<IUUID2> {
            return UUID2(UUID.fromString(uuidStr))
        }

        ////////////////////////////////
        // Generators                 //
        ////////////////////////////////

        fun <TDomainUUID2 : IUUID2> randomUUID2(): TDomainUUID2 {
            @Suppress("UNCHECKED_CAST")
            return UUID2<TDomainUUID2>(UUID.randomUUID()) as TDomainUUID2
        }

        fun <TDomainUUID2 : IUUID2> randomUUID2(clazz: Class<TDomainUUID2>?): TDomainUUID2 {
            @Suppress("UNCHECKED_CAST")
            return UUID2<TDomainUUID2>(UUID.randomUUID(), clazz ?: UUID::class.java) as TDomainUUID2
        }

        //////////////////////////////////////////////////
        // Methods for creating fake UUID's for testing //
        //////////////////////////////////////////////////

        fun <TDomainUUID2 : IUUID2> createFakeUUID2(nullableId: Int?, nullableClazz: Class<*>?): UUID2<TDomainUUID2> {
            val id: Int = nullableId ?: 1
            val clazz = nullableClazz ?: UUID::class.java

            @Suppress("UNCHECKED_CAST")
            return createFakeUUID2(id, clazz) as UUID2<TDomainUUID2>
        }

        private fun createFakeUUID2(id: Int, clazz: Class<out Any>) : UUID2<IUUID2> {
            val idPaddedWith11LeadingZeroes = String.format("%011d", id)
            val uuid = UUID.fromString("00000000-0000-0000-0000-$idPaddedWith11LeadingZeroes")

            return UUID2(
                uuid ?: throw IllegalArgumentException("uuid cannot be null"),
                clazz
            )
        }

        /////////////////////////////////
        // UUID2 Type String interface //
        /////////////////////////////////

        fun calcUUID2TypeStr(clazz: Class<*>?): String {
            clazz ?: return "UUID"

            // Build the UUID2 Type string -> ie: `Model.DomainInfo.BookInfo`
            // - Gets all Class names of all superClasses for this clazz.
            // - Climbs the Class Inheritance hierarchy for the clazz.
            // - *NOT* the Class path (org.elegantobjects.jpages.App2.domain.book.BookInfo`
            val superClassNames: MutableList<String> = ArrayList()
            var curClazz = clazz
            while (curClazz?.simpleName != "Object") {
                superClassNames.add(curClazz?.simpleName.toString())
                curClazz = curClazz?.superclass
            }

            // Build a Class Inheritance Path from each concrete Class name
            val uuid2TypeStr = StringBuilder()
            for (i in superClassNames.indices.reversed()) {
                uuid2TypeStr.append(
                    getLastSegmentOfTypeStrPath(superClassNames[i])
                )
                if (i != 0) {
                    uuid2TypeStr.append(".")
                }
            }

            return uuid2TypeStr.toString()
        }

        private fun getLastSegmentOfTypeStrPath(classPath: String): String {
            val segments = classPath.split("\\.".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            return if (segments.size == 1) {
                classPath
            } else segments[segments.size - 1]
        }
    }
}