/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.jvm.compiler

import org.jetbrains.kotlin.codegen.ClassFileFactory
import org.jetbrains.kotlin.codegen.InlineTestUtil
import org.jetbrains.kotlin.codegen.filterClassFiles
import java.io.File
import java.util.Collections

abstract class AbstractCompileKotlinAgainstMultifileKotlinTest : AbstractCompileKotlinAgainstKotlinTest(), AbstractSMAPBaseTest {

    fun doBoxTest(firstFileName: String) {
        val inputFiles = listOf(firstFileName, firstFileName.substringBeforeLast("1.kt") + "2.kt")
        doBoxTest(inputFiles)
    }

    private fun doBoxTest(files: List<String>): Pair<ClassFileFactory, ClassFileFactory> {
        Collections.sort(files)

        var factory1: ClassFileFactory? = null
        var factory2: ClassFileFactory? = null
        try {
            factory1 = compileA(File(files[1]))
            factory2 = compileB(File(files[0]))
            invokeBox(files[0])
        }
        catch (e: Throwable) {
            var result = ""
            if (factory1 != null) {
                result += "FIRST: \n\n" + factory1.createText()
            }
            if (factory2 != null) {
                result += "\n\nSECOND: \n\n" + factory2.createText()
            }
            System.out.println(result)
            throw e
        }

        return Pair(factory1!!, factory2!!)
    }

}