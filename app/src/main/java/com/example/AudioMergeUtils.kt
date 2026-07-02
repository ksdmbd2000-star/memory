package com.example

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

object AudioMergeUtils {
    private const val TAG = "AudioMergeUtils"
    private const val SAMPLE_RATE = 16000
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) // 32000 bytes/sec

    fun createWavFromChunks(context: Context, minutes: Int, outputFile: File): Boolean {
        val requestedBytes = minutes * 60L * BYTES_PER_SECOND
        val chunks = AudioRecordingState.getSortedChunkFiles(context)
        if (chunks.isEmpty()) {
            Log.e(TAG, "No chunk files available to merge")
            return false
        }

        // Create a temporary file to merge all raw PCM bytes
        val tempPcmFile = File(context.cacheDir, "temp_merged.pcm")
        if (tempPcmFile.exists()) tempPcmFile.delete()

        try {
            FileOutputStream(tempPcmFile).use { outStream ->
                for (chunk in chunks) {
                    if (!chunk.exists() || chunk.length() == 0L) continue
                    try {
                        FileInputStream(chunk).use { inStream ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (inStream.read(buffer).also { read = it } != -1) {
                                outStream.write(buffer, 0, read)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to read chunk: ${chunk.name}", e)
                    }
                }
            }

            val totalPcmBytes = tempPcmFile.length()
            if (totalPcmBytes == 0L) {
                Log.e(TAG, "Merged PCM file is empty")
                return false
            }

            // Slice the trailing bytes if the recorded audio is longer than requested
            val bytesToCopy = totalPcmBytes.coerceAtMost(requestedBytes)
            val skipBytes = (totalPcmBytes - bytesToCopy).coerceAtLeast(0L)

            if (outputFile.exists()) {
                outputFile.delete()
            }

            // Write WAV file with header and then raw PCM bytes
            FileOutputStream(outputFile).use { outWav ->
                // Write placeholder WAV header
                writeWavHeader(outWav, CHANNELS, SAMPLE_RATE, BITS_PER_SAMPLE, bytesToCopy)

                // Copy sliced PCM bytes from temp merged file
                RandomAccessFile(tempPcmFile, "r").use { tempRaf ->
                    tempRaf.seek(skipBytes)
                    val buffer = ByteArray(8192)
                    var remaining = bytesToCopy
                    while (remaining > 0) {
                        val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                        val read = tempRaf.read(buffer, 0, toRead)
                        if (read == -1) break
                        outWav.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }

            Log.d(TAG, "Successfully wrote WAV file: ${outputFile.absolutePath}, duration size: $bytesToCopy bytes")
            return true

        } catch (e: IOException) {
            Log.e(TAG, "IOException during WAV merge/slice creation", e)
            return false
        } finally {
            if (tempPcmFile.exists()) {
                tempPcmFile.delete()
            }
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        channels: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        pcmDataSize: Long
    ) {
        val totalFileSize = pcmDataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        // Size of total file - 8
        header[4] = (totalFileSize and 0xff).toByte()
        header[5] = ((totalFileSize shr 8) and 0xff).toByte()
        header[6] = ((totalFileSize shr 16) and 0xff).toByte()
        header[7] = ((totalFileSize shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte() // fmt 
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16 // Subchunk 1 size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // Audio format: 1 (PCM)
        header[21] = 0

        header[22] = channels.toByte() // Channels (1 or 2)
        header[23] = 0

        // Sample rate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        // Byte rate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = blockAlign.toByte() // Block align
        header[33] = 0

        header[34] = bitsPerSample.toByte() // Bits per sample
        header[35] = 0

        header[36] = 'd'.code.toByte() // data
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Subchunk 2 size (PCM data size)
        header[40] = (pcmDataSize and 0xff).toByte()
        header[41] = ((pcmDataSize shr 8) and 0xff).toByte()
        header[42] = ((pcmDataSize shr 16) and 0xff).toByte()
        header[43] = ((pcmDataSize shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
