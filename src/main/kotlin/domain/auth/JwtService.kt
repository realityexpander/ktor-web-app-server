package com.realityexpander.domain.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.*

class JwtService(
    private val secret: String,
    private val issuer: String,
    private val audience: String
) {

    val verifier: JWTVerifier = JWT.require(Algorithm.HMAC512(secret))
        .withIssuer(issuer)
        .build()

    fun generateLoginAuthToken(user: UserAuthEntity, clientIpAddress: String): String {
        val expiresAtTimeMillis =
            System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7 // 7 days
        val expiresAtDate = Date(expiresAtTimeMillis)

        val jwt = JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience("$audience/api/login")
//            .withClaim("id", user.id)
            .withClaim("id", user.id.toString())
            .withClaim("email", user.email)
            .withClaim("clientIpAddress", clientIpAddress)
            .withExpiresAt(expiresAtDate)
            .sign(Algorithm.HMAC512(secret))

        return jwt
    }

    fun generatePasswordResetToken(user: UserAuthEntity): String {
        val expiresAtTimeMillis =
            System.currentTimeMillis() + 1000 * 60 * 60 * 1 // 1 hour
        val expiresAtDate = Date(expiresAtTimeMillis)

        val jwt = JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience("$audience/api/password-reset")
            .withClaim("type", "passwordReset")
//            .withClaim("id", user.id)
            .withClaim("id", user.id.toString())
            .withClaim("email", user.email)
            .withExpiresAt(expiresAtDate)
            .sign(Algorithm.HMAC512(secret))

        return jwt
    }

    fun verify(token: String): DecodedJWT {
        return verifier.verify(token)
    }
}
