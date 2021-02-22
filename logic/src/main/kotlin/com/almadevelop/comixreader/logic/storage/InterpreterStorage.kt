package com.almadevelop.comixreader.logic.storage

import com.almadevelop.comixreader.data.entity.ml.Interpreter
import com.almadevelop.comixreader.logic.usecase.InterpreterUseCase

internal typealias InterpreterObjectStorage = SingleObjectStoragePickPoint<Interpreter>

internal class InterpreterSource(private val useCase: InterpreterUseCase) :
    SingleObjectStorageImpl.Source<Interpreter> {
    override suspend fun new() =
        useCase.init()

    override suspend fun onReleased(obj: Interpreter) {
        super.onReleased(obj)

        obj.close()
    }
}