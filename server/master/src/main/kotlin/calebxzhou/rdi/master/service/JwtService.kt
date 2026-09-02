package calebxzhou.rdi.master.service

import calebxzhou.rdi.master.CONF
import calebxzau.rdi.common.logging.Loggers
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import org.bson.types.ObjectId
import java.util.*

/**
 * Utility responsible for issuing and verifying JWT tokens.
 */
object JwtService {
    private val lgr by Loggers
    private val config get() = CONF.jwt

    private val algorithm: Algorithm by lazy {
        require(config.secret.isNotBlank()) { "JWT secret must not be blank" }
        Algorithm.HMAC256(config.secret)
    }

    val verifier: JWTVerifier by lazy {
        val builder = JWT
            .require(algorithm)
            .withIssuer(config.issuer)

        if (config.audience.isNotBlank()) {
            builder.withAudience(config.audience)
        }

        builder.build()
    }

    fun generateToken(uid: ObjectId, extraClaims: Map<String, String> = emptyMap()): String {
        val now = System.currentTimeMillis()
        val expiresAt = config.expiresInSeconds.takeIf { it > 0 }?.let { now + it * 1000 }

        val builder = JWT.create()
            .withIssuer(config.issuer)
            .withClaim("uid", uid.toHexString())
            .withIssuedAt(Date(now))

        if (config.audience.isNotBlank()) {
            builder.withAudience(config.audience)
        }

        expiresAt?.let { builder.withExpiresAt(Date(it)) }

        extraClaims.forEach { (key, value) ->
            builder.withClaim(key, value)
        }

        return builder.sign(algorithm)
    }
}
