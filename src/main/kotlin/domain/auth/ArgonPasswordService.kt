package com.realityexpander.domain.auth

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

// source: https://supertokens.com/blog/password-hashing-salting
// https://github.com/phxql/argon2-playground/blob/main/src/main/java/de/mkammerer/argon2playground/Main.java

class ArgonPasswordService(
    private val pepper: String
) {

    private val argon2: Argon2 = Argon2Factory.create(
        Argon2Factory.Argon2Types.ARGON2id,
        16, // bytes
        64 // bytes
    )

    fun getSaltedPepperedPasswordHash(password: String): String {
        val passwordPeppered = "$password.$pepper"

        // Generate hash
        val passwordCharArray = (passwordPeppered).toCharArray()
        val saltedPepperedPasswordHash: String = argon2.hash(
            3,          // Number of iterations
            64 * 1024,   // 64mb
            1,         // how many parallel threads to use
            passwordCharArray
        )

        return saltedPepperedPasswordHash
    }

    fun validatePassword(incomingPassword: String, userPasswordHash: String): Boolean {
        val incomingPasswordWithPepper = "$incomingPassword.$pepper"
        return argon2.verify(userPasswordHash, incomingPasswordWithPepper.toCharArray())
    }
}