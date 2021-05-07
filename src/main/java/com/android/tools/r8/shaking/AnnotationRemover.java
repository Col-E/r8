// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.kotlin.KotlinMemberLevelInfo;
import com.android.tools.r8.kotlin.KotlinPropertyInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Set;

public class AnnotationRemover {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final Set<DexAnnotation> annotationsToRetain;
  private final ProguardKeepAttributes keep;
  private final Set<DexType> removedClasses;

  private AnnotationRemover(
      AppView<AppInfoWithLiveness> appView,
      Set<DexAnnotation> annotationsToRetain,
      Set<DexType> removedClasses) {
    this.appView = appView;
    this.options = appView.options();
    this.annotationsToRetain = annotationsToRetain;
    this.keep = appView.options().getProguardConfiguration().getKeepAttributes();
    this.removedClasses = removedClasses;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Used to filter annotations on classes, methods and fields. */
  private boolean filterAnnotations(DexDefinition holder, DexAnnotation annotation) {
    return annotationsToRetain.contains(annotation)
        || shouldKeepAnnotation(appView, holder, annotation, isAnnotationTypeLive(annotation));
  }

  public static boolean shouldKeepAnnotation(
      AppView<AppInfoWithLiveness> appView, DexDefinition holder, DexAnnotation annotation) {
    return shouldKeepAnnotation(
        appView, holder, annotation, isAnnotationTypeLive(annotation, appView));
  }

  public static boolean shouldKeepAnnotation(
      AppView<?> appView,
      DexDefinition holder,
      DexAnnotation annotation,
      boolean isAnnotationTypeLive) {
    // If we cannot run the AnnotationRemover we are keeping the annotation.
    if (!appView.options().isShrinking()) {
      return true;
    }
    ProguardKeepAttributes config =
        appView.options().getProguardConfiguration() != null
            ? appView.options().getProguardConfiguration().getKeepAttributes()
            : ProguardKeepAttributes.fromPatterns(ImmutableList.of());

    DexItemFactory dexItemFactory = appView.dexItemFactory();

    switch (annotation.visibility) {
      case DexAnnotation.VISIBILITY_SYSTEM:
        // InnerClass and EnclosingMember are represented in class attributes, not annotations.
        assert !DexAnnotation.isInnerClassAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isMemberClassesAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingMethodAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingClassAnnotation(annotation, dexItemFactory);
        assert appView.options().passthroughDexCode
            || !DexAnnotation.isSignatureAnnotation(annotation, dexItemFactory);
        if (config.exceptions && DexAnnotation.isThrowingAnnotation(annotation, dexItemFactory)) {
          return true;
        }
        if (DexAnnotation.isSourceDebugExtension(annotation, dexItemFactory)) {
          assert holder.isDexClass();
          appView.setSourceDebugExtensionForType(
              holder.asDexClass(), annotation.annotation.elements[0].value.asDexValueString());
          return config.sourceDebugExtension;
        }
        if (config.methodParameters
            && DexAnnotation.isParameterNameAnnotation(annotation, dexItemFactory)) {
          return true;
        }
        if (DexAnnotation.isAnnotationDefaultAnnotation(annotation, dexItemFactory)) {
          // These have to be kept if the corresponding annotation class is kept to retain default
          // values.
          return true;
        }
        return false;

      case DexAnnotation.VISIBILITY_RUNTIME:
        if (!config.runtimeVisibleAnnotations) {
          return false;
        }
        return isAnnotationTypeLive;

      case DexAnnotation.VISIBILITY_BUILD:
        if (!config.runtimeInvisibleAnnotations) {
          return false;
        }
        return isAnnotationTypeLive;

      default:
        throw new Unreachable("Unexpected annotation visibility.");
    }
  }

  private boolean isAnnotationTypeLive(DexAnnotation annotation) {
    return isAnnotationTypeLive(annotation, appView);
  }

  private static boolean isAnnotationTypeLive(
      DexAnnotation annotation, AppView<AppInfoWithLiveness> appView) {
    DexType annotationType = annotation.annotation.type.toBaseType(appView.dexItemFactory());
    return appView.appInfo().isNonProgramTypeOrLiveProgramType(annotationType);
  }

  /**
   * Used to filter annotations on parameters.
   */
  private boolean filterParameterAnnotations(DexAnnotation annotation) {
    if (annotationsToRetain.contains(annotation)) {
      return true;
    }
    switch (annotation.visibility) {
      case DexAnnotation.VISIBILITY_SYSTEM:
        return false;
      case DexAnnotation.VISIBILITY_RUNTIME:
        if (!keep.runtimeVisibleParameterAnnotations) {
          return false;
        }
        break;
      case DexAnnotation.VISIBILITY_BUILD:
        if (!keep.runtimeInvisibleParameterAnnotations) {
          return false;
        }
        break;
      default:
        throw new Unreachable("Unexpected annotation visibility.");
    }
    return isAnnotationTypeLive(annotation);
  }

  public AnnotationRemover ensureValid() {
    keep.ensureValid(appView.options().forceProguardCompatibility);
    return this;
  }

  public void run() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      stripAttributes(clazz);
      clazz.setAnnotations(
          clazz.annotations().rewrite(annotation -> rewriteAnnotation(clazz, annotation)));
      // Kotlin properties are split over fields and methods. Check if any is pinned before pruning
      // the information.
      Set<KotlinPropertyInfo> pinnedKotlinProperties = Sets.newIdentityHashSet();
      clazz.forEachMethod(method -> processMethod(method, clazz, pinnedKotlinProperties));
      clazz.forEachField(field -> processField(field, clazz, pinnedKotlinProperties));
      clazz.forEachProgramMember(
          member -> {
            KotlinMemberLevelInfo kotlinInfo = member.getKotlinInfo();
            if (kotlinInfo.isProperty()
                && !pinnedKotlinProperties.contains(kotlinInfo.asProperty())) {
              member.clearKotlinInfo();
            }
          });
    }
  }

  private void processMethod(
      DexEncodedMethod method,
      DexProgramClass clazz,
      Set<KotlinPropertyInfo> pinnedKotlinProperties) {
    method.setAnnotations(
        method.annotations().rewrite(annotation -> rewriteAnnotation(method, annotation)));
    method.parameterAnnotationsList =
        method.parameterAnnotationsList.keepIf(this::filterParameterAnnotations);
    KeepMethodInfo methodInfo = appView.getKeepInfo().getMethodInfo(method, clazz);
    if (methodInfo.isSignatureAttributeRemovalAllowed(options)) {
      method.clearGenericSignature();
    }
    if (!methodInfo.isPinned() && method.getKotlinInfo().isFunction()) {
      method.clearKotlinMemberInfo();
    }
    if (methodInfo.isPinned() && method.getKotlinInfo().isProperty()) {
      pinnedKotlinProperties.add(method.getKotlinInfo().asProperty());
    }
  }

  private void processField(
      DexEncodedField field,
      DexProgramClass clazz,
      Set<KotlinPropertyInfo> pinnedKotlinProperties) {
    field.setAnnotations(
        field.annotations().rewrite(annotation -> rewriteAnnotation(field, annotation)));
    KeepFieldInfo fieldInfo = appView.getKeepInfo().getFieldInfo(field, clazz);
    if (fieldInfo.isSignatureAttributeRemovalAllowed(options)) {
      field.clearGenericSignature();
    }
    if (fieldInfo.isPinned() && field.getKotlinInfo().isProperty()) {
      pinnedKotlinProperties.add(field.getKotlinInfo().asProperty());
    }
  }

  private DexAnnotation rewriteAnnotation(DexDefinition holder, DexAnnotation original) {
    // Check if we should keep this annotation first.
    if (filterAnnotations(holder, original)) {
      // Then, filter out values that refer to dead definitions.
      return original.rewrite(this::rewriteEncodedAnnotation);
    }
    return null;
  }

  private DexEncodedAnnotation rewriteEncodedAnnotation(DexEncodedAnnotation original) {
    GraphLens graphLens = appView.graphLens();
    DexType annotationType = original.type.toBaseType(appView.dexItemFactory());
    if (removedClasses.contains(annotationType)) {
      return null;
    }
    DexType rewrittenType = graphLens.lookupType(annotationType);
    DexEncodedAnnotation rewrite =
        original.rewrite(
            graphLens::lookupType, element -> rewriteAnnotationElement(rewrittenType, element));
    assert rewrite != null;
    DexClass annotationClass = appView.appInfo().definitionFor(rewrittenType);
    assert annotationClass == null
        || appView.appInfo().isNonProgramTypeOrLiveProgramType(rewrittenType);
    return rewrite;
  }

  private DexAnnotationElement rewriteAnnotationElement(
      DexType annotationType, DexAnnotationElement original) {
    DexClass definition = appView.definitionFor(annotationType);
    // We cannot strip annotations where we cannot look up the definition, because this will break
    // apps that rely on the annotation to exist. See b/134766810 for more information.
    if (definition == null) {
      return original;
    }
    assert definition.isInterface();
    boolean liveGetter =
        definition
            .getMethodCollection()
            .hasVirtualMethods(method -> method.getReference().name == original.name);
    return liveGetter ? original : null;
  }

  private void stripAttributes(DexProgramClass clazz) {
    // If [clazz] is mentioned by a keep rule, it could be used for reflection, and we therefore
    // need to keep the enclosing method and inner classes attributes, if requested. In Proguard
    // compatibility mode we keep these attributes independent of whether the given class is kept.
    // In full mode we remove the attribute if not both sides are kept.
    clazz.removeEnclosingMethodAttribute(
        enclosingMethodAttribute ->
            appView
                .getKeepInfo()
                .getClassInfo(clazz)
                .isEnclosingMethodAttributeRemovalAllowed(
                    options, enclosingMethodAttribute, appView));
    // It is important that the call to getEnclosingMethodAttribute is done after we potentially
    // pruned it above.
    clazz.removeInnerClasses(
        attribute ->
            canRemoveInnerClassAttribute(clazz, attribute, clazz.getEnclosingMethodAttribute()));
    if (clazz.getClassSignature().isValid()
        && appView.getKeepInfo().getClassInfo(clazz).isSignatureAttributeRemovalAllowed(options)) {
      clazz.clearClassSignature();
    }
  }

  private boolean canRemoveInnerClassAttribute(
      DexProgramClass clazz,
      InnerClassAttribute innerClassAttribute,
      EnclosingMethodAttribute enclosingAttributeMethod) {
    if (innerClassAttribute.getOuter() == null
        && (clazz.isLocalClass() || clazz.isAnonymousClass())) {
      // This is a class that has an enclosing method attribute and the inner class attribute
      // is related to the enclosed method.
      assert innerClassAttribute.getInner() != null;
      return appView
          .getKeepInfo()
          .getClassInfo(innerClassAttribute.getInner(), appView)
          .isInnerClassesAttributeRemovalAllowed(options, enclosingAttributeMethod);
    } else if (innerClassAttribute.getOuter() == null) {
      assert !clazz.isLocalClass() && !clazz.isAnonymousClass();
      return appView
          .getKeepInfo()
          .getClassInfo(innerClassAttribute.getInner(), appView)
          .isInnerClassesAttributeRemovalAllowed(options);
    } else if (innerClassAttribute.getInner() == null) {
      assert innerClassAttribute.getOuter() != null;
      return appView
          .getKeepInfo()
          .getClassInfo(innerClassAttribute.getOuter(), appView)
          .isInnerClassesAttributeRemovalAllowed(options);
    } else {
      // If both inner and outer is specified, only keep if both are pinned.
      assert innerClassAttribute.getOuter() != null && innerClassAttribute.getInner() != null;
      return appView
              .getKeepInfo()
              .getClassInfo(innerClassAttribute.getInner(), appView)
              .isInnerClassesAttributeRemovalAllowed(options)
          || appView
              .getKeepInfo()
              .getClassInfo(innerClassAttribute.getOuter(), appView)
              .isInnerClassesAttributeRemovalAllowed(options);
    }
  }

  public static void clearAnnotations(AppView<?> appView) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.clearAnnotations();
      clazz.members().forEach(DexDefinition::clearAnnotations);
    }
  }

  public static class Builder {

    /**
     * The set of annotations that were matched by a conditional if rule. These are needed for the
     * interpretation of if rules in the second round of tree shaking.
     */
    private final Set<DexAnnotation> annotationsToRetain = Sets.newIdentityHashSet();

    public void retainAnnotation(DexAnnotation annotation) {
      annotationsToRetain.add(annotation);
    }

    public AnnotationRemover build(
        AppView<AppInfoWithLiveness> appView, Set<DexType> removedClasses) {
      return new AnnotationRemover(appView, annotationsToRetain, removedClasses);
    }
  }
}
