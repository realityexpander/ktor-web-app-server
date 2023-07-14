package com.realityexpander.domain.common.data.remote.emailer

import com.realityexpander.applicationConfig
import com.realityexpander.ktorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.SimpleEmail

suspend fun sendPasswordResetEmail(emailAddress: String, passwordResetToken: String = ""): Boolean {
    ktorLogger.info("Sending reset email with SendInBlue to $emailAddress")

    val message = """
    <html>
        <body>
            <h1>Reset Password</h1>
            <img src="https://picsum.photos/200/300" alt="image">
            <p>Someone requested that you reset your password.</p>
            <p>
            <p>If this was not you, please ignore this email.</p>
            <br>
            <br>
            <a href="http://localhost:8081/reset-password/${passwordResetToken}">Click here to reset your password</a>
        </body>
    </html>
        
    """.trimIndent()

    return sendEmail(
        message,
        emailAddress,
        "Reset your password"
    )
}

suspend fun sendEmail(
    message: String,
    emailAddress: String,
    subject: String
): Boolean {
    return sendSimpleEmailViaSendInBlue(message, emailAddress, subject)
}

suspend fun sendSimpleEmailViaSendInBlue(
    message: String,
    emailAddress: String,
    subject: String
): Boolean {
    ktorLogger.info("Sending simple email with SendInBlue to $emailAddress")

    // Dashboard: https://app.sendinblue.com/settings
    // uses SendInBlue - google account realityexpanderdev@gmail.com

    return withContext(Dispatchers.IO) {
        try {
            val email = SimpleEmail()
            email.hostName = "smtp-relay.sendinblue.com"
//            email.setSmtpPort(587)
            email.setDebug(true)
            email.setAuthenticator(
                DefaultAuthenticator(
                    applicationConfig.emailSendinblueFromEmail,
                    applicationConfig.emailSendinblueApiKey
                )
            )
            email.isSSLOnConnect = true
            email.setFrom(applicationConfig.emailSendinblueFromEmail, applicationConfig.emailSendinblueFromName)
            email.subject = subject
            email.setMsg(message)
            email.addTo(emailAddress)
            email.send()

            return@withContext true
        } catch (e: Exception) {
            ktorLogger.error("Error sending email: $e")
            return@withContext false
        }
    }
}