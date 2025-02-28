// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass

import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.quickfix.DelegatingIntentionAction
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.containsStarProjections
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.guessTypes
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.noSubstitutions
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.Qualifier
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.descriptors.ClassKind as ClassDescriptorKind

internal fun String.checkClassName(): Boolean = isNotEmpty() && Character.isUpperCase(first())

private fun String.checkPackageName(): Boolean = isNotEmpty() && Character.isLowerCase(first())

internal fun getTargetParentsByQualifier(
    element: KtElement,
    isQualified: Boolean,
    qualifierDescriptor: DeclarationDescriptor?
): List<PsiElement> {
    val file = element.containingKtFile
    val project = file.project
    val targetParents: List<PsiElement> = when {
        !isQualified ->
            element.parents.filterIsInstance<KtClassOrObject>().toList() + file
        qualifierDescriptor is ClassDescriptor ->
            listOfNotNull(DescriptorToSourceUtilsIde.getAnyDeclaration(project, qualifierDescriptor))
        qualifierDescriptor is PackageViewDescriptor ->
            if (qualifierDescriptor.fqName != file.packageFqName) {
                listOfNotNull(JavaPsiFacade.getInstance(project).findPackage(qualifierDescriptor.fqName.asString()))
            } else listOf(file)
        else ->
            emptyList()
    }
    return targetParents.filter { it.canRefactor() }
}

internal fun getTargetParentsByCall(call: Call, context: BindingContext): List<PsiElement> {
    val callElement = call.callElement
    val receiver = call.explicitReceiver
    return when (receiver) {
        null -> getTargetParentsByQualifier(callElement, false, null)
        is Qualifier -> getTargetParentsByQualifier(
            callElement,
            true,
            context[BindingContext.REFERENCE_TARGET, receiver.referenceExpression]
        )
        is ReceiverValue -> getTargetParentsByQualifier(callElement, true, receiver.type.constructor.declarationDescriptor)
        else -> throw AssertionError("Unexpected receiver: $receiver")
    }
}

internal fun isInnerClassExpected(call: Call) = call.explicitReceiver is ReceiverValue

internal fun KtExpression.guessTypeForClass(context: BindingContext, moduleDescriptor: ModuleDescriptor) =
    guessTypes(context, moduleDescriptor, coerceUnusedToUnit = false).singleOrNull()

internal fun KotlinType.toClassTypeInfo(): TypeInfo {
    return TypeInfo.ByType(this, Variance.OUT_VARIANCE).noSubstitutions()
}

internal fun getClassKindFilter(expectedType: KotlinType, containingDeclaration: PsiElement): (ClassKind) -> Boolean {
    if (expectedType.isAnyOrNullableAny()) {
        return { _ -> true }
    }

    val descriptor = expectedType.constructor.declarationDescriptor ?: return { _ -> false }

    val canHaveSubtypes = !(expectedType.constructor.isFinal || expectedType.containsStarProjections()) || expectedType.isUnit()
    val isEnum = DescriptorUtils.isEnumClass(descriptor)

    if (!(canHaveSubtypes || isEnum)
        || descriptor is TypeParameterDescriptor
    ) return { _ -> false }

    return { classKind ->
        when (classKind) {
            ClassKind.ENUM_ENTRY -> isEnum && containingDeclaration == DescriptorToSourceUtils.descriptorToDeclaration(descriptor)
            ClassKind.INTERFACE -> containingDeclaration !is PsiClass
                    || (descriptor as? ClassDescriptor)?.kind == ClassDescriptorKind.INTERFACE
            else -> canHaveSubtypes
        }
    }
}

internal fun KtSimpleNameExpression.getCreatePackageFixIfApplicable(targetParent: PsiElement): IntentionAction? {
    val name = getReferencedName()
    if (!name.checkPackageName()) return null

    val basePackage: PsiPackage =
        when (targetParent) {
            is KtFile -> JavaPsiFacade.getInstance(targetParent.project).findPackage(targetParent.packageFqName.asString())
            is PsiPackage -> targetParent
            else -> null
        }
            ?: return null

    val baseName = basePackage.qualifiedName
    val fullName = if (baseName.isNotEmpty()) "$baseName.$name" else name

    val javaFix = CreateClassOrPackageFix.createFix(fullName, resolveScope, this, basePackage, null, null, null) ?: return null

    return object : DelegatingIntentionAction(javaFix) {
        override fun getFamilyName(): String = KotlinBundle.message("fix.create.from.usage.family")

        override fun getText(): String = KotlinBundle.message("create.package.0", fullName)
    }
}

data class UnsubstitutedTypeConstraintInfo(
    val typeParameter: TypeParameterDescriptor,
    private val originalSubstitution: Map<TypeConstructor, TypeProjection>,
    val upperBound: KotlinType
) {
    fun performSubstitution(vararg substitution: Pair<TypeConstructor, TypeProjection>): TypeConstraintInfo? {
        val currentSubstitution = LinkedHashMap<TypeConstructor, TypeProjection>().apply {
            this.putAll(originalSubstitution)
            this.putAll(substitution)
        }
        val substitutedUpperBound = TypeSubstitutor.create(currentSubstitution).substitute(upperBound, Variance.INVARIANT) ?: return null
        return TypeConstraintInfo(typeParameter, substitutedUpperBound)
    }
}

data class TypeConstraintInfo(
    val typeParameter: TypeParameterDescriptor,
    val upperBound: KotlinType
)

fun getUnsubstitutedTypeConstraintInfo(element: KtTypeElement): UnsubstitutedTypeConstraintInfo? {
    val context = element.analyze(BodyResolveMode.PARTIAL)
    val containingTypeArg = (element.parent as? KtTypeReference)?.parent as? KtTypeProjection ?: return null
    val argumentList = containingTypeArg.parent as? KtTypeArgumentList ?: return null
    val containingTypeRef = (argumentList.parent as? KtTypeElement)?.parent as? KtTypeReference ?: return null
    val containingType = containingTypeRef.getAbbreviatedTypeOrType(context) ?: return null
    val baseType = containingType.constructor.declarationDescriptor?.defaultType ?: return null
    val typeParameter = containingType.constructor.parameters.getOrNull(argumentList.arguments.indexOf(containingTypeArg))
    val upperBound = typeParameter?.upperBounds?.singleOrNull() ?: return null
    val substitution = getTypeSubstitution(baseType, containingType) ?: return null
    return UnsubstitutedTypeConstraintInfo(typeParameter, substitution, upperBound)
}

fun getTypeConstraintInfo(element: KtTypeElement) = getUnsubstitutedTypeConstraintInfo(element)?.performSubstitution()
