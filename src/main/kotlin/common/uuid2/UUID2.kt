@file:Suppress("StructuralWrap")

package common.uuid2

import com.google.gson.InstanceCreator
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import io.ktor.util.reflect.*
import org.bson.json.JsonParseException
import org.reflections.Reflections
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.*

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

open class UUID2<TUUID2 : IUUID2> : IUUID2 {
    private val uuid: UUID

    // NOT final due to need for it to be set for creating objects via JSON deserialization. :( // todo - is there a way around this? Maybe reflection?
    private var _uuid2Type: String // Class Inheritance Path of the object the UUID refers to. '.' separated.
        private set(value) {
            field = getNormalizedUuid2TypeString(value)
        }
    val uuid2TypeStr: String  // todo change interface to use this getter instead of overridden method
        get() {
            return _uuid2Type
        }

    constructor(uuid2: UUID2<TUUID2>, uuid2TypeStr: String? = uuid2.uuid2TypeStr()) {
        uuid = (uuid2 as UUID2<*>).uuid()
        _uuid2Type = if (uuid2TypeStr != null) {
            getNormalizedUuid2TypeString(uuid2TypeStr)
        } else {
            "UUID" // Default to untyped UUID
        }
    }
    constructor(uuid2: UUID2<TUUID2>, clazz: Class<TUUID2>?) : this(uuid2, calcUUID2TypeStr(clazz))
    constructor(uuid: UUID, uuid2TypeStr: String = "UUID") {
        this.uuid = uuid
        _uuid2Type = getNormalizedUuid2TypeString(uuid2TypeStr)
    }
    constructor(uuid: UUID, clazz: Class<TUUID2>?) : this(uuid, calcUUID2TypeStr(clazz))
//    constructor(clazz: Class<TUUID2>) : this(UUID.randomUUID(), clazz)
//    constructor(uuid2TypeStr: String) : this(UUID.randomUUID(), uuid2TypeStr)
    constructor() : this(UUID.randomUUID(), "UUID")

    ////////////////////////////////
    // Published Getters          //
    ////////////////////////////////

    fun uuid(): UUID {
        return uuid
    }

    override fun uuid2TypeStr(): String {
        return uuid2TypeStr
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

    fun isMatchingUUID2Type(otherUUID2: UUID2<*>): Boolean {
        return uuid2TypeStr() == otherUUID2.uuid2TypeStr()
    }

    fun isMatchingUUID2TypeStr(otherUUID2TypeStr: String): Boolean {
        return uuid2TypeStr() == otherUUID2TypeStr
    }

    fun toUUID(): UUID {
        return UUID(uuid.mostSignificantBits, uuid.leastSignificantBits)
    }

    fun toDomainUUID2(): UUID2<IUUID2> {
        @Suppress("UNCHECKED_CAST")
        return UUID2(this, uuid2TypeStr()) as UUID2<IUUID2>
    }

    fun toUUID2(): UUID2<TUUID2> {
        return UUID2(this)
    }

    // Note: Should only be used when importing JSON
    @Suppress("FunctionName") // for leading underscore  // todo - is there a better way to do this in kotlin?
    fun _setUUID2TypeStr(uuid2TypeStr: String?): Boolean {
        _uuid2Type = getNormalizedUuid2TypeString(uuid2TypeStr)
        return true // always return `true` instead of a `void` return type
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

    class UUID2IUUID2InstanceCreator : InstanceCreator<UUID2<IUUID2>> {
        override fun createInstance(type: Type): UUID2<IUUID2> {
            // Create and return an instance of UUID2<IUUID2>
            return randomUUID2()
        }
    } // todo delete?

    // Note: Deserializes all JSON Numbers to Longs for all UUID2.HashMap Entity Number values.
    // - For consistent number deserialization bc GSON defaults to Doubles.
    class Uuid2MutableMapJsonDeserializer: JsonDeserializer<MutableMap<UUID2<*>, *>?> {

        @Throws(JsonParseException::class)
        override fun deserialize(
            json: com.google.gson.JsonElement?,
            typeOfT: Type?,
            jsonDeserializationContext: JsonDeserializationContext?
        ): MutableMap<UUID2<*>, *> {

            val uuid2ToUuidMap: MutableMap<UUID2<*>, Any> = mutableMapOf()
            try {

                // Rebuild the UUID2<*> to Entity
                json?.asJsonObject?.entrySet()?.forEach { entry ->
                    val uuid2Key = UUID2.fromUUID2StrToSameTypeUUID2(entry.key)
                    val entity = entry.value
                        ?: throw RuntimeException("Uuid2HashMapGsonDeserializer.deserialize(): entity is null, uuid2Key=$uuid2Key")

                    // Convert any Numbers to Longs
                    @Suppress("IMPLICIT_CAST_TO_ANY")
                    val entityValue = if (entity.isJsonPrimitive && entity.asJsonPrimitive.isNumber) {
                        entity.asLong
                    } else {
                        entity
                    }

                    uuid2ToUuidMap[uuid2Key] = entityValue
                }
            } catch (e: IllegalArgumentException) {
                System.err.println(e.message)
                throw RuntimeException(e)
            }

            return uuid2ToUuidMap
        }
    }

    companion object {

        @Throws(IllegalArgumentException::class)
        fun <TUUID2: IUUID2> fromUUID2String(uuid2FormattedString: String): UUID2<TUUID2> {
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

        @Throws(RuntimeException::class)
        fun fromUUID2StrToSameTypeUUID2(uuid2Str: String): UUID2<*> {
            val uuid2 = fromUUID2String<IUUID2>(uuid2Str)
            val typeStr = uuid2.uuid2TypeStr()

            fun lastUUID2TypeStrPathItem(uuidTypeStr: String): String {
                val segments = uuidTypeStr.split(".")
                return segments[segments.size - 1]
            }

//            // Check if it's in the White-listed UUID2 types // todo make this a config option?
//            // Create a UUID2 using generic type from the clazz
//            when(typeStr) {
//                "Role.Book" -> return fromUUID2String<Book>(uuid2Str)
//                "Role.User" -> return fromUUID2String<User>(uuid2Str)
//
//                // LEAVE FOR WHITE-LISTED UUID2 TYPES
//                //    "Role.Library" -> return fromUUID2String<Library>(uuid2Str)
//                //    "Role.PrivateLibrary" -> return fromUUID2String<PrivateLibrary>(uuid2Str)
//                //    "Role.Account" -> return fromUUID2String<Account>(uuid2Str)
//            }

            ///////////////////////////////////////////////////////////////////
            // The UUID2Type is NOT in the White-listed UUID2 types.         //
            // - Attempt to use SLOW REFLECTION to find the correct type.    //
            // - todo Maybe log a warning here?                              //
            ///////////////////////////////////////////////////////////////////

            // Find all the implementations of IUUID2
            val iuuid2SubTypeClazzList =
                Reflections("com.realityexpander").getSubTypesOf(IUUID2::class.java)
            val uuid2TypeClazz = iuuid2SubTypeClazzList.find { clazz ->
                    clazz.simpleName == lastUUID2TypeStrPathItem(typeStr)
                } ?: throw RuntimeException("Unknown UUID2 type: $typeStr")

            val uuid2DomainParameterizedType = object : ParameterizedType {
                override fun getRawType(): Type = UUID2::class.java
                override fun getOwnerType(): Type? = null
                override fun getActualTypeArguments(): Array<Type> = arrayOf(uuid2TypeClazz)
            }

            try {
                return createUUID2TypedInstance(uuid2, uuid2DomainParameterizedType)
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }

        @Throws(RuntimeException::class)
        private fun createUUID2TypedInstance(
            uuid2: UUID2<*>,
            types: ParameterizedType
        ): UUID2<*> {
            // Check the raw type from the parameterized type to ensure it is a UUID2
            val rawType = types.rawType as Class<*>  // should be `class common.uuid.UUID2`
            if(rawType != UUID2::class.java) {
                throw RuntimeException("Uuid2HashMapGsonDeserializer.deserialize(): rawType is not a UUID2: $rawType")
            }

            // Get the generic UUID2 type
            val uuid2DomainTypeClazz = types.actualTypeArguments[0] as Class<*>

            // find the constructor for the UUID2 & Clazz type params
            val uuid2Constructor = rawType.declaredConstructors.first {
                it.parameterTypes.contentEquals(arrayOf(UUID2::class.java, Class::class.java))
            } ?: throw RuntimeException("Unable to find UUID2(UUID, Class) constructor")

            return uuid2Constructor
                .newInstance(uuid2, uuid2DomainTypeClazz) as UUID2<*>
        }

        fun isMatchingUUID2TypeStr(firstUuid2Str: String?, secondUuid2Str: String?): Boolean {
            if (firstUuid2Str == null) return false // note: null checks are acceptable for static methods.

            return if (secondUuid2Str == null) false
            else
                try {
                    // Only need the `type string`, not the actual `type` of the UUID2.
                    val firstUUID2 = fromUUID2String<IUUID2>(firstUuid2Str)
                    val secondUUID2 = fromUUID2String<IUUID2>(secondUuid2Str)
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

        fun <TUUID2 : IUUID2> fromUUID2(id: UUID2<IUUID2>, clazz: Class<TUUID2>): UUID2<TUUID2> {
            return UUID2<TUUID2>(id.uuid, clazz)
        }

        fun <TUUID2 : IUUID2> fromUUID(uuid: UUID): UUID2<TUUID2> {
            return UUID2(uuid)
        }

        fun <TUUID2 : IUUID2> fromUUIDString(uuidStr: String): UUID2<TUUID2> {
            return UUID2(UUID.fromString(uuidStr))
        }

        ////////////////////////////////
        // Generators                 //
        ////////////////////////////////

        fun <TUUID2 : IUUID2> randomUUID2(): UUID2<TUUID2> {
            return UUID2<TUUID2>(UUID.randomUUID())
        }

        fun <TUUID2 : IUUID2> randomUUID2(clazz: Class<TUUID2>?): UUID2<TUUID2> {
            return UUID2<TUUID2>(UUID.randomUUID(), clazz)
        }

        ////////////////////////////////////
        // Create fake UUID's for testing //
        ////////////////////////////////////

        @Suppress("UNCHECKED_CAST")
        fun <TUUID2 : IUUID2> createFakeUUID2(nullableId: Int?, nullableClazz: Class<TUUID2>?): UUID2<TUUID2> {
            val id: Int = nullableId ?: 1
            val clazz: Class<TUUID2> = nullableClazz ?: IUUID2::class.java as Class<TUUID2>

            val idPaddedWith11LeadingZeroes = String.format("%011d", id)
            val uuid = UUID.fromString("00000000-0000-0000-0000-$idPaddedWith11LeadingZeroes")
            uuid ?: throw IllegalArgumentException("uuid cannot be null")

            return UUID2(uuid, clazz)
        }

        /////////////////////////////////
        // UUID2Type String interface  //
        /////////////////////////////////

        fun <TUUID2 : IUUID2> calcUUID2TypeStr(clazz: Class<in TUUID2>?): String {
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