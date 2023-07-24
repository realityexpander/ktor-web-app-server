@file:Suppress("StructuralWrap")

package common.uuid2

import com.google.gson.*
import common.uuid2.UUID2.Companion.fromUUID2StrToUUID2
import domain.account.Account
import domain.book.Book
import domain.library.Library
import domain.library.PrivateLibrary
import domain.user.User
import io.ktor.util.reflect.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
 *  * Includes Gson Serializers for of type-safe **`Map`s** and **`Array`s** for **`UUID2`s**.
 *  * **`UUID2`** is immutable.
 *
 *  *  **`IUUID2`** is a marker interface for classes that will be used with **`UUID2`**.
 *  *  All Domain objects must be marked with the **`IUUID2`** marker interface to be used with
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
 * **UUID2 String Format example:**
 * ```
 * ・UUID2:Role.User@00000000-0000-0000-0000-000000000001
 * ・▲▲▲▲▲⸻Always prefixed with `UUID2`
 * ・     ▲⸻ `:` divides between Prefix and Type
 * ・      ▲▲▲▲▲▲▲▲▲⸻ UUID2Type
 * ・               ▲⸻ `@` divides the UUID2Type and UUID Value
 * ・                ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲⸻ UUID Value
 * ```
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin Conversion
 */

//@Serializable  // todo use kotlinx serialization instead of gson
@Serializable(with = UUID2Serializer::class)
open class UUID2<TUUID2 : IUUID2> : IUUID2 {

    @Serializable(with = UUIDSerializer::class)
    private val uuid: UUID

    // NOT final due to need for it to be set for creating objects via JSON deserialization. :(
    // todo - is there a way around this? Maybe reflection? Test Use Val?
    var uuid2Type: String // Class Inheritance Path of the object the UUID refers to. '.' separated.
        private set(value) {
            field = getNormalizedUuid2TypeString(value)
        }

    private val uuid2TypeStr: String
        get() {
            return uuid2Type
        }

    constructor(uuid2: UUID2<TUUID2>, uuid2TypeStr: String? = uuid2.uuid2TypeStr()) {
        uuid = (uuid2 as UUID2<*>).uuid()
        uuid2Type =
            if (uuid2TypeStr != null) {
                getNormalizedUuid2TypeString(uuid2TypeStr)
            } else {
                "UUID" // Default to untyped UUID
            }
    }
    constructor(uuid2: UUID2<TUUID2>, clazz: Class<TUUID2>?) : this(uuid2, calcUUID2TypeStr(clazz))
    constructor(uuid: UUID, uuid2TypeStr: String = "UUID") {
        this.uuid = uuid
        uuid2Type = getNormalizedUuid2TypeString(uuid2TypeStr)
    }
    constructor(uuid: UUID, clazz: Class<TUUID2>?) : this(uuid, calcUUID2TypeStr(clazz))
    constructor(clazz: Class<TUUID2>) : this(UUID.randomUUID(), clazz)
    constructor(uuid2TypeStr: String = "UUID") : this(UUID.randomUUID(), uuid2TypeStr)
    constructor() : this(UUID.randomUUID(), "UUID")

    ///////////////////////
    // Published Methods //
    ///////////////////////

    fun uuid(): UUID {
        return uuid
    }

    override fun uuid2TypeStr(): String {
        return uuid2TypeStr
    }

    override fun toString(): String {
        return "UUID2:$uuid2Type@$uuid"
    }

    fun isOnlyUUIDEqual(other: UUID2<*>): Boolean {
        return other.uuid() == uuid()
    }

    fun isMatchingUUID2Type(otherUUID2: UUID2<*>): Boolean {
        return uuid2TypeStr() == otherUUID2.uuid2TypeStr()
    }

    fun String.isMatchingUUID2TypeStr(): Boolean {
        val otherUUID2TypeStr = this
        return uuid2TypeStr() == otherUUID2TypeStr
    }

    override fun hashCode(): Int {
        return uuid().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return if (other !is UUID2<*>) false else other.uuid() == uuid() && uuid2TypeStr() == other.uuid2TypeStr()
    }

    ////////////////////////////////
    // Converters                 //
    ////////////////////////////////

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

    // Note: Should only be used when importing JSON
    // todo - is there a better way to do this in kotlin?  // this doesn't seem to be needed for kotlinx serialization
    @Suppress("FunctionName") // for leading underscore
    fun _setUUID2TypeStr(uuid2TypeStr: String?): Boolean {
        uuid2Type = getNormalizedUuid2TypeString(uuid2TypeStr)
        return true // always return `true` instead of a `void` return type
    }


    ////////////////////////////////
    // JSON Serialization Helpers //
    ////////////////////////////////

    // for Gson
    class Uuid2ArrayListJsonSerializer: JsonSerializer<ArrayList<*>?> {
        override fun serialize(
            src: ArrayList<*>?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            val uuid2JsonArray = JsonArray()

            try {
                src?.forEach { uuid2 ->
//                    uuid2JsonArray.add(uuid2.toString())
                    uuid2JsonArray.add(
                        uuid2.toString()
                            .removePrefix("\"")  // remove any leading quotes (added by JsonPrimitive)
                            .removeSuffix("\"")  // remove any trailing quotes (added by JsonPrimitive)
                    )
                }
            } catch (e: Exception) {
                throw RuntimeException("Error serializing UUID2 ArrayList JSON", e)
            }

            return uuid2JsonArray
        }
    }

    // for Gson
    class Uuid2ArrayListJsonDeserializer: JsonDeserializer<ArrayList<*>?> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            jsonDeserializationContext: JsonDeserializationContext?
        ): ArrayList<*> {

            val uuid2ArrayList: ArrayList<Any> = arrayListOf()
            try {

                val uuid2ArrayListJson =
                    json?.asJsonArray

                // Rebuild the UUID2<*> to Entity
                uuid2ArrayListJson?.forEach { entity ->
                    val uuid2 = entity.asString.fromUUID2StrToUUID2()
                    uuid2ArrayList.add(uuid2)
                }

            } catch (e: Exception) {
                throw RuntimeException("Error deserializing UUID2 ArrayList JSON", e)
            }

            return uuid2ArrayList
        }
    }

    // for Gson
    class Uuid2JsonSerializer: JsonSerializer<UUID2<*>?> {
        override fun serialize(
            src: UUID2<*>?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(src.toString())
        }
    }

    // For Gson
    class Uuid2JsonDeserializer: JsonDeserializer<UUID2<*>?> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            jsonDeserializationContext: JsonDeserializationContext?
        ): UUID2<*> {
            return (json?.asString ?: "").fromUUID2StrToUUID2()
        }
    }

    // For Gson
    // Note: Deserializes all JSON Numbers to Longs for all Entity Number JSON values.
    // - For consistent number deserialization due to GSON defaulting to convert numbers to Doubles.
    class Uuid2MapJsonDeserializer: JsonDeserializer<MutableMap<UUID2<*>, *>?> {

        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            jsonDeserializationContext: JsonDeserializationContext?
        ): MutableMap<UUID2<*>, *> {

            val uuid2ToEntityMap: MutableMap<UUID2<*>, Any> = mutableMapOf()
            try {

                val uuid2MapJsonEntrySet =
                    json?.asJsonObject?.entrySet()

                // Rebuild the UUID2<*> to Entity
                uuid2MapJsonEntrySet?.forEach { entry ->
                    val uuid2Key = entry.key.fromUUID2StrToUUID2()
                    val entity = entry.value
                        ?: throw RuntimeException("Uuid2HashMapGsonDeserializer.deserialize(): entity is null, uuid2Key=$uuid2Key")

                    // Convert any JSON Numbers to Longs (instead of gson default Doubles)
                    @Suppress("IMPLICIT_CAST_TO_ANY")
                    val entityValue =
                        when(true) {
                            (entity.isJsonPrimitive && entity.asJsonPrimitive.isNumber) ->
                                entity.asLong
                            entity.isJsonArray -> {
                                val jsonArray = entity.asJsonArray
                                val jsonArraySize = jsonArray.size()

                                val mutableListValues = mutableListOf<Any>()
                                for (i in 0 until jsonArraySize) {
                                    val jsonArrayValue = jsonArray[i]
                                    if (jsonArrayValue.isJsonPrimitive && jsonArrayValue.asJsonPrimitive.isNumber) {
                                        mutableListValues.add(jsonArrayValue.asLong)
                                    } else {
                                        mutableListValues.add(jsonArrayValue)
                                    }
                                }

                                mutableListValues
                            }
                            else -> entity
                        }

                    uuid2ToEntityMap[uuid2Key] = entityValue
                }
            } catch (e: IllegalArgumentException) {
                System.err.println(e.message)
                throw RuntimeException(e)
            }

            return uuid2ToEntityMap
        }
    }

    companion object {

        @Throws(IllegalArgumentException::class)
        fun <TUUID2: IUUID2> String.fromUUID2String(): UUID2<TUUID2> {
            val uuid2FormattedString = this.trim()
            // format example:
            //
            // UUID2:Role.User@00000000-0000-0000-0000-000000000001
            // ▲▲▲▲▲-- Always prefixed with `UUID2`
            //      ▲-- `:` divides between Prefix and UUID2Type
            //       ▲▲▲▲▲▲▲▲▲-- UUID2Type
            //                ▲-- `@` divides the UUID2Type and UUID Value
            //                 ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲-- UUID Value

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
            val uuidStr = segments[1]          // ie: 00000000-0000-0000-0000-000000000001

            return UUID2(UUID.fromString(uuidStr), uuid2TypeStr)
        }

        @Throws(RuntimeException::class)
        fun String.fromUUID2StrToUUID2(): UUID2<*> {
            val uuid2Str = this
            val uuid2 = uuid2Str.fromUUID2String<IUUID2>()
            val typeStr = uuid2.uuid2TypeStr()

            fun lastPathItemOfUUID2TypeStr(uuidTypeStr: String): String {
                val segments = uuidTypeStr.split(".")
                return segments[segments.size - 1]
            }

            // Check if it's in the White-listed UUID2 types
            // todo make this a config option?
            when(typeStr) {
                "Role.Book" ->           return uuid2Str.fromUUID2String<Book>()
                "Role.User" ->           return uuid2Str.fromUUID2String<User>()
                "Role.Library" ->        return uuid2Str.fromUUID2String<Library>()
                "Role.PrivateLibrary" -> return uuid2Str.fromUUID2String<PrivateLibrary>()
                "Role.Account" ->        return uuid2Str.fromUUID2String<Account>()
            }

            ///////////////////////////////////////////////////////////////////
            // The UUID2Type is NOT in the White-listed UUID2 types.         //
            // - Attempt to use SLOW REFLECTION to find the correct type.    //
            ///////////////////////////////////////////////////////////////////

            System.err.println("WARNING: UUID2Type is NOT in the White-listed UUID2 types. " +
                    "Attempting to use SLOW REFLECTION to find the correct type. typeStr=$typeStr," +
                    " uuid2Str=$uuid2Str," +
                    "Updated the White-listed UUID2 types in `UUID2.kt` to improve performance."
            )

            // Find all the implementations of IUUID2
            val iuuid2SubTypeClazzList =
                Reflections("domain").getSubTypesOf(IUUID2::class.java)
            val uuid2TypeClazz = iuuid2SubTypeClazzList.find { clazz ->
                    clazz.simpleName == lastPathItemOfUUID2TypeStr(typeStr)
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
                    val firstUUID2 = firstUuid2Str.fromUUID2String<IUUID2>()
                    val secondUUID2 = secondUuid2Str.fromUUID2String<IUUID2>()
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

        fun <TUUID2 : IUUID2> UUID2<*>.toUUID2WithUUID2TypeOf(kClazz: KClass<TUUID2>): UUID2<TUUID2> {
            return UUID2(this.uuid, kClazz.java)
        }

        fun <TUUID2 : IUUID2> UUID.fromUUID(): UUID2<TUUID2> {
            return UUID2(this)
        }

        fun <TUUID2 : IUUID2> String.fromUUIDString(): UUID2<TUUID2> {
            return UUID2(UUID.fromString(this))
        }

        ////////////////////////////////
        // Generators                 //
        ////////////////////////////////

        fun <TUUID2 : IUUID2> randomUUID2(): UUID2<TUUID2> {
            return UUID2(UUID.randomUUID())
        }

        fun <TUUID2 : IUUID2> randomUUID2(clazz: Class<TUUID2>?): UUID2<TUUID2> {
            return UUID2(UUID.randomUUID(), clazz)
        }

        fun <TUUID2 : IUUID2> randomUUID2(kClazz: KClass<TUUID2>): UUID2<TUUID2> {
            return UUID2(UUID.randomUUID(), kClazz.java)
        }

        ////////////////////////////////////
        // Create fake UUID's for testing //
        ////////////////////////////////////

        @Suppress("UNCHECKED_CAST")
        fun <TUUID2 : IUUID2> createFakeUUID2(nullableId: Int?, nullableClazz: Class<TUUID2>?): UUID2<TUUID2> {
            val id: Int = nullableId ?: 1
            if(id > 999999999) throw IllegalArgumentException("id cannot be greater than 999999999")
            val clazz: Class<TUUID2> = nullableClazz ?: IUUID2::class.java as Class<TUUID2>

            val idPaddedWith11LeadingZeroes = String.format("%011d", id)
            val uuid = UUID.fromString("00000000-0000-0000-0000-$idPaddedWith11LeadingZeroes")
            uuid ?: throw IllegalArgumentException("uuid cannot be null")

            return UUID2(uuid, clazz)
        }

        @Suppress("UNCHECKED_CAST")
        fun <TUUID2 : IUUID2> createFakeUUID2(nullableId: Int?, nullableKClazz: KClass<TUUID2>?): UUID2<TUUID2> {
            val id: Int = nullableId ?: 1
            if(id > 999999999) throw IllegalArgumentException("id cannot be greater than 999999999")
            val clazz: Class<TUUID2> = nullableKClazz?.java ?: IUUID2::class.java as Class<TUUID2>

            val idPaddedWith11LeadingZeroes = String.format("%011d", id)
            val uuid = UUID.fromString("00000000-0000-0000-0000-$idPaddedWith11LeadingZeroes")
            uuid ?: throw IllegalArgumentException("uuid cannot be null")

            return UUID2(uuid, clazz)
        }

        /////////////////////////////////
        // UUID2Type String interface  //
        /////////////////////////////////

        // todo use KClass instead of Class
        fun <TUUID2 : IUUID2> calcUUID2TypeStr(clazz: Class<in TUUID2>?): String {
//        fun <TUUID2 : IUUID2> calcUUID2TypeStr(clazz: KClass<in TUUID2>?): String {
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
//                curClazz = curClazz?.supertypes?.firstOrNull()?.classifier as KClass<in TUUID2>
//                curClazz = curClazz?.superclasses?.firstOrNull() as KClass<in TUUID2>
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

object UUID2Serializer : KSerializer<UUID2<*>> {
    override val descriptor = PrimitiveSerialDescriptor("UUID2", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID2<*> {
        return decoder.decodeString().fromUUID2StrToUUID2()
    }

    override fun serialize(encoder: Encoder, value: UUID2<*>) {
        encoder.encodeString(value.toString())
    }
}

fun UUID2<*>.isMatchingUUID2Type(): Boolean {
    val otherUUID2 = this
    return uuid2TypeStr() == otherUUID2.uuid2TypeStr()
}