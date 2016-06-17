/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.lang.resolve.android.test

import org.jetbrains.kotlin.codegen.AbstractBytecodeTextTest
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestJdkKind
import java.io.File

open class AbstractAndroidExtensionsAsmListingTest : AbstractBytecodeTextTest() {
    private fun createAndroidAPIEnvironment(path: String) {
        return createEnvironmentForConfiguration(
                KotlinTestUtils.newConfiguration(ConfigurationKind.ALL, TestJdkKind.ANDROID_API), path)
    }

    private fun createEnvironmentForConfiguration(configuration: CompilerConfiguration, path: String) {
        val layoutPaths = getResPaths(path)
        myEnvironment = createAndroidTestEnvironment(configuration, layoutPaths)
    }

    override fun doTest(path: String) {
        val fileName = path + getTestName(true) + ".kt"
        val listingFile = File(path + getTestName(true) + ".txt")

        createAndroidAPIEnvironment(path)
        loadFileByFullPath(fileName)
        val bytecodeListing = generateToText().replace("\\@Lkotlin\\/Metadata\\;\\(.+[\n\r]".toRegex(), "")
        KotlinTestUtils.assertEqualsToFile(listingFile, bytecodeListing)
    }
}