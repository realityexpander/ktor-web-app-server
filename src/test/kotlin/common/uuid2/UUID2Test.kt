package common.uuid2

import com.google.gson.GsonBuilder
import common.uuid2.UUID2.Companion.fromUUID
import common.uuid2.UUID2.Companion.fromUUID2String
import common.uuid2.UUID2.Companion.fromUUIDString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.*

/**
 * UUID2Test - Unit tests for UUID2 class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 - Kotlin version
 */

open class Role(
    @Transient
    open val id: UUID2<*> = UUID2.createFakeUUID2(1, IUUID2::class.java)
)

class User(
    override val id: UUID2<User>,
    val name: String,
    val bookIdToNumAcceptedMap: MutableMap<UUID2<Book>, Long> = mutableMapOf()
) : Role(id), IUUID2 {
    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }
}

class Book(
    override val id: UUID2<Book>,
    val name: String,
) : Role(id), IUUID2 {
    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }
}

class Library(
    override val id: UUID2<Library>,
    val name: String,
) : Role(id), IUUID2 {
    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }
}

class UUID2Test {

    @BeforeEach
    fun setUp() {
        // no-op - not every test requires setup
    }

    @Test
    fun `Deserialized UUID2 string is Correct`() {
        // • ARRANGE
        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)
        val expectedUUID2Str = "UUID2:Role.Book@00000000-0000-0000-0000-000000001200"

        // • ACT
        val book1200Uuid2Str: String = book1200Id.toString()

        // • ASSERT
        assertEquals(
            expectedUUID2Str,
            book1200Uuid2Str,
            "UUID2 String not serialized correctly",
        )
    }

    @Test
    fun `Create new UUID2 from UUID2 string are Equal`() {
        // • ARRANGE
        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)
        val book1200UUID2Str: String = book1200Id.toString()

        // • ACT
        val book1200aId: UUID2<Book> = book1200UUID2Str.fromUUID2String()

        // • ASSERT
        assertEquals(book1200Id, book1200aId)
    }

    @Test
    fun `Create new UUID2 from another UUID2 are Equal`() {
        // • ARRANGE
        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)

        // • ACT
        val book1200aId: UUID2<Book> = UUID2(book1200Id)

        // • ASSERT
        assertEquals(book1200Id, book1200aId)
    }

    @Test
    fun `UUID2 values with equal UUIDs using onlyUUIDEquals are Equal`() {
        // • ARRANGE
        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)
        val user1200Id: UUID2<User> = UUID2.createFakeUUID2(1200, User::class.java)
        val user9999Id: UUID2<User> = UUID2.createFakeUUID2(9999, User::class.java)

        // • ACT
        val isEqual: Boolean = book1200Id.isOnlyUUIDEqual(user1200Id)
        val isNotEqual: Boolean = book1200Id.isOnlyUUIDEqual(user9999Id)

        // • ASSERT
        assertTrue(isEqual)
        assertFalse(isNotEqual)
    }

    @Test
    fun `Equal UUID2 values are Equal`() {
        // • ARRANGE
        val book9999Id: UUID2<Book> = UUID2.createFakeUUID2(9999, Book::class.java)
        val book9999Ida: UUID2<Book> = UUID2.createFakeUUID2(9999, Book::class.java)

        // • ACT
        assertEquals(book9999Id, book9999Ida)
    }

    @Test
    fun `Parse UUID2 String with fromUUID2String() using invalid UUID2 string throws IllegalArgumentException is Success`() {
        // • ARRANGE
        val invalidUUID2Str = "UUID2:Role.Book_00000000-0000-0000-0000-000000001200" // missing `@`

        // • ACT
        try {
            val book1200aId: UUID2<Book> = invalidUUID2Str.fromUUID2String()
            fail(Exception("Expected IllegalArgumentException"))
        } catch (e: IllegalArgumentException) {
            // • ASSERT
            assertTrue(true)
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    fun `Parse UUID2 String with fromUUID2String() using invalid UUID throws IllegalArgumentException is Success`() {
        // • ARRANGE
        val invalidUUID2Str = "UUID2:Role.Book@00000000-0000-0000-0000-000000000000000000001200" // UUID is too long

        // • ACT
        try {
            val book1200aId: UUID2<Book> = invalidUUID2Str.fromUUID2String()

            // This is only here to satisfy the compiler warnings.  It should never be reached.
            System.err.printf("SHOULD NEVER SEE THIS - book1200aId=%s", book1200aId)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // • ASSERT
            assertTrue(true)
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    fun `Parse UUID2 String with fromUUID2() is Success`() {
        // • ARRANGE
        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)
        val book1200IdStr: String = book1200Id.toString()

        // • ACT
        val book1200aId: UUID2<Book> = book1200IdStr.fromUUID2String()

        // • ASSERT
        assertEquals(book1200Id, book1200aId)
    }

    @Test
    fun `Parse UUID2 String with fromUUID2() using invalid UUID2 string throws IllegalArgumentException`() {
        // • ARRANGE
        val invalidUUID2Str = "UUID2:Role.Book_00000000-0000-0000-0000-000000001200" // missing `@`

        // • ACT
        try {
            val book1200aId: UUID2<Book> = invalidUUID2Str.fromUUID2String()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // • ASSERT
            assertTrue(true)
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    fun `Parse UUID2 String with fromUUID() is Success`() {
        // • ARRANGE
        val book1200uuid: UUID = UUID2.createFakeUUID2<Book>(1200, Book::class.java).uuid()

        // • ACT
        val book1200aId: UUID2<Book> = book1200uuid.fromUUID()

        // • ASSERT
        assertEquals(book1200uuid,book1200aId.uuid())
    }

    @Test
    fun `Parse UUID2 String with fromUUID() using invalid UUID2 string throws IllegalArgumentException`() {
        // • ARRANGE
        val invalidUUID2Str = "UUID2:Role.Book_00000000-0000-0000-0000-000000001200" // missing `@`

        // • ACT
        try {
            val book1200aId: UUID2<Book> = invalidUUID2Str.fromUUID2String()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // • ASSERT
            assertTrue(true)
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    fun `Parse UUID2 String with fromUUIDString() is Success`() {
        // • ARRANGE
        val book1200uuid: UUID = UUID2.createFakeUUID2<Book>(1200, Book::class.java).uuid()
        val book1200uuidStr: String = book1200uuid.toString()

        // • ACT
        val book1200aUuid: UUID = book1200uuidStr.fromUUIDString<Book>().uuid()

        // • ASSERT
        assertEquals(book1200uuid, book1200aUuid)
    }

    @Test
    fun `Parse UUID2 String with fromUUIDString() using invalid UUID2 string throws IllegalArgumentException`() {
        // • ARRANGE
        val invalidUUIDStr = "00000000-0000-0000-0000-000000000000000001200" // UUID too long

        // • ACT
        try {
            val book1200aId: UUID2<Book> = invalidUUIDStr.fromUUIDString()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // • ASSERT
            assertTrue(true)
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    fun `Deserializing a Role JSON containing a mutableMap using Gson Uuid2HashMapJsonDeserializer is Success`() {
        // • ARRANGE
        val book1200: Book = Book(UUID2.createFakeUUID2(1200, Book::class.java), "The Hobbit")
        val book1201: Book = Book(UUID2.createFakeUUID2(1201, Book::class.java), "The Fellowship of the Ring")
        val user01 = User(
            UUID2.createFakeUUID2(1, User::class.java),
            "Bilbo Baggins",
            mutableMapOf<UUID2<Book>, Long>().apply {
                put(book1200.id, 1L)
                put(book1201.id, 2L)
            }
        )

        // create a Gson instance with the Uuid2MapJsonDeserializer registered
        val gson = GsonBuilder()
                .registerTypeAdapter(MutableMap::class.java, UUID2.Uuid2MapJsonDeserializer())
                .setPrettyPrinting()
                .create()
        val user01Json: String = gson.toJson(user01)
        val book1200Json: String = gson.toJson(book1200)

        // • ACT
        val user01a: User = gson.fromJson(user01Json, User::class.java)
        val book1200a: Book = gson.fromJson(book1200Json, Book::class.java)

        // • ASSERT
        assertEquals(user01a.bookIdToNumAcceptedMap.containsKey(book1200.id), true)
        assertEquals(user01a.bookIdToNumAcceptedMap.containsKey(book1201.id), true)
        assertEquals(user01a.bookIdToNumAcceptedMap[book1200.id], 1L)
        assertEquals(user01a.bookIdToNumAcceptedMap[book1201.id], 2L)
        assertEquals(book1200a.id, book1200.id)
    }

    // Represents an unknown UUID2 type
    class UnknownUUID2TypeEntity(val id: UUID2<*>) : IUUID2 {
        override fun uuid2TypeStr(): String {
            return UUID2.calcUUID2TypeStr(this.javaClass)
        }
    }

    @Test
    fun `Deserializing a Role JSON containing an unknown UUID2 type using Gson Uuid2HashMapJsonDeserializer throws RuntimeException`() {
        // • ARRANGE
        val unknownUUID2TypeEntity = UnknownUUID2TypeEntity(UUID2.createFakeUUID2(9999, UnknownUUID2TypeEntity::class.java))
        val user01 = User(
            UUID2.createFakeUUID2(1, User::class.java),
            "Bilbo Baggins",
            mutableMapOf<UUID2<Book>, Long>().apply {
                @Suppress("UNCHECKED_CAST")
                put(unknownUUID2TypeEntity.id as UUID2<Book>, 1L) // cast to UUID2<Book> to make the compiler happy
            }
        )

        // create a Gson instance with the Uuid2MapJsonDeserializer registered
        println("▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼ Warning is expected for this test. ▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼")
        val gson = GsonBuilder()
                .registerTypeAdapter(MutableMap::class.java, UUID2.Uuid2MapJsonDeserializer())
                .setPrettyPrinting()
                .create()
        val user01Json: String = gson.toJson(user01)

        assertThrows(RuntimeException::class.java) {
            // • ACT
            val user01a: User = gson.fromJson(user01Json, User::class.java)
        }

    }

}