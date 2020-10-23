// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.dex.Constants;
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
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class AnnotationRemover {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final Set<DexAnnotation> annotationsToRetain;
  private final Set<DexType> classesToRetainInnerClassAttributeFor;
  private final ProguardKeepAttributes keep;
  private final Set<DexType> removedClasses;

  private AnnotationRemover(
      AppView<AppInfoWithLiveness> appView,
      Set<DexType> classesToRetainInnerClassAttributeFor,
      Set<DexAnnotation> annotationsToRetain,
      Set<DexType> removedClasses) {
    this.appView = appView;
    this.options = appView.options();
    this.annotationsToRetain = annotationsToRetain;
    this.classesToRetainInnerClassAttributeFor = classesToRetainInnerClassAttributeFor;
    this.keep = appView.options().getProguardConfiguration().getKeepAttributes();
    this.removedClasses = removedClasses;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Set<DexType> getClassesToRetainInnerClassAttributeFor() {
    return classesToRetainInnerClassAttributeFor;
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
        assert !DexAnnotation.isSignatureAnnotation(annotation, dexItemFactory);
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
        if (DexAnnotation.isSynthesizedClassMapAnnotation(annotation, dexItemFactory)) {
          // TODO(sgjesse) When should these be removed?
          return true;
        }
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

  private static boolean hasGenericEnclosingClass(
      DexProgramClass clazz,
      Map<DexType, DexProgramClass> enclosingClasses,
      Set<DexProgramClass> genericClasses) {
    while (true) {
      DexProgramClass enclosingClass = enclosingClasses.get(clazz.type);
      if (enclosingClass == null) {
        return false;
      }
      if (genericClasses.contains(enclosingClass)) {
        return true;
      }
      clazz = enclosingClass;
    }
  }

  public void run() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      stripAttributes(clazz);
      clazz.setAnnotations(
          clazz.annotations().rewrite(annotation -> rewriteAnnotation(clazz, annotation)));
      clazz.forEachMethod(method -> processMethod(method, clazz));
      clazz.forEachField(field -> processField(field, clazz));
    }
  }

  private void processMethod(DexEncodedMethod method, DexProgramClass clazz) {
    method.setAnnotations(
        method.annotations().rewrite(annotation -> rewriteAnnotation(method, annotation)));
    method.parameterAnnotationsList =
        method.parameterAnnotationsList.keepIf(this::filterParameterAnnotations);
    if (appView
        .getKeepInfo()
        .getMethodInfo(method, clazz)
        .isAllowSignatureAttributeRemovalAllowed(options)) {
      method.clearGenericSignature();
    }
  }

  private void processField(DexEncodedField field, DexProgramClass clazz) {
    field.setAnnotations(
        field.annotations().rewrite(annotation -> rewriteAnnotation(field, annotation)));
    if (appView
        .getKeepInfo()
        .getFieldInfo(field, clazz)
        .isAllowSignatureAttributeRemovalAllowed(options)) {
      field.clearGenericSignature();
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
            .hasVirtualMethods(method -> method.method.name == original.name);
    return liveGetter ? original : null;
  }

  private boolean enclosingMethodPinned(DexClass clazz) {
    return clazz.getEnclosingMethodAttribute() != null
        && clazz.getEnclosingMethodAttribute().getEnclosingClass() != null
        && appView.appInfo().isPinned(clazz.getEnclosingMethodAttribute().getEnclosingClass());
  }

  private static boolean hasInnerClassesFromSet(DexProgramClass clazz, Set<DexType> innerClasses) {
    for (InnerClassAttribute attr : clazz.getInnerClasses()) {
      if (attr.getOuter() == clazz.type && innerClasses.contains(attr.getInner())) {
        return true;
      }
    }
    return false;
  }

  private void stripAttributes(DexProgramClass clazz) {
    // If [clazz] is mentioned by a keep rule, it could be used for reflection, and we therefore
    // need to keep the enclosing method and inner classes attributes, if requested. In Proguard
    // compatibility mode we keep these attributes independent of whether the given class is kept.
    // To ensure reflection from both inner to outer and and outer to inner for kept classes - even
    // if only one side is kept - keep the attributes is any class mentioned in these attributes
    // is kept.
    boolean keptAnyway =
        appView.appInfo().isPinned(clazz.type)
            || enclosingMethodPinned(clazz)
            || appView.options().forceProguardCompatibility;
    boolean keepForThisInnerClass = false;
    boolean keepForThisEnclosingClass = false;
    if (!keptAnyway) {
      keepForThisInnerClass = classesToRetainInnerClassAttributeFor.contains(clazz.type);
      keepForThisEnclosingClass =
          hasInnerClassesFromSet(clazz, classesToRetainInnerClassAttributeFor);
    }
    if (keptAnyway || keepForThisInnerClass || keepForThisEnclosingClass) {
      if (!keep.enclosingMethod) {
        clazz.clearEnclosingMethodAttribute();
      }
      if (!keep.innerClasses) {
        clazz.clearInnerClasses();
      } else if (!keptAnyway) {
        // We're keeping this only because of classesToRetainInnerClassAttributeFor.
        final boolean finalKeepForThisInnerClass = keepForThisInnerClass;
        final boolean finalKeepForThisEnclosingClass = keepForThisEnclosingClass;
        clazz.removeInnerClasses(
            ica -> {
              if (appView.appInfo().isPinned(ica.getInner())) {
                return false;
              }
              DexType outer = ica.getOuter();
              if (outer != null && appView.appInfo().isPinned(outer)) {
                return false;
              }
              if (finalKeepForThisInnerClass && ica.getInner() == clazz.type) {
                return false;
              }
              if (finalKeepForThisEnclosingClass
                  && outer == clazz.type
                  && classesToRetainInnerClassAttributeFor.contains(ica.getInner())) {
                return false;
              }
              return true;
            });
      }
    } else {
      // These attributes are only relevant for reflection, and this class is not used for
      // reflection. (Note that clearing these attributes can enable more vertical class merging.)
      clazz.clearEnclosingMethodAttribute();
      clazz.clearInnerClasses();
    }
    if (appView
        .getKeepInfo()
        .getClassInfo(clazz)
        .isAllowSignatureAttributeRemovalAllowed(options)) {
      clazz.clearClassSignature();
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

    private Set<DexType> classesToRetainInnerClassAttributeFor;

    public Builder computeClassesToRetainInnerClassAttributeFor(
        AppView<AppInfoWithLiveness> appView) {
      assert classesToRetainInnerClassAttributeFor == null;
      // In case of minification for certain inner classes we need to retain their InnerClass
      // attributes because their minified name still needs to be in hierarchical format
      // (enclosing$inner) otherwise the GenericSignatureRewriter can't produce the correct,
      // renamed signature.

      // More precisely:
      // - we're going to retain the InnerClass attribute that refers to the same class as 'inner'
      // - for live, inner, nonstatic classes
      // - that are enclosed by a class with a generic signature.

      // In compat mode we always keep all InnerClass attributes (if requested).
      // If not requested we never keep any. In these cases don't compute eligible classes.
      Set<DexType> result = Sets.newIdentityHashSet();
      if (!appView.options().forceProguardCompatibility
          && appView.options().getProguardConfiguration().getKeepAttributes().innerClasses) {
        // Build lookup table and set of the interesting classes.
        // enclosingClasses.get(clazz) gives the enclosing class of 'clazz'
        Map<DexType, DexProgramClass> enclosingClasses = new IdentityHashMap<>();
        Set<DexProgramClass> genericClasses = Sets.newIdentityHashSet();
        for (DexProgramClass clazz : appView.appInfo().classes()) {
          if (clazz.getClassSignature().hasSignature()) {
            genericClasses.add(clazz);
          }
          for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
            if ((innerClassAttribute.getAccess() & Constants.ACC_STATIC) == 0
                && innerClassAttribute.getOuter() == clazz.type) {
              enclosingClasses.put(innerClassAttribute.getInner(), clazz);
            }
          }
        }
        for (DexProgramClass clazz : appView.appInfo().classes()) {
          // If [clazz] is mentioned by a keep rule, it could be used for reflection, and we
          // therefore need to keep the enclosing method and inner classes attributes, if requested.
          if (appView.appInfo().isPinned(clazz.type)) {
            for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
              DexType inner = innerClassAttribute.getInner();
              if (appView.appInfo().isNonProgramTypeOrLiveProgramType(inner)) {
                result.add(inner);
              }
              DexType context = innerClassAttribute.getLiveContext(appView);
              if (context != null && appView.appInfo().isNonProgramTypeOrLiveProgramType(context)) {
                result.add(context);
              }
            }
          }
          if (clazz.getInnerClassAttributeForThisClass() != null
              && appView.appInfo().isNonProgramTypeOrLiveProgramType(clazz.type)
              && hasGenericEnclosingClass(clazz, enclosingClasses, genericClasses)) {
            result.add(clazz.type);
          }
        }
      }
      classesToRetainInnerClassAttributeFor = result;
      return this;
    }

    public Builder setClassesToRetainInnerClassAttributeFor(
        Set<DexType> classesToRetainInnerClassAttributeFor) {
      this.classesToRetainInnerClassAttributeFor = classesToRetainInnerClassAttributeFor;
      return this;
    }

    public void retainAnnotation(DexAnnotation annotation) {
      annotationsToRetain.add(annotation);
    }

    public AnnotationRemover build(
        AppView<AppInfoWithLiveness> appView, Set<DexType> removedClasses) {
      assert classesToRetainInnerClassAttributeFor != null;
      return new AnnotationRemover(
          appView, classesToRetainInnerClassAttributeFor, annotationsToRetain, removedClasses);
    }
  }
}
