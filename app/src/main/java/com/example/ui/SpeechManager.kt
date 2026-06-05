package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onListeningStateChange: (Boolean) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Reconhecimento de voz offline indisponível. Usando modo digitação ou carregue exemplos abaixo.")
            onListeningStateChange(false)
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onListeningStateChange(true)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onListeningStateChange(false)
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio de gravação."
                            SpeechRecognizer.ERROR_CLIENT -> "Serviço de voz offline inativo. Digite ou use simulador."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de gravação não concedida."
                            SpeechRecognizer.ERROR_NETWORK -> "Sem de conexão de rede de voz."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo limite de rede excedido."
                            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma voz captada de forma legível."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado."
                            SpeechRecognizer.ERROR_SERVER -> "Erro no servidor do Google."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silêncio prolongado detectado."
                            else -> "Falha na captação local de voz ($error)."
                        }
                        onError(message)
                        onListeningStateChange(false)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onResult(matches[0])
                        } else {
                            onError("Nenhuma fala detectada.")
                        }
                        onListeningStateChange(false)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onPartialResult(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                
                // Configuração avançada de tempo de silêncio para tolerar pausas de pensamento de pelo menos 5 segundos
                // IMPORTANTE: Devem ser passados como Integer (Int) e não Long, caso contrário o motor de voz do Google ignora!
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 5000)
                putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 5000)
                putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 5000)

                // Ativar pontuação e formatação automática no motor de Speech-To-Text do Google (Android 13+)
                putExtra("android.speech.extra.ENABLE_FORMATTING", "android.speech.extra.FORMATTING_SENSITIVE")
                putExtra("android.speech.extra.ENABLE_FORMATTING", true)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("Falha: ${e.message}")
            onListeningStateChange(false)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignored
        }
        speechRecognizer = null
        onListeningStateChange(false)
    }
}
