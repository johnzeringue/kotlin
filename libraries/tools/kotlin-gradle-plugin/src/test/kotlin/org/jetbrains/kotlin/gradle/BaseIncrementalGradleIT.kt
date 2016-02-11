package org.jetbrains.kotlin.gradle

import com.google.common.io.Files
import org.gradle.api.logging.LogLevel
import org.jetbrains.kotlin.gradle.incremental.BuildStep
import org.jetbrains.kotlin.gradle.incremental.parseTestBuildLog
import org.junit.Assume
import java.io.File
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class BaseIncrementalGradleIT : BaseGradleIT() {

    open inner class IncrementalTestProject(name: String, wrapperVersion: String = "2.4", minLogLevel: LogLevel = LogLevel.DEBUG) : Project(name, wrapperVersion, minLogLevel) {
        var modificationStage: Int = 1
    }

    inner class JpsTestProject(val resourcesBase: File, val relPath: String, wrapperVersion: String = "1.6", minLogLevel: LogLevel = LogLevel.DEBUG) : IncrementalTestProject(File(relPath).name, wrapperVersion, minLogLevel) {
        override val resourcesRoot = File(resourcesBase, relPath)

        override fun setupWorkingDir() {
            val srcDir = File(projectDir, "src")
            srcDir.mkdirs()
            resourcesRoot.walk()
                    .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
                    .forEach { Files.copy(it, File(srcDir, it.name)) }
            copyDirRecursively(File(resourcesRootFile, "GradleWrapper-$wrapperVersion"), projectDir)
            copyDirRecursively(File(resourcesRootFile, "incrementalGradleProject"), projectDir)
        }
    }

    fun IncrementalTestProject.modify(runStage: Int? = null) {
        // TODO: multimodule support
        val projectSrcDir = File(File(workingDir, projectName), "src")
        assertTrue(projectSrcDir.exists())
        val actualStage = runStage ?: modificationStage

        println("<--- Modify stage: ${runStage?.toString() ?: "single"}")

        fun resource2project(f: File) = File(projectSrcDir, f.toRelativeString(resourcesRoot))

        resourcesRoot.walk().filter { it.isFile }.forEach {
            val nameParts = it.name.split(".")
            if (nameParts.size > 2) {
                val (fileStage, hasStage) = nameParts.last().toIntOr(0)
                if (!hasStage || fileStage == actualStage) {
                    val orig = File(resource2project(it.parentFile), nameParts.dropLast(if (hasStage) 2 else 1).joinToString("."))
                    when (if (hasStage) nameParts[nameParts.size - 2] else nameParts.last()) {
                        "touch" -> {
                            assert(orig.exists())
                            orig.setLastModified(Date().time)
                            println("<--- Modify: touch $orig")
                        }
                        "new" -> {
                            it.copyTo(orig, overwrite = true)
                            orig.setLastModified(Date().time)
                            println("<--- Modify: new $orig from $it")
                        }
                        "delete" -> {
                            assert(orig.exists())
                            orig.delete()
                            println("<--- Modify: delete $orig")
                        }
                    }
                }
            }
        }

        modificationStage = actualStage + 1
    }

    fun IncrementalTestProject.performAndAssertBuildStages(options: BuildOptions = defaultBuildOptions(), weakTesting: Boolean = false) {

        val checkKnown = testIsKnownJpsTestProject(resourcesRoot)
        Assume.assumeTrue(checkKnown.second ?: "", checkKnown.first)

        build("build", options = options) {
            assertSuccessful()
            assertReportExists()
        }

        val buildLogFile = resourcesRoot.listFiles { f: File -> f.name.endsWith("build.log") }?.sortedBy { it.length() }?.firstOrNull()
        assertNotNull(buildLogFile, "*build.log file not found" )

        val buildLog = parseTestBuildLog(buildLogFile!!)
        assertTrue(buildLog.any())

        println("<--- Build log size: ${buildLog.size}")
        buildLog.forEach {
            println("<--- Build log stage: ${if (it.compileSucceeded) "succeeded" else "failed"}: kotlin: ${it.compiledKotlinFiles} java: ${it.compiledJavaFiles}")
        }

        if (buildLog.size == 1) {
            modify()
            buildAndAssertStageResults(buildLog.first(), weakTesting = weakTesting)
        }
        else {
            buildLog.forEachIndexed { stage, stageResults ->
                modify(stage + 1)
                buildAndAssertStageResults(stageResults, weakTesting = weakTesting)
            }
        }
    }

    fun IncrementalTestProject.buildAndAssertStageResults(expected: BuildStep, options: BuildOptions = defaultBuildOptions(), weakTesting: Boolean = false) {
        build("build", options = options) {
            if (expected.compileSucceeded) {
                assertSuccessful()
                assertCompiledJavaSources(expected.compiledJavaFiles, weakTesting)
                assertCompiledKotlinSources(expected.compiledKotlinFiles, weakTesting)
            }
            else {
                assertFailed()
            }
        }
    }
}


private val supportedSourceExtensions = arrayListOf("kt", "java")
private val supportedModifyExtensions = arrayListOf("new", "delete")
private val unsupportedModifyExtensions = arrayListOf("touch")

private fun String.toIntOr(defaultVal: Int): Pair<Int, Boolean> {
    try {
        return Pair(toInt(), true)
    }
    catch (e: NumberFormatException) {
        return Pair(defaultVal, false)
    }
}

fun isJpsTestProject(projectRoot: File): Boolean = projectRoot.listFiles { f: File -> f.name.endsWith("build.log") }?.any() ?: false

fun testIsKnownJpsTestProject(projectRoot: File): Pair<Boolean, String?> {
    var hasKnownSources = false
    projectRoot.walk().filter { it.isFile }.forEach {
        if (it.name.equals("dependencies.txt", ignoreCase = true))
            return@testIsKnownJpsTestProject Pair(false, "multimodule tests are not supported yet")
        val nameParts = it.name.split(".")
        if (nameParts.size > 1) {
            val (fileStage, hasStage) = nameParts.last().toIntOr(0)
            val modifyExt = nameParts[nameParts.size - (if (hasStage) 2 else 1)]
            val ext = nameParts[nameParts.size - (if (hasStage) 3 else 2)]
            if (modifyExt in unsupportedModifyExtensions)
                return@testIsKnownJpsTestProject Pair(false, "unsupported modification extension ${it.name}")
            if (modifyExt in supportedModifyExtensions && ext !in supportedSourceExtensions)
                return@testIsKnownJpsTestProject Pair(false, "unknown staged file ${it.name}")
        }
        if (!hasKnownSources && it.extension in supportedSourceExtensions) {
            hasKnownSources = true
        }
    }
    return if (hasKnownSources) Pair(true, null)
    else Pair(false, "no known sources found")
}

