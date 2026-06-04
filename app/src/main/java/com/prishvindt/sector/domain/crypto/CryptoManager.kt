package com.prishvindt.sector.domain.crypto

interface CryptoManager {
    fun encryptForContact(
        contactId: String,
        plainText: String
    ): Result<String>

    fun decryptFromContact(
        contactId: String,
        encryptedText: String
    ): Result<String>

    fun encryptLocal(plainText: String): Result<String>

    fun decryptLocal(encryptedText: String): Result<String>
}

class NoOpCryptoManager : CryptoManager {
    override fun encryptForContact(
        contactId: String,
        plainText: String
    ): Result<String> = Result.failure(NoRealCryptoAvailableException())

    override fun decryptFromContact(
        contactId: String,
        encryptedText: String
    ): Result<String> = Result.failure(NoRealCryptoAvailableException())

    override fun encryptLocal(plainText: String): Result<String> =
        Result.success(plainText)

    override fun decryptLocal(encryptedText: String): Result<String> =
        Result.success(encryptedText)
}

class NoRealCryptoAvailableException : IllegalStateException(
    "Real encryption is not implemented yet"
)
