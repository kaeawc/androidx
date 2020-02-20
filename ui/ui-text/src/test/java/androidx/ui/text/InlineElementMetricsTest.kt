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

package androidx.ui.text

import androidx.ui.unit.TextUnit
import androidx.ui.unit.em
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.IllegalArgumentException

@RunWith(JUnit4::class)
class InlineElementMetricsTest {
    @Test(expected = IllegalArgumentException::class)
    fun width_isInherit() {
        InlineElementMetrics(
            width = TextUnit.Inherit,
            height = 1.em,
            textVerticalAlign = TextVerticalAlign.AboveBaseline
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun height_isInherit() {
        InlineElementMetrics(
            width = 1.em,
            height = TextUnit.Inherit,
            textVerticalAlign = TextVerticalAlign.AboveBaseline
        )
    }
}