/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.benchmark.test.view

import androidx.ui.integration.test.view.AndroidTextViewTestCase
import androidx.test.filters.LargeTest
import androidx.ui.benchmark.AndroidBenchmarkRule
import androidx.ui.benchmark.benchmarkDrawPerf
import androidx.ui.benchmark.benchmarkFirstDraw
import androidx.ui.benchmark.benchmarkFirstLayout
import androidx.ui.benchmark.benchmarkFirstMeasure
import androidx.ui.benchmark.benchmarkFirstSetContent
import androidx.ui.benchmark.benchmarkLayoutPerf
import androidx.ui.integration.test.TextBenchmarkTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Benchmark that runs [AndroidTextViewTestCase].
 */
@LargeTest
@RunWith(Parameterized::class)
class AndroidTextViewBenchmark(private val textLength: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "length={0}")
        fun initParameters(): Array<Any> = arrayOf(32, 512)
    }

    @get:Rule
    val textBenchmarkRule = TextBenchmarkTestRule()

    @get:Rule
    val benchmarkRule = AndroidBenchmarkRule()

    private val caseFactory = {
        textBenchmarkRule.generator { textGenerator ->
            AndroidTextViewTestCase(
                List(textBenchmarkRule.repeatTimes) {
                    textGenerator.nextParagraph(textLength)
                }
            )
        }
    }
    @Test
    fun first_setContent() {
        benchmarkRule.benchmarkFirstSetContent(caseFactory)
    }

    @Test
    fun first_measure() {
        benchmarkRule.benchmarkFirstMeasure(caseFactory)
    }

    @Test
    fun first_setContentPlusMeasure() {
        with(benchmarkRule) {
            runBenchmarkFor(caseFactory) {
                measureRepeated {
                    setupContent()
                    runWithTimingDisabled {
                        requestLayout()
                    }
                    measure()
                    runWithTimingDisabled {
                        disposeContent()
                    }
                }
            }
        }
    }

    @Test
    fun first_layout() {
        benchmarkRule.benchmarkFirstLayout(caseFactory)
    }

    @Test
    fun first_draw() {
        benchmarkRule.benchmarkFirstDraw(caseFactory)
    }

    @Test
    fun layout() {
        benchmarkRule.benchmarkLayoutPerf(caseFactory)
    }

    @Test
    fun draw() {
        benchmarkRule.benchmarkDrawPerf(caseFactory)
    }
}