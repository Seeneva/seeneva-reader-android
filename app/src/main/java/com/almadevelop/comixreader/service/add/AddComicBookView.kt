package com.almadevelop.comixreader.service.add

import com.almadevelop.comixreader.logic.entity.ComicAddResult
import com.almadevelop.comixreader.logic.entity.FileData
import com.almadevelop.comixreader.presenter.PresenterView

interface AddComicBookView : PresenterView {
    /**
     * Called when comic book adding task started
     *
     * @param fileData comic book what has been started
     */
    fun onAddingStarted(fileData: FileData)

    /**
     * Called before [onAddingResult]
     *
     * @param id open task id
     * @param fileData comic book what has been finished
     */
    fun onAddingFinished(id: Int, fileData: FileData)

    /**
     * Called when adding task has received an result
     *
     * @param fileData comic book what has received an result
     * @param result open task result
     */
    fun onAddingResult(fileData: FileData, result: ComicAddResult)

    /**
     * Called when can't add a comic book
     *
     * @param id open task id
     * @param fileData comic book what has been failed
     */
    fun onAddingFailed(id: Int, fileData: FileData)
}