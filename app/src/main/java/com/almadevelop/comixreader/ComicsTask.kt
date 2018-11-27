package com.almadevelop.comixreader

import android.util.Log
import com.google.firebase.ml.custom.*
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ComicsTask : Callback {
    private val interpreter: FirebaseModelInterpreter
    private val modelInputOutput: FirebaseModelInputOutputOptions

    override val id: Long = 0

    init {
        registerModel()
        interpreter = createInterpreter()
        modelInputOutput = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(BATCH_SIZE, 611, 398, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(BATCH_SIZE, 9750, 10))
            .build()

    }

    override fun onPagesBatchPrepared(input: ByteBuffer): ByteBuffer? {
        val s = runBlocking {
            val b = input.order(ByteOrder.nativeOrder())

//                Log.d(
//                    "!!!!!!! ANDROID",
//                    "!!!!! ${Thread.currentThread().name}___ ${b.getFloat(0)}"
//                )


            val inputs = FirebaseModelInputs.Builder()
                .add(b)
                .build()


            suspendCoroutine<ByteBuffer> { cont ->
                interpreter.run(inputs, modelInputOutput).addOnSuccessListener {
                    val output =
                        it.getOutput<Array<Array<FloatArray>>>(0)

                    val buf = ByteBuffer.allocateDirect(4 * 4 * 9750 * 10).order(ByteOrder.nativeOrder())

                    output.asSequence()
                        .flatMap { it.asSequence() }
                        .flatMap { it.asSequence() }
                        .forEach {
                            buf.putFloat(it)
                        }

                    cont.resume(buf)

                    //interpreter.close()

                }.addOnFailureListener {
                    Log.d("ERROR", "!!!!!!!!! ${it}")
                    cont.resumeWithException(it)
                }
            }
        }

//            Log.d(
//                "FINISH",
//                "!!!!!!!!! ${onPagesBatchPrepared.getFloat(0)}"
//            )

        return s
    }

    override fun onComicInfoParsed(comicInfo: ComicInfo) {
        Log.d("!!!!!!!!!!", "!!!!!!!! $comicInfo")
    }

    override fun onComicPageObjectsDetected(comicPageObjects: ComicPageObjects) {
        Log.d("!!!!!!!!!!", "!!!!!!!! $comicPageObjects")
    }

    private fun registerModel() {
        val modelSource = FirebaseLocalModelSource.Builder(MODEL_NAME)
            .setAssetFilePath("comix.tflite")
            .build()

        FirebaseModelManager.getInstance().registerLocalModelSource(modelSource)
    }

    private fun createInterpreter(): FirebaseModelInterpreter {
        val options = FirebaseModelOptions.Builder()
            .setLocalModelName(MODEL_NAME)
            .build()

        return FirebaseModelInterpreter.getInstance(options)!!
    }

    private companion object {
        const val MODEL_NAME = "baloons"
        const val BATCH_SIZE = 4 //!!!!!!!!!! Need to be 4
    }
}