/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.service.add

import app.seeneva.reader.logic.entity.ComicAddResult
import app.seeneva.reader.logic.entity.FileData
import app.seeneva.reader.presenter.PresenterView

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