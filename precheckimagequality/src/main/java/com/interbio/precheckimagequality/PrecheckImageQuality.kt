package com.interbio.precheckimagequality

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import io.jsonwebtoken.security.SignatureException
import java.util.*


object PrecheckImageQuality {
    private lateinit var config: PrecheckImageQualityConfiguration
    private var validLicense: Boolean = false

    fun initialize(context: Context, configuration: PrecheckImageQualityConfiguration) {
        this.config = configuration
    }
    fun version(): String {
        return "2.2.1"
    }

    private fun checkLicense(context: Context): Int {
        if (config.license == "") {
            return 5106
        }

        var jwtString = config.license
        var pem = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzdtRVuBb2aSVqITSwi/p
AX1PDUtn0Ie8FMRaneiht8Zy6d65xoDhJIdrBjBX6839K13B758DFlmTrak+IlfI
q01Bg5ciHtIS0RWbIY7TV92WBuiY1J7TZj3vZRqkfmF3MOc1zEw3cKkYly+TIBGU
TdsA/dfoVLbXXnW1lrZYOOKTxHOg24yEXHqCDmF4XUc2VCJDTw9AWmBcxNaueCnp
4S51OhlEI1yu5KTT7O3d/CHKXZbWqzeHlU2SSF3oQ1aMiumSo8tcgyUtz/qsY5v1
MUu5G4ujB9xG02jLFddQCJMOJNpZeD6isTqd/63pxiG/asnrtec6PT7pwtBiJSFf
KwIDAQAB
-----END PUBLIC KEY-----
    """.trimIndent()
        val publicKeyPEM: String = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace(System.lineSeparator(), "")
            .replace("-----END PUBLIC KEY-----", "")

        val publicKeyBytes: ByteArray = Base64.decode(publicKeyPEM, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)

        try {
            val claims = Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(jwtString).body
            print(claims)
            val currentTime = Calendar.getInstance().time
            val bundleId = context.getPackageName()

            var expired = claims.expiration
            var issuedAt = claims.issuedAt
            var targetApps = claims.audience
            var targetPlatform = claims.subject
            Log.d("LicenseCheck", "expiration: ${expired.time} currentTime: ${currentTime.time}")
            Log.d("LicenseCheck", "issuedAt: ${issuedAt.time} currentTime: ${currentTime.time}")
            Log.d("LicenseCheck", "targetApps: $targetApps bundleId: $bundleId")
            Log.d("LicenseCheck", "targetPlatform: $targetPlatform")

            if(expired.before(currentTime)) {
                // license expired
                return 5101
            }
            if(issuedAt.after(currentTime)) {
                // license not yet valid
                return 5102
            }
            if(!targetApps.equals("all_apps")) {
                if(!targetApps.split(".").contains(bundleId)) {
                    // invalid apps bundle id
                    return 5103
                }
            }
            if(!targetPlatform.contains("android")) {
                // invalid platform
                return 5104
            }
            return 0
        } catch (exception: SignatureException) {
            return 5100
        } catch (exception: ExpiredJwtException) {
            return 5101
        } catch (exception: Exception) {
            exception.printStackTrace()
            return 5105
        }
    }

    fun cleanup() {
    }

    fun startPrecheck(context: Context, onSuccess: (image: String) -> Unit, onError: (errorCode: Int) -> Unit) {
//        val licenseCheckResult = checkLicense(context)
//        if(licenseCheckResult != 0) {
//            onError(licenseCheckResult)
//            return
//        }


        PassedData.onSuccess = onSuccess
        PassedData.onError = onError
        PassedData.config = this.config
        val intent = Intent(context, CaptureActivity::class.java)
        context.startActivity(intent)
    }
}