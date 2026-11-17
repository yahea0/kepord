package com.kepord.ime.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class MechanicalSoundProfile(val titleAr: String, val descAr: String) {
    CLICKY_BLUE("Clicky Blue (أزرق ميكانيكي)", "تكتكة ميكانيكية واضحة وحادة مع صوت ارتطام PBT"),
    TACTILE_BROWN("Tactile Brown (بني متوازن)", "صوت لمسي مريح ومكتوم بنقرة ميكانيكية نظيفة"),
    DEEP_THOCK("Deep Thock (ثوك عميق)", "صوت عميق وفخم يشبه السويتشات المزيتة")
}

class MechanicalSoundEngine(context: Context) {

    private val appContext = context.applicationContext
    private val soundPool: SoundPool
    private val loadedPools = HashMap<MechanicalSoundProfile, IntArray>()
    private var currentProfileLoaded: MechanicalSoundProfile? = null
    private var variationIdx = 0

    @Volatile
    private var isReady = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()

        Thread {
            prepareProfile(MechanicalSoundProfile.CLICKY_BLUE)
            isReady = true
        }.start()
    }

    fun preloadProfileAsync(profile: MechanicalSoundProfile) {
        if (currentProfileLoaded == profile && loadedPools.containsKey(profile)) return
        Thread {
            prepareProfile(profile)
        }.start()
    }

    fun playKeyClick(profile: MechanicalSoundProfile, volume: Float) {
        if (!isReady) return
        val ids = loadedPools[profile]
        if (ids == null) {
            preloadProfileAsync(profile)
            return
        }
        val soundId = ids[variationIdx % ids.size]
        variationIdx = (variationIdx + 1) % ids.size
        val v = volume.coerceIn(0.05f, 1.0f)
        soundPool.play(soundId, v, v, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }

    @Synchronized
    private fun prepareProfile(profile: MechanicalSoundProfile) {
        if (loadedPools.containsKey(profile)) {
            currentProfileLoaded = profile
            return
        }
        val pitchMultipliers = floatArrayOf(0.96f, 1.0f, 1.04f)
        val ids = IntArray(pitchMultipliers.size)
        val cacheDir = appContext.cacheDir
        for (i in pitchMultipliers.indices) {
            val file = File(cacheDir, "kepord_mech_v2_${profile.name.lowercase()}_$i.wav")
            if (!file.exists() || file.length() < 100L) {
                val wavBytes = synthesizeSwitchWav(profile, pitchMultipliers[i], i + 17)
                FileOutputStream(file).use { it.write(wavBytes) }
            }
            ids[i] = soundPool.load(file.absolutePath, 1)
        }
        loadedPools[profile] = ids
        currentProfileLoaded = profile
    }

    private fun synthesizeSwitchWav(
        profile: MechanicalSoundProfile,
        pitchMult: Float,
        seed: Int
    ): ByteArray {
        val sampleRate = 44100
        val durationSec = 0.034
        val count = (sampleRate * durationSec).toInt()
        val samples = ShortArray(count)
        val rng = Random(seed)

        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate.toDouble()
            var v: Double

            when (profile) {
                MechanicalSoundProfile.CLICKY_BLUE -> {
                    // Crisp leaf snap at t=0 + delayed PBT housing thock at t=2.1ms
                    val snapEnv = exp(-t / 0.0010)
                    val snap = snapEnv * (
                        0.58 * sin(2.0 * PI * 2850.0 * pitchMult * t) +
                            0.36 * sin(2.0 * PI * 4450.0 * pitchMult * t) +
                            0.22 * (rng.nextDouble() * 2.0 - 1.0)
                        )
                    val t2 = (t - 0.0021).coerceAtLeast(0.0)
                    val thockEnv = if (t >= 0.0021) exp(-t2 / 0.0046) else 0.0
                    val thockFreq = (235.0 + 185.0 * exp(-t2 / 0.0020)) * pitchMult
                    val thock = thockEnv * (
                        0.72 * sin(2.0 * PI * thockFreq * t2) +
                            0.30 * sin(2.0 * PI * 840.0 * pitchMult * t2)
                        )
                    v = snap * 0.78 + thock * 0.88
                }

                MechanicalSoundProfile.TACTILE_BROWN -> {
                    val snapEnv = exp(-t / 0.0013)
                    val snap = snapEnv * (
                        0.36 * sin(2.0 * PI * 1720.0 * pitchMult * t) +
                            0.20 * (rng.nextDouble() * 2.0 - 1.0)
                        )
                    val t2 = (t - 0.0017).coerceAtLeast(0.0)
                    val thockEnv = if (t >= 0.0017) exp(-t2 / 0.0056) else 0.0
                    val thock = thockEnv * (
                        0.82 * sin(2.0 * PI * 215.0 * pitchMult * t2) +
                            0.28 * sin(2.0 * PI * 640.0 * pitchMult * t2)
                        )
                    v = snap * 0.62 + thock * 0.92
                }

                MechanicalSoundProfile.DEEP_THOCK -> {
                    val thockEnv = exp(-t / 0.0066)
                    val freq = (165.0 + 150.0 * exp(-t / 0.0022)) * pitchMult
                    val transient = exp(-t / 0.0012) * 0.16 * (rng.nextDouble() * 2.0 - 1.0)
                    v = thockEnv * (
                        0.88 * sin(2.0 * PI * freq * t) +
                            0.32 * sin(2.0 * PI * 470.0 * pitchMult * t)
                        ) + transient
                }
            }

            if (i > count - 96) {
                v *= (count - i).toDouble() / 96.0
            }
            val pcm = (v * 27000.0).toInt().coerceIn(-32767, 32767)
            samples[i] = pcm.toShort()
        }

        val dataSize = samples.size * 2
        val baos = ByteArrayOutputStream(44 + dataSize)
        val dos = DataOutputStream(baos)

        fun writeLeInt(value: Int) {
            dos.writeByte(value and 0xFF)
            dos.writeByte((value ushr 8) and 0xFF)
            dos.writeByte((value ushr 16) and 0xFF)
            dos.writeByte((value ushr 24) and 0xFF)
        }

        fun writeLeShort(value: Int) {
            dos.writeByte(value and 0xFF)
            dos.writeByte((value ushr 8) and 0xFF)
        }

        dos.writeBytes("RIFF")
        writeLeInt(36 + dataSize)
        dos.writeBytes("WAVE")
        dos.writeBytes("fmt ")
        writeLeInt(16)
        writeLeShort(1)
        writeLeShort(1)
        writeLeInt(sampleRate)
        writeLeInt(sampleRate * 2)
        writeLeShort(2)
        writeLeShort(16)
        dos.writeBytes("data")
        writeLeInt(dataSize)
        for (s in samples) {
            writeLeShort(s.toInt())
        }
        dos.flush()
        return baos.toByteArray()
    }
}
