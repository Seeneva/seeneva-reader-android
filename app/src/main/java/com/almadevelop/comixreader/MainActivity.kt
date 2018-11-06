package com.almadevelop.comixreader

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
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

        val b = BitmapFactory.decodeStream(assets.open("comix.jpg"))

        val input = Array(1) { Array(611) { Array(398) { FloatArray(3) } } }

        val inputs = FirebaseModelInputs.Builder()
            .add(input)
            .build()

        interpreter.run(inputs, modelInputOutput)
            .addOnSuccessListener {
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

    companion object {
        val MODEL_NAME = "baloons"
    }
}
