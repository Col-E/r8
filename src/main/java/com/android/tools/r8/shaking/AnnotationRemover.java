// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.kotlin.KotlinMemberLevelInfo;
import com.android.tools.r8.kotlin.KotlinPropertyInfo;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class AnnotationRemover {

  private final AppView<AppInfoWithLiveness> appView;
  private final Mode mode;
  private final InternalOptions options;
  private final Set<DexAnnotation> annotationsToRetain;
  private final ProguardKeepAttributes keep;
  private final Set<DexType> removedClasses;

  private AnnotationRemover(
      AppView<AppInfoWithLiveness> appView,
      Set<DexAnnotation> annotationsToRetain,
      Mode mode,
      Set<DexType> removedClasses) {
    this.appView = appView;
    this.mode = mode;
    this.options = appView.options();
    this.annotationsToRetain = annotationsToRetain;
    this.keep = appView.options().getProguardConfiguration().getKeepAttributes();
    this.removedClasses = removedClasses;
  }

  public static Builder builder(Mode mode) {
    return new Builder(mode);
  }

  /** Used to filter annotations on classes, methods and fields. */
  private boolean filterAnnotations(
      ProgramDefinition holder, DexAnnotation annotation, AnnotatedKind kind) {
    return annotationsToRetain.contains(annotation)
        || shouldKeepAnnotation(
            appView, holder, annotation, isAnnotationTypeLive(annotation), kind);
  }

  public static boolean shouldKeepAnnotation(
      AppView<?> appView,
      ProgramDefinition holder,
      DexAnnotation annotation,
      boolean isAnnotationTypeLive,
      AnnotatedKind kind) {
    // If we cannot run the AnnotationRemover we are keeping the annotation.
    if (!appView.options().isShrinking()) {
      return true;
    }

    InternalOptions options = appView.options();
    ProguardKeepAttributes config =
        options.getProguardConfiguration() != null
            ? options.getProguardConfiguration().getKeepAttributes()
            : ProguardKeepAttributes.fromPatterns(ImmutableList.of());

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    switch (annotation.visibility) {
      case DexAnnotation.VISIBILITY_SYSTEM:
        if (kind.isParameter()) {
          return false;
        }
        // InnerClass and EnclosingMember are represented in class attributes, not annotations.
        assert !DexAnnotation.isInnerClassAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isMemberClassesAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingMethodAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingClassAnnotation(annotation, dexItemFactory);
        assert options.passthroughDexCode
            || !DexAnnotation.isSignatureAnnotation(annotation, dexItemFactory);
        if (config.exceptions && DexAnnotation.isThrowingAnnotation(annotation, dexItemFactory)) {
          return true;
        }
        if (DexAnnotation.isSourceDebugExtension(annotation, dexItemFactory)) {
          assert holder.isProgramClass();
          appView.setSourceDebugExtensionForType(
              holder.asProgramClass(), annotation.annotation.elements[0].value.asDexValueString());
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
        // We always keep the @java.lang.Retention annotation on annotation classes, since the
        // removal of this annotation may change the annotation from being runtime visible to
        // runtime invisible.
        if (holder.isProgramClass()
            && holder.asProgramClass().isAnnotation()
            && DexAnnotation.isJavaLangRetentionAnnotation(annotation, dexItemFactory)) {
          return true;
        }

        if (kind.isParameter()) {
          if (!options.isKeepRuntimeVisibleParameterAnnotationsEnabled()) {
            return false;
          }
        } else if (!annotation.isTypeAnnotation()
            && !options.isKeepRuntimeVisibleAnnotationsEnabled()) {
          return false;
        } else if (annotation.isTypeAnnotation()
            && !options.isKeepRuntimeVisibleTypeAnnotationsEnabled()) {
          return false;
        }
        return isAnnotationTypeLive;

      case DexAnnotation.VISIBILITY_BUILD:
        if (annotation
            .getAnnotationType()
            .getDescriptor()
            .startsWith(options.itemFactory.dalvikAnnotationOptimizationPrefix)) {
          return true;
        }
        if (kind.isParameter()) {
          if (!options.isKeepRuntimeInvisibleParameterAnnotationsEnabled()) {
            return false;
          }
        } else if (!annotation.isTypeAnnotation()
            && !options.isKeepRuntimeInvisibleAnnotationsEnabled()) {
          return false;
        } else if (annotation.isTypeAnnotation()
            && !options.isKeepRuntimeInvisibleTypeAnnotationsEnabled()) {
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

  public AnnotationRemover ensureValid() {
    keep.ensureValid(appView.options().forceProguardCompatibility);
    return this;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(appView.appInfo().classes(), this::run, executorService);
    assert verifyNoKeptKotlinMembersForClassesWithNoKotlinInfo();
  }

  private void run(DexProgramClass clazz) {
    KeepClassInfo keepInfo = appView.getKeepInfo().getClassInfo(clazz);
    removeAnnotations(clazz, keepInfo);
    stripAttributes(clazz, keepInfo);
    // Kotlin metadata for classes are removed in the KotlinMetadataEnqueuerExtension. Kotlin
    // properties are split over fields and methods. Check if any is pinned before pruning the
    // information.
    Set<KotlinPropertyInfo> pinnedKotlinProperties = Sets.newIdentityHashSet();
    clazz.forEachProgramMember(member -> processMember(member, clazz, pinnedKotlinProperties));
    clazz.forEachProgramMember(
        member -> {
          KotlinMemberLevelInfo kotlinInfo = member.getKotlinInfo();
          if (kotlinInfo.isProperty()
              && !pinnedKotlinProperties.contains(kotlinInfo.asProperty())) {
            member.clearKotlinInfo();
          }
        });
  }

  private boolean verifyNoKeptKotlinMembersForClassesWithNoKotlinInfo() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.getKotlinInfo().isNoKotlinInformation()) {
        clazz.forEachProgramMember(
            member -> {
              assert member.getKotlinInfo().isNoKotlinInformation()
                  : "Should have pruned kotlin info";
            });
      }
    }
    return true;
  }

  private void processMember(
      ProgramMember<?, ?> member,
      DexProgramClass clazz,
      Set<KotlinPropertyInfo> pinnedKotlinProperties) {
    KeepMemberInfo<?, ?> memberInfo = appView.getKeepInfo().getMemberInfo(member);
    removeAnnotations(member, memberInfo);
    if (memberInfo.isSignatureRemovalAllowed(options)) {
      member.clearGenericSignature();
    }
    if (!member.getKotlinInfo().isProperty()
        && memberInfo.isKotlinMetadataRemovalAllowed(clazz, options)) {
      member.clearKotlinInfo();
    }
    // Postpone removal of kotlin property info until we have seen all fields, setters and getters.
    if (member.getKotlinInfo().isProperty()
        && !memberInfo.isKotlinMetadataRemovalAllowed(clazz, options)) {
      pinnedKotlinProperties.add(member.getKotlinInfo().asProperty());
    }
  }

  private DexAnnotation rewriteAnnotation(
      ProgramDefinition holder, DexAnnotation original, AnnotatedKind kind) {
    // Check if we should keep this annotation first.
    if (filterAnnotations(holder, original, kind)) {
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

  @SuppressWarnings("ReferenceEquality")
  private DexAnnotationElement rewriteAnnotationElement(
      DexType annotationType, DexAnnotationElement original) {
    // The dalvik.annotation.AnnotationDefault is typically not on bootclasspath. However, if it
    // is present, the definition does not define the 'value' getter but that is the spec:
    // https://source.android.com/devices/tech/dalvik/dex-format#dalvik-annotation-default
    // If the annotation matches the structural requirement keep it.
    if (appView.dexItemFactory().annotationDefault.equals(annotationType)
        && appView.dexItemFactory().valueString.equals(original.name)) {
      return original;
    }
    // We cannot strip annotations where we cannot look up the definition, because this will break
    // apps that rely on the annotation to exist. See b/134766810 for more information.
    DexClass definition = appView.definitionFor(annotationType);
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

  private void removeAnnotations(ProgramDefinition definition, KeepInfo<?, ?> keepInfo) {
    assert mode.isInitialTreeShaking() || annotationsToRetain.isEmpty();
    boolean isAnnotation =
        definition.isProgramClass() && definition.asProgramClass().isAnnotation();
    if (keepInfo.isAnnotationRemovalAllowed(options)) {
      if (isAnnotation) {
        definition.rewriteAllAnnotations(
            (annotation, isParameterAnnotation) ->
                shouldRetainAnnotationOnAnnotationClass(annotation) ? annotation : null);
      } else if (mode.isInitialTreeShaking()) {
        definition.rewriteAllAnnotations(
            (annotation, isParameterAnnotation) ->
                annotationsToRetain.contains(annotation) ? annotation : null);
      } else {
        definition.clearAllAnnotations();
      }
    } else {
      definition.rewriteAllAnnotations(
          (annotation, kind) -> rewriteAnnotation(definition, annotation, kind));
    }
  }

  private boolean shouldRetainAnnotationOnAnnotationClass(DexAnnotation annotation) {
    if (DexAnnotation.isAnnotationDefaultAnnotation(annotation, appView.dexItemFactory())) {
      return shouldRetainAnnotationDefaultAnnotationOnAnnotationClass(annotation);
    }
    if (DexAnnotation.isJavaLangRetentionAnnotation(annotation, appView.dexItemFactory())) {
      return shouldRetainRetentionAnnotationOnAnnotationClass(annotation);
    }
    return annotationsToRetain.contains(annotation);
  }

  @SuppressWarnings("UnusedVariable")
  private boolean shouldRetainAnnotationDefaultAnnotationOnAnnotationClass(
      DexAnnotation annotation) {
    // We currently always retain the @AnnotationDefault annotations for annotation classes. In full
    // mode we could consider only retaining @AnnotationDefault annotations for pinned annotations,
    // as this is consistent with removing all annotations for non-kept items.
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean shouldRetainRetentionAnnotationOnAnnotationClass(DexAnnotation annotation) {
    // Retain @Retention annotations that are different from @Retention(RetentionPolicy.CLASS).
    if (annotation.annotation.getNumberOfElements() != 1) {
      return true;
    }
    DexAnnotationElement element = annotation.annotation.getElement(0);
    if (element.name != appView.dexItemFactory().valueString) {
      return true;
    }
    DexValue value = element.getValue();
    if (!value.isDexValueEnum()
        || value.asDexValueEnum().getValue()
            != appView.dexItemFactory().javaLangAnnotationRetentionPolicyMembers.CLASS) {
      return true;
    }
    return false;
  }

  private void stripAttributes(DexProgramClass clazz, KeepClassInfo keepInfo) {
    // If [clazz] is mentioned by a keep rule, it could be used for reflection, and we therefore
    // need to keep the enclosing method and inner classes attributes, if requested. In Proguard
    // compatibility mode we keep these attributes independent of whether the given class is kept.
    // In full mode we remove the attribute if not both sides are kept.
    clazz.removeEnclosingMethodAttribute(
        enclosingMethodAttribute ->
            keepInfo.isEnclosingMethodAttributeRemovalAllowed(
                options, enclosingMethodAttribute, appView));
    // It is important that the call to getEnclosingMethodAttribute is done after we potentially
    // pruned it above.
    clazz.removeInnerClasses(
        attribute ->
            canRemoveInnerClassAttribute(clazz, attribute, clazz.getEnclosingMethodAttribute()));
    if (clazz.getClassSignature().isValid() && keepInfo.isSignatureRemovalAllowed(options)) {
      clazz.clearClassSignature();
    }
    if (keepInfo.isPermittedSubclassesRemovalAllowed(options)) {
      clazz.clearPermittedSubclasses();
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

    private final Mode mode;

    Builder(Mode mode) {
      this.mode = mode;
    }

    public boolean isRetainedForFinalTreeShaking(DexAnnotation annotation) {
      return annotationsToRetain.contains(annotation);
    }

    public void retainAnnotation(DexAnnotation annotation) {
      annotationsToRetain.add(annotation);
    }

    public AnnotationRemover build(
        AppView<AppInfoWithLiveness> appView, Set<DexType> removedClasses) {
      return new AnnotationRemover(appView, annotationsToRetain, mode, removedClasses);
    }
  }
}
