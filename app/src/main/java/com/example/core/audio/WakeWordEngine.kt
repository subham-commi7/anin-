package com.example.core.audio

class WakeWordEngine(
    private val onWakeWordDetected: () -> Unit
) {
    private var isAninSpeaking: Boolean = false
    private var energyAccumulator: Float = 0f
    private var frameCounter: Int = 0

    fun updatePlaybackState(speaking: Boolean) {
        isAninSpeaking = speaking
    }

    fun processAudioFrame(chunk: FloatArray) {
        // RULE: Anin's own synthesized voice must never trigger the wake-word
        if (isAninSpeaking) return

        var frameEnergy = 0f
        for (sample in chunk) {
            frameEnergy += sample * sample
        }
        val rms = Math.sqrt((frameEnergy / Math.max(1, chunk.size)).toDouble()).toFloat()

        if (rms > 0.12f) {
            energyAccumulator += rms
            frameCounter++
            if (frameCounter >= 6 && energyAccumulator > 0.8f) {
                onWakeWordDetected()
                frameCounter = 0
                energyAccumulator = 0f
            }
        } else {
            frameCounter = Math.max(0, frameCounter - 1)
            energyAccumulator = Math.max(0f, energyAccumulator - 0.1f)
        }
    }

    fun triggerWakeWordManually() {
        onWakeWordDetected()
    }
}
