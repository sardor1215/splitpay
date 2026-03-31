package com.splitpay.auth

import org.eclipse.angus.mail.util.MailSSLSocketFactory
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

object EmailService {
    private val host    = System.getenv("SMTP_HOST") ?: "smtp.gmail.com"
    private val port    = System.getenv("SMTP_PORT") ?: "587"
    private val user    = System.getenv("SMTP_USER") ?: "your@gmail.com"
    private val pass    = System.getenv("SMTP_PASS") ?: "your-app-password"
    private val baseUrl = System.getenv("BASE_URL")  ?: "http://localhost:8080"

    fun sendVerificationEmail(toEmail: String, token: String) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.port", port)
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(user, pass)
        })

        val link = "$baseUrl/auth/verify?token=$token"

        MimeMessage(session).apply {
            setFrom(InternetAddress(user))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            subject = "Verify your SplitPay account"
            setText("""
                Welcome to SplitPay!

                Please verify your email by clicking the link below:
                $link

                This link expires in 24 hours.
            """.trimIndent())
        }.also { Transport.send(it) }
    }
}