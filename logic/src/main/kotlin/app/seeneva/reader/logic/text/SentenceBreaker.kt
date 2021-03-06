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

package app.seeneva.reader.logic.text

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.BreakIterator
import java.util.*
import kotlin.coroutines.CoroutineContext
import android.icu.text.BreakIterator as BreakIteratorICU

/**
 * Factory of [SentenceBreaker]
 */
internal class SentenceBreakerFactory(private val coroutineContext: CoroutineContext) {
    private var sentenceBreaker: SentenceBreaker? = null

    /**
     * Get [SentenceBreaker] by provided [locale]
     * @param locale locale to use
     * @return [SentenceBreaker] instance
     */
    fun get(locale: Locale = Locale.getDefault()): SentenceBreaker {
        return sentenceBreaker?.takeIf { it.locale == locale } ?: when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                SentenceBreakerICU(coroutineContext, locale)
            else ->
                SentenceBreakerJava(coroutineContext, locale)
        }.also { sentenceBreaker = it }
    }
}

/**
 * String breaker into sequences
 */
internal interface SentenceBreaker {
    /**
     * Locale to use
     */
    val locale: Locale

    /**
     * Break text into flow of sequence strings
     * @param text text to break
     * @return flow of sequences
     */
    suspend fun breakText(text: String): Flow<String>
}

private abstract class BaseSentenceBreaker<B>(
    private val coroutineContext: CoroutineContext,
    protected val breakIterator: B,
    override val locale: Locale
) : SentenceBreaker {
    override suspend fun breakText(text: String): Flow<String> {
        if (text.isBlank()) {
            return emptyFlow()
        }

        withContext(coroutineContext) { setText(text) }

        currentCoroutineContext().ensureActive()

        return flow {
            var start = 0

            while (hasNext()) {
                val end = current()

                emit(text.substring(start, end))

                start = end
            }
        }.flowOn(coroutineContext)
    }

    protected abstract fun hasNext(): Boolean

    protected abstract fun current(): Int

    protected abstract fun setText(text: String)
}

@RequiresApi(Build.VERSION_CODES.N)
private class SentenceBreakerICU(coroutineContext: CoroutineContext, locale: Locale) :
    BaseSentenceBreaker<BreakIteratorICU>(
        coroutineContext,
        BreakIteratorICU.getSentenceInstance(locale),
        locale
    ) {
    override fun hasNext(): Boolean =
        breakIterator.next() != BreakIteratorICU.DONE

    override fun current(): Int =
        breakIterator.current()

    override fun setText(text: String) {
        breakIterator.setText(text)
    }
}

private class SentenceBreakerJava(coroutineContext: CoroutineContext, locale: Locale) :
    BaseSentenceBreaker<BreakIterator>(
        coroutineContext,
        BreakIterator.getSentenceInstance(locale),
        locale
    ) {
    override fun hasNext(): Boolean =
        breakIterator.next() != BreakIterator.DONE

    override fun current(): Int =
        breakIterator.current()

    override fun setText(text: String) {
        breakIterator.setText(text)
    }
}