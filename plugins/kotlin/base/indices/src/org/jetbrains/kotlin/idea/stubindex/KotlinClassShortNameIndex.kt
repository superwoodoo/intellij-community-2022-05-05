// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtClassOrObject

object KotlinClassShortNameIndex : KotlinStringStubIndexExtension<KtClassOrObject>(KtClassOrObject::class.java) {
    private val KEY: StubIndexKey<String, KtClassOrObject> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex")

    override fun getKey(): StubIndexKey<String, KtClassOrObject> = KEY

    override fun get(s: String, project: Project, scope: GlobalSearchScope): Collection<KtClassOrObject> {
        return StubIndex.getElements(KEY, s, project, scope, KtClassOrObject::class.java)
    }

    @JvmStatic
    @Deprecated("Use KotlinClassShortNameIndex as an object.", ReplaceWith("KotlinClassShortNameIndex"))
    fun getInstance(): KotlinClassShortNameIndex = KotlinClassShortNameIndex
}