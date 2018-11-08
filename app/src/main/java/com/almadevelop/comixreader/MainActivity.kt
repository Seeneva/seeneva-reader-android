package com.almadevelop.comixreader

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
            .add(renderScriptTest())
            .build()

        interpreter.run(inputs, modelInputOutput)
            .addOnSuccessListener {
                val output = it.getOutput<Array<Array<FloatArray>>>(0)
                Log.d("!!!!!", "!!!!!!!! ${it.getOutput<Array<Array<FloatArray>>>(0)[0][0][0]}")
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

    companion object {
        const val MODEL_NAME = "baloons"
    }
}
