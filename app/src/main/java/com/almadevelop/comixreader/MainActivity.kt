package com.almadevelop.comixreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.renderscript.Allocation
import androidx.renderscript.Element
import androidx.renderscript.RenderScript
import androidx.renderscript.Type
import com.google.firebase.ml.custom.*
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerModel()

        val interpreter = createInterpreter()

        val modelInputOutput = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 611, 398, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 9750, 10))
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
                parsePrediction(output, CLASSES, BATCH_SIZE, ANCHORS_HEIGHT, ANCHORS_WIDTH, ANCHOR_PER_GRID)
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

    fun renderScriptTest(): Array<Array<Array<FloatArray>>> {
        val inB = BitmapFactory.decodeStream(assets.open("comix.jpg"))

        val imgW = inB.width
        val imgH = inB.height

        val rs = RenderScript.create(this)

        val inAllocation = Allocation.createFromBitmap(rs, inB)
        val outAllocation = Allocation.createTyped(rs, Type.createXY(rs, Element.F32_3(rs), imgW, imgH))
        val script2 = ScriptC_comix_page_prepare(rs)

        inB.recycle()

        script2.invoke_proceed(inAllocation, outAllocation)

        inAllocation.destroy()

        val a = FloatArray(imgW * imgH * 4)
        outAllocation.copyTo(a)
        outAllocation.destroy()

        val input = Array(1) { Array(imgH) { Array(imgW) { FloatArray(3) } } }

        //rotate indexes. Because height is first in the model input
        var h = 0
        var w = 0

        for (i in 0 until a.size step 4) {
            val array = input[0][h][w]

            array[2] = a[i]
            array[1] = a[i + 1]
            array[0] = a[i + 2]

            if (w == imgW - 1) {
                h++
                w = 0
            } else {
                w++
            }
        }

        return input
    }

    fun decodePrediction(pred: Array<Array<FloatArray>>) {
        val rs = RenderScript.create(this)

        val inAllocation = ScriptField_ModelPred.create1D(rs, 9750)

        pred[0].forEachIndexed { i, a ->
            ScriptField_ModelPred.Item().apply {
                for (arrayIndex in 0 until 5) {
                    y[arrayIndex] = a[arrayIndex]
                }
            }.also {
                inAllocation.set(it, i, true)
            }
        }

        val al = inAllocation.allocation

        val script2 = ScriptC_prediction(rs)
        //script2.invoke_decodePred(al, CLASSES, BATCH_SIZE, ANCHORS_HEIGHT, ANCHORS_WIDTH, ANCHOR_PER_GRID)
    }

    fun ndkTest(): Array<Array<Array<FloatArray>>> {
        val inB = BitmapFactory.decodeStream(assets.open("comix.jpg"))
        val r = stringFromJNI(inB)
        inB.recycle()
        return Array(1) { r }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(s: Bitmap): Array<Array<FloatArray>>

    external fun parsePrediction(
        pred: Array<Array<FloatArray>>,
        cCount: Int,
        batchSize: Int,
        aHeigth: Int,
        aWidth: Int,
        aPerGrid: Int
    )

    companion object {
        const val MODEL_NAME = "baloons"
        const val CLASSES = 1
        const val BATCH_SIZE = 4
        const val ANCHORS_HEIGHT = 39
        const val ANCHORS_WIDTH = 25
        const val ANCHOR_PER_GRID = 10

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
