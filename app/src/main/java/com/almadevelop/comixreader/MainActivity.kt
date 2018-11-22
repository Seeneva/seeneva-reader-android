package com.almadevelop.comixreader

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.applyCanvas
import com.google.firebase.ml.custom.*
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerModel()

        val interpreter = createInterpreter()

        val modelInputOutput = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(BATCH_SIZE, 611, 398, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(BATCH_SIZE, 9750, 10))
            .build()

        val inputs = FirebaseModelInputs.Builder()
            .add(ndkTest())
            .build()

        interpreter.run(inputs, modelInputOutput)
            .addOnSuccessListener {
                val output = it.getOutput<Array<Array<FloatArray>>>(0)

//                //calculate non padded entries
//                val classesCount = 1
//                // 1 - no object class. 4 - bounding box
//                val numberOfOtputs = classesCount + 1 + 4
//
//                decodePrediction(output)

                Log.d("!!!!!", "!!!!!!!! ${output[0][0].contentToString()}")
                val r = parsePrediction(assets, output, BATCH_SIZE)
                Log.d("!!!!!", "!!!!!!!!2 ${r.contentDeepToString()}")

                val o = BitmapFactory.Options().apply {
                    inMutable = true
                }

                val b = BitmapFactory.decodeStream(assets.open("comix.jpg"), null, o).applyCanvas {
                    val p = Paint().apply {
                        color = 0x78ff0000
                        this.style = Paint.Style.FILL
                    }

                    r.forEach {
                        val (cx, cy, w, h) = it
                        drawRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, p)
                    }
                }

                imageView.setImageBitmap(b)


            }.addOnFailureListener {
                Log.d("!!!!!!!!!", it.message)
            }
    }

    fun registerModel() {
        val modelSource = FirebaseLocalModelSource.Builder(MODEL_NAME)
            .setAssetFilePath("comix.tflite")
            .build()

        FirebaseModelManager.getInstance().registerLocalModelSource(modelSource)
    }

    fun createInterpreter(): FirebaseModelInterpreter {
        val options = FirebaseModelOptions.Builder()
            .setLocalModelName(MODEL_NAME)
            .build()

        return FirebaseModelInterpreter.getInstance(options)!!
    }

    fun ndkTest(): Array<Array<Array<FloatArray>>> {
        val inB = BitmapFactory.decodeStream(assets.open("comix.jpg"))
        val start = System.currentTimeMillis()
        val r = stringFromJNI(inB)
        Log.d("!!!!!!!!!", (System.currentTimeMillis() - start).toString())
        inB.recycle()
        Log.d("!!!!!!!!!!!!! IMG", r.contentDeepToString())
        return Array(1) {
            if (it == 0) {
                r
            } else {
                Array(611) { Array(398) { FloatArray(3) } }
            }
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(s: Bitmap): Array<Array<FloatArray>>

    external fun parsePrediction(
        assetManager: AssetManager,
        pred: Array<Array<FloatArray>>,
        batchSize: Int
    ): Array<FloatArray>

    companion object {
        const val MODEL_NAME = "baloons"
        const val BATCH_SIZE = 1 //!!!!!!!!!! Need to be 4

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
