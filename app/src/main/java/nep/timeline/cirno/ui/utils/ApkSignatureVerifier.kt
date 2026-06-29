package nep.timeline.cirno.ui.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkSignatureVerifier {
    fun verify(context: Context, apkFile: File): Boolean {
        val installedFingerprint = getInstalledCertSha256(context) ?: return false
        val apkFingerprint = getApkCertSha256(context, apkFile) ?: return false
        return installedFingerprint == apkFingerprint
    }

    private fun getInstalledCertSha256(context: Context): String? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val signature = signatures?.firstOrNull() ?: return null
            sha256Hex(signature.toByteArray())
        } catch (_: Exception) {
            null
        }
    }

    private fun getApkCertSha256(context: Context, apkFile: File): String? {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.path,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            ) ?: return null
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val signature = signatures?.firstOrNull() ?: return null
            sha256Hex(signature.toByteArray())
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
