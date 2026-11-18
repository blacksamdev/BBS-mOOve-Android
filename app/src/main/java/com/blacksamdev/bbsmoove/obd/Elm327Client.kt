package com.blacksamdev.bbsmoove.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Le boîtier n'a pas répondu dans le délai imparti. */
class ElmTimeoutException(command: String) : IOException("pas de réponse à $command")

/**
 * Dialogue avec un boîtier ELM327 en Bluetooth classique (profil série SPP).
 *
 * Le protocole est textuel : on envoie une commande terminée par un retour
 * chariot (« 010C ») et le boîtier répond, puis affiche son invite « > »
 * quand il est prêt pour la commande suivante.
 *
 * Les lectures se font par sondage (available + petite pause) plutôt qu'en
 * lecture bloquante : ça permet un délai d'attente par commande, et fermer
 * la liaison depuis un autre fil interrompt proprement la boucle.
 */
class Elm327Client(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
) {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    /**
     * Ouvre la liaison série. Les clones ELM327 bon marché ne répondent pas
     * tous de la même façon : on essaie la connexion standard, puis la
     * variante « non sécurisée », puis le canal 1 en direct, qui débloque
     * beaucoup de clones.
     */
    @SuppressLint("MissingPermission")
    fun connect() {
        try {
            adapter.cancelDiscovery()
        } catch (e: SecurityException) {
            // Pas de permission de scan : sans gravité, on tente quand même.
        }

        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            {
                val method = device.javaClass.getMethod(
                    "createRfcommSocket",
                    Int::class.javaPrimitiveType,
                )
                method.invoke(device, 1) as BluetoothSocket
            },
        )

        var lastError: Exception? = null
        for (makeSocket in attempts) {
            var candidate: BluetoothSocket? = null
            try {
                candidate = makeSocket()
                candidate.connect()
                socket = candidate
                input = candidate.inputStream
                output = candidate.outputStream
                return
            } catch (e: Exception) {
                lastError = e
                try {
                    candidate?.close()
                } catch (closeError: IOException) {
                    // déjà fermé
                }
            }
        }
        throw IOException("liaison Bluetooth impossible (${lastError?.message})", lastError)
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // déjà fermé
        }
        socket = null
        input = null
        output = null
    }

    /**
     * Envoie une commande et renvoie la réponse en majuscules, sans espaces,
     * une ligne par réponse. Lève [ElmTimeoutException] si l'invite « > »
     * n'arrive pas à temps, et IOException si la liaison est coupée.
     */
    fun send(command: String, timeoutMs: Long = 2_000): String {
        val out = output ?: throw IOException("boîtier non connecté")
        val inp = input ?: throw IOException("boîtier non connecté")

        // On vide d'éventuels restes d'une réponse précédente.
        while (inp.available() > 0) {
            if (inp.read() < 0) throw IOException("liaison fermée")
        }

        out.write((command + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()

        val buffer = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (inp.available() > 0) {
                val c = inp.read()
                if (c < 0) throw IOException("liaison fermée")
                val ch = c.toChar()
                if (ch == '>') return normalize(buffer.toString())
                buffer.append(ch)
            } else {
                Thread.sleep(5)
            }
        }
        throw ElmTimeoutException(command)
    }

    companion object {
        /** Identifiant standard du profil série Bluetooth (SPP). */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Majuscules, sans espaces, une réponse par ligne, lignes vides retirées. */
        fun normalize(raw: String): String =
            raw.uppercase()
                .replace(" ", "")
                .replace('\r', '\n')
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "SEARCHING..." }
                .joinToString("\n")

        /**
         * Erreurs du boîtier qui signifient « la voiture ne répond pas » :
         * en général contact coupé, ou protocole pas encore trouvé.
         */
        fun isNoAnswer(response: String): Boolean {
            val r = response.replace("\n", "")
            return r.contains("NODATA") ||
                r.contains("UNABLETOCONNECT") ||
                r.contains("BUSINIT") ||
                r.contains("CANERROR") ||
                r.contains("BUSERROR") ||
                r.contains("STOPPED") ||
                r.contains("ERROR")
        }

        /**
         * Octets de données d'une réponse en mode 01 pour [pid], ou null.
         * « 410C1AF8 » pour le PID 0C donne [0x1A, 0xF8].
         * S'il y a plusieurs calculateurs, on prend la première réponse.
         */
        fun pidBytes(response: String, pid: Int): IntArray? =
            allPidBytes(response, pid).firstOrNull()

        /** Données de toutes les réponses (une par calculateur) pour [pid]. */
        fun allPidBytes(response: String, pid: Int): List<IntArray> {
            val key = "41" + "%02X".format(pid)
            val results = mutableListOf<IntArray>()
            for (line in response.split('\n')) {
                val index = line.indexOf(key)
                if (index < 0) continue
                val hex = line.substring(index + key.length)
                    .takeWhile { it in '0'..'9' || it in 'A'..'F' }
                val count = hex.length / 2
                if (count == 0) continue
                results.add(IntArray(count) { hex.substring(it * 2, it * 2 + 2).toInt(16) })
            }
            return results
        }
    }
}
