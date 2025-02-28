// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.serviceContainer.ComponentManagerImpl
import org.jetbrains.kotlin.asJava.builder.LightClassConstructionContext
import org.jetbrains.kotlin.asJava.builder.StubComputationTracker
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.junit.Assert
import java.util.Collections.synchronizedList

object LightClassComputationControl {
    fun testWithControl(project: Project, testText: String, testBody: () -> Unit) {
        val expectedLightClassFqNames = InTextDirectivesUtils.findLinesWithPrefixesRemoved(
            testText, "// $LIGHT_CLASS_DIRECTIVE"
        ).map { it.trim() }

        val actualFqNames = synchronizedList(ArrayList<String>())
        val stubComputationTracker = object : StubComputationTracker {
            override fun onStubComputed(javaFileStub: PsiJavaFileStub, context: LightClassConstructionContext) {
                val qualifiedName = (javaFileStub.childrenStubs.single() as PsiClassStub<*>).qualifiedName!!
                actualFqNames.add(qualifiedName)
            }
        }

        project.withServiceRegistered<StubComputationTracker, Unit>(stubComputationTracker) {
            testBody()
        }

        if (expectedLightClassFqNames.toSortedSet() != synchronized(actualFqNames) { actualFqNames.toSortedSet() }) {
            Assert.fail(
                "Expected to compute: ${expectedLightClassFqNames.prettyToString()}\n" +
                        "Actually computed: ${actualFqNames.prettyToString()}\n" +
                        "Use $LIGHT_CLASS_DIRECTIVE to specify expected light class computations"
            )
        }
    }

    const val LIGHT_CLASS_DIRECTIVE = "LIGHT_CLASS:"

    private fun List<String>.prettyToString() = if (isEmpty()) "<empty>" else joinToString()
}

inline fun <reified T : Any, R> ComponentManager.withServiceRegistered(instance: T, body: () -> R): R {
    val picoContainer = this as ComponentManagerImpl
    val key = T::class.java.name
    try {
        picoContainer.unregisterComponent(key)
        picoContainer.registerComponentInstance(key, instance)
        return body()
    } finally {
        picoContainer.unregisterComponent(key)
    }
}
