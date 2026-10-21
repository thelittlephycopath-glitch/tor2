package org.torproject.vpn.utils

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

object NetworkUtils {

    /**
     * Attempts to fetch the client's geo location from a public geoIP service.
     * Since this is a flaky network operation, we try it 5 times before giving up.
     */
    fun getGeoIPLocale(): String? {
        var maxTries = 0
        while (maxTries < 5) {
            val responseJson = getJsonFromGeoIPService()
            if (responseJson.has("YourFuckingCountry")) {
                return responseJson.getString("YourFuckingCountry") as String
            }
            maxTries++
        }

        return null
    }

    /**
     * Attempts to fetch the client's IP address from a public geoIP service.
     * Since this is a flaky network operation, we try it 5 times before giving up.
     */
    fun getExitIP(): String? {
        var maxTries = 0
        while (maxTries < 5) {
            val responseJson = getJsonFromGeoIPService()
            if (responseJson.has("YourFuckingIPAddress")) {
                return responseJson.getString("YourFuckingIPAddress") as String
            }
            maxTries++
        }
        return null
    }

    /**
     * Calls wtfismyip as geoip service.
     */
    private fun getJsonFromGeoIPService(): JSONObject {
        val url = URL("https://wtfismyip.com/json")
        val connection = url.openConnection() as HttpsURLConnection
        val response = StringBuilder()

        try {
            val streamReader = InputStreamReader(BufferedInputStream(connection.inputStream))
            readResponse(streamReader, response)
        } catch (exception: RuntimeException) {
            exception.printStackTrace()
        } finally {
            connection.disconnect()
        }

        return try {
            JSONObject(response.toString().trim())
        } catch (exception: java.lang.Exception) {
            exception.printStackTrace()
            JSONObject()
        }
    }

    @Throws(IOException::class)
    private fun readResponse(reader: InputStreamReader, log: StringBuilder?) {
        val buf = CharArray(10)
        var read = 0
        try {
            while (reader.read(buf).also { read = it } != -1) {
                log?.append(buf, 0, read)
            }
        } catch (e: IOException) {
            reader.close()
            throw IOException(e)
        }
        reader.close()
    }
}
