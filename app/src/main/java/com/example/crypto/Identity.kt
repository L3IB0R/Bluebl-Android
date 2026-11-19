package com.example.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

class Identity private constructor(
    private val noiseSecret: ByteArray,
    private val signingSecret: ByteArray
) {
    private val noiseParams = X25519PrivateKeyParameters(noiseSecret, 0)
    private val signingParams = Ed25519PrivateKeyParameters(signingSecret, 0)

    fun toBytes(): Pair<ByteArray, ByteArray> = Pair(noiseSecret.copyOf(), signingSecret.copyOf())

    fun noisePublic(): ByteArray = noiseParams.generatePublicKey().encoded

    fun signingPublic(): ByteArray = signingParams.generatePublicKey().encoded

    fun peerId(): ByteArray = derivePeerId(noisePublic())

    fun peerIdHex(): String = peerId().toHex()

    fun signingPrivateParams(): Ed25519PrivateKeyParameters = signingParams

    fun verifyingPublicParams(): Ed25519PublicKeyParameters = signingParams.generatePublicKey()

    fun sign(msg: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, signingParams)
        signer.update(msg, 0, msg.size)
        return signer.generateSignature()
    }

    companion object {
        fun generate(): Identity {
            val rng = SecureRandom()
            val noise = ByteArray(32).also { rng.nextBytes(it) }
            val signing = ByteArray(32).also { rng.nextBytes(it) }
            return Identity(noise, signing)
        }

        fun fromBytes(noiseSecret: ByteArray, signingSecret: ByteArray): Identity {
            require(noiseSecret.size == 32 && signingSecret.size == 32)
            return Identity(noiseSecret.copyOf(), signingSecret.copyOf())
        }

        fun loadOrGenerate(file: File): Identity {
            if (file.exists() && file.length() == 64L) {
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes != null && bytes.size == 64) {
                    return fromBytes(bytes.copyOfRange(0, 32), bytes.copyOfRange(32, 64))
                }
            }
            val id = generate()
            file.parentFile?.mkdirs()
            val (noise, signing) = id.toBytes()
            val buf = ByteArray(64)
            System.arraycopy(noise, 0, buf, 0, 32)
            System.arraycopy(signing, 0, buf, 32, 32)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            runCatching {
                tmp.writeBytes(buf)
                tmp.renameTo(file)
            }
            return id
        }

        fun derivePeerId(noisePublic: ByteArray): ByteArray {
            require(noisePublic.size == 32)
            val hash = MessageDigest.getInstance("SHA-256").digest(noisePublic)
            return hash.copyOfRange(0, 8)
        }

        fun matchesClaimedPeerId(claimed: String, noisePublic: ByteArray): Boolean {
            if (claimed.length != 16) return false
            if (!claimed.all { it in '0'..'9' || it in 'a'..'f' }) return false
            return derivePeerId(noisePublic).toHex() == claimed
        }

        fun verifySignature(signature: ByteArray, msg: ByteArray, publicKey: ByteArray): Boolean {
            if (signature.size != 64 || publicKey.size != 32) return false
            return runCatching {
                val key = Ed25519PublicKeyParameters(publicKey, 0)
                val verifier = Ed25519Signer()
                verifier.init(false, key)
                verifier.update(msg, 0, msg.size)
                verifier.verifySignature(signature)
            }.getOrDefault(false)
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in indices step 2) {
        val hi = Character.digit(this[i], 16)
        val lo = Character.digit(this[i + 1], 16)
        if (hi == -1 || lo == -1) return null
        out[i / 2] = ((hi shl 4) or lo).toByte()
    }
    return out
}
