// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtFile

object KotlinFileFacadeShortNameIndex : StringStubIndexExtension<KtFile>() {
    private val KEY: StubIndexKey<String, KtFile> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeShortNameIndex")

    override fun getKey(): StubIndexKey<String, KtFile> = KEY

    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtFile> {
        return StubIndex.getElements(KEY, key, project, scope, KtFile::class.java)
    }
}