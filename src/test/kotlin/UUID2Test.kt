import common.uuid2.IUUID2
import common.uuid2.UUID2
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

/**
 * UUID2Test - Unit tests for UUID2 class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

open class Role(
    open val id: IUUID2 = UUID2.createFakeUUID2<IUUID2>(1, UUID::class.java)
)

data class User(
    override val id: UUID2<User>,
) : Role(id), IUUID2 {
    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }
}

data class Book(
    override val id: UUID2<Book>,
) : Role(id), IUUID2 {
    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }
}

data class TestObjects(
    val uuid2ToEntityMap: UUID2.HashMap<UUID2<Book>, UUID2<User>>,
    val user01: UUID2<User>,
    val user02: UUID2<User>,
    val book1200: UUID2<Book>,
    val book1300: UUID2<Book>
)

@Suppress("JUnitMalformedDeclaration")
class UUID2Test {
//    var ctx: Context? = null

    @Before
    fun setUp() {
        // no-op - not every test requires setup
    }

    private fun setUpUuid2HashMapTest(): TestObjects {
        val testObjects =  TestObjects(
//             ctx = LibraryAppTest.setupDefaultTestContext()
            uuid2ToEntityMap = UUID2.HashMap<UUID2<Book>, UUID2<User>>(),
            book1200 = UUID2.createFakeUUID2<Book>(1200, Book::class.java),
            book1300 = UUID2.createFakeUUID2<Book>(1300, Book::class.java),
            user01 = UUID2.createFakeUUID2<User>(1, User::class.java),
            user02 = UUID2.createFakeUUID2<User>(2, User::class.java)
        )

        testObjects.uuid2ToEntityMap.put(testObjects.book1200, testObjects.user01)
        testObjects.uuid2ToEntityMap.put(testObjects.book1300, testObjects.user02)

        return testObjects
    }

    @Test
    fun `Deserialized UUID2 string is Correct`() {
        // • ARRANGE
//        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)
        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)
        val expectedUUID2Str = "UUID2:Role.Book@00000000-0000-0000-0000-000000001200"

        // • ACT
        val book1200Uuid2Str: String = book1200Id.toString()

        // • ASSERT
        Assert.assertEquals(
            "UUID2 String not serialized correctly",
            book1200Uuid2Str, expectedUUID2Str
        )
    }

    @Test
    fun `Create new UUID2 from UUID2 string are Equal`() {
        // • ARRANGE
        val book1200Id: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)
        val book1200UUID2Str: String = book1200Id.toString()

        // • ACT
        val book1200aId: UUID2<Book> = UUID2.fromUUID2String(book1200UUID2Str)

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
        Assert.assertTrue(isEqual)
        Assert.assertFalse(isNotEqual)
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
    fun `Get UUID2HashMap item is Success`() {
        // • ARRANGE
        val testObjects = setUpUuid2HashMapTest()

        // • ACT
        val user: UUID2<User>? = testObjects.uuid2ToEntityMap[testObjects.book1200]

        // • ASSERT
        user ?: Assert.fail()
//        ctx.log.d(this, "simple retrieval, user=$user")
        Assert.assertNotNull(user)
    }

    @Test
    fun `Get UUID2HashMap item using new UUID2 is Success`() {
        // • ARRANGE
        val testObjects = setUpUuid2HashMapTest()
        val user: UUID2<User>? = testObjects.uuid2ToEntityMap[testObjects.book1200]
        user ?: Assert.fail()
        val book1a: UUID2<Book> = UUID2.createFakeUUID2(1200, Book::class.java)

        // • ACT
        val user2: UUID2<User>? = testObjects.uuid2ToEntityMap[book1a]

        // • ASSERT
//        ctx.log.d(this, "retrieved using new id, user=$user")
        Assert.assertNotNull(user2)
        assertEquals(user2, user)
    }

    @Test
    fun `Remove UUID2HashMap item using new UUID2 is Success`() {
        // • ARRANGE
        val testObjects = setUpUuid2HashMapTest()

        // • ACT
        testObjects.uuid2ToEntityMap.remove(testObjects.book1200)

        // • ASSERT
        val user: UUID2<User>? = testObjects.uuid2ToEntityMap[testObjects.book1200]
//        ctx.log.d(this, "after removal, user=$user")
        Assert.assertNull(user)

        // check keySet count
        val keySet: Set<UUID2<Book>> = testObjects.uuid2ToEntityMap.keySet()
        Assert.assertEquals(1, keySet.size.toLong())
    }

    @Test
    fun `Put UUID2HashMap item twice only upserts single item is Success`() {
        // • ARRANGE
        val testObjects = setUpUuid2HashMapTest()
        testObjects.uuid2ToEntityMap.remove(testObjects.book1200)

        // • ACT
        testObjects.uuid2ToEntityMap.put(testObjects.book1200, testObjects.user01)
        // put it in again (should replace. not duplicate)
        testObjects.uuid2ToEntityMap.put(testObjects.book1200, testObjects.user01)

        // • ASSERT
        // check keySet count
        val keySet: Set<UUID2<Book>> = testObjects.uuid2ToEntityMap.keySet()
        Assert.assertEquals(2, keySet.size.toLong())

        // check values count
        val values: Collection<UUID2<User>> = testObjects.uuid2ToEntityMap.values()
        Assert.assertEquals(2, values.size.toLong())

        // check entrySet count
        val entrySet: Set<Map.Entry<UUID2<Book>, UUID2<User>?>> = testObjects.uuid2ToEntityMap.entrySet()
        Assert.assertEquals(2, entrySet.size.toLong())

        // check containsKey
        Assert.assertTrue("containsKey(book1) failed", testObjects.uuid2ToEntityMap.containsKey(testObjects.book1200))
        Assert.assertTrue("containsKey(book2) failed", testObjects.uuid2ToEntityMap.containsKey(testObjects.book1300))
        Assert.assertFalse(
            "containsKey(Book 1400) should fail",
            testObjects.uuid2ToEntityMap.containsKey(UUID2.createFakeUUID2(1400, Book::class.java))
        )
    }

    @Test
    fun `Invalid UUID2 String throws IllegalArgumentException is Success`() {
        // • ARRANGE
        val invalidUUID2Str = "UUID2:Role.Book_00000000-0000-0000-0000-000000001200" // missing `@`

        // • ACT
        try {
            val book1200aId: UUID2<Book> = UUID2.fromUUID2String<Book>(invalidUUID2Str)
            Assert.fail("Expected IllegalArgumentException")

            // This is only here to satisfy the compiler warnings.  It should never be reached.
            System.err.printf("SHOULD NEVER SEE THIS - book1200aId=%s", book1200aId)
        } catch (e: IllegalArgumentException) {
            // • ASSERT
            Assert.assertTrue(true)
        } catch (e: Exception) {
            Assert.fail(e.message)
        }
    }
}