package com.almadevelop.comixreader.logic.usecase

import com.almadevelop.comixreader.data.entity.ml.Interpreter
import com.almadevelop.comixreader.data.source.jni.NativeSource

internal interface InterpreterUseCase {
    /**
     * Create new ML Interpreter
     * @return new ML Interpreter
     */
    suspend fun init(): Interpreter
}

internal class InterpreterUseCaseImpl(private val nativeSource: NativeSource) : InterpreterUseCase {
    override suspend fun init() =
        nativeSource.initInterpreterFromAsset("comix.tflite")
}