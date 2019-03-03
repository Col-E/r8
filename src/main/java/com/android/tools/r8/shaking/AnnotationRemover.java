// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnnotationRemover {

  private final AppInfoWithLiveness appInfo;
  private final GraphLense lense;
  private final ProguardKeepAttributes keep;
  private final InternalOptions options;
  private final Set<DexType> classesToRetainInnerClassAttributeFor;

  public AnnotationRemover(
      AppInfoWithLiveness appInfo,
      GraphLense lense,
      InternalOptions options,
      Set<DexType> classesToRetainInnerClassAttributeFor) {
    this.appInfo = appInfo;
    this.lense = lense;
    this.keep = options.getProguardConfiguration().getKeepAttributes();
    this.options = options;
    this.classesToRetainInnerClassAttributeFor = classesToRetainInnerClassAttributeFor;
  }

  /**
   * Used to filter annotations on classes, methods and fields.
   */
  private boolean filterAnnotations(DexAnnotation annotation) {
    return shouldKeepAnnotation(
        annotation, isAnnotationTypeLive(annotation), appInfo.dexItemFactory, options);
  }

  static boolean shouldKeepAnnotation(
      DexAnnotation annotation,
      boolean isAnnotationTypeLive,
      DexItemFactory dexItemFactory,
      InternalOptions options) {
    ProguardKeepAttributes config =
        options.getProguardConfiguration() != null
            ? options.getProguardConfiguration().getKeepAttributes()
            : ProguardKeepAttributes.fromPatterns(ImmutableList.of());

    switch (annotation.visibility) {
      case DexAnnotation.VISIBILITY_SYSTEM:
        // InnerClass and EnclosingMember are represented in class attributes, not annotations.
        assert !DexAnnotation.isInnerClassAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isMemberClassesAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingMethodAnnotation(annotation, dexItemFactory);
        assert !DexAnnotation.isEnclosingClassAnnotation(annotation, dexItemFactory);
        if (config.exceptions && DexAnnotation.isThrowingAnnotation(annotation, dexItemFactory)) {
          return true;
        }
        if (config.signature && DexAnnotation.isSignatureAnnotation(annotation, dexItemFactory)) {
          return true;
        }
        if (config.sourceDebugExtension
            && DexAnnotation.isSourceDebugExtension(annotation, dexItemFactory)) {
          return true;
        }
        if (options.canUseParameterNameAnnotations()
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
    DexType annotationType = annotation.annotation.type.toBaseType(appInfo.dexItemFactory);
    DexClass definition = appInfo.definitionFor(annotationType);
    // TODO(b/73102187): How to handle annotations without definition.
    if (options.enableTreeShaking && definition == null) {
      return false;
    }
    return definition == null || definition.isLibraryClass()
        || appInfo.liveTypes.contains(annotationType);
  }

  /**
   * Used to filter annotations on parameters.
   */
  private boolean filterParameterAnnotations(DexAnnotation annotation) {
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

  public AnnotationRemover ensureValid(ProguardConfiguration.Builder compatibility) {
    keep.ensureValid(options.forceProguardCompatibility, compatibility);
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

  private static boolean hasSignatureAnnotation(DexProgramClass clazz, DexItemFactory itemFactory) {
    for (DexAnnotation annotation : clazz.annotations.annotations) {
      if (DexAnnotation.isSignatureAnnotation(annotation, itemFactory)) {
        return true;
      }
    }
    return false;
  }

  public static Set<DexType> computeClassesToRetainInnerClassAttributeFor(
      AppInfoWithLiveness appInfo, InternalOptions options) {
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
    if (options.forceProguardCompatibility
        || !options.getProguardConfiguration().getKeepAttributes().innerClasses) {
      return Collections.emptySet();
    }

    // Build lookup table and set of the interesting classes.
    // enclosingClasses.get(clazz) gives the enclosing class of 'clazz'
    Map<DexType, DexProgramClass> enclosingClasses = new IdentityHashMap<>();
    Set<DexProgramClass> genericClasses = Sets.newIdentityHashSet();

    Iterable<DexProgramClass> programClasses = appInfo.classes();
    for (DexProgramClass clazz : programClasses) {
      if (hasSignatureAnnotation(clazz, options.itemFactory)) {
        genericClasses.add(clazz);
      }
      for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
        if ((innerClassAttribute.getAccess() & Constants.ACC_STATIC) == 0
            && innerClassAttribute.getOuter() == clazz.type) {
          enclosingClasses.put(innerClassAttribute.getInner(), clazz);
        }
      }
    }

    Set<DexType> result = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : programClasses) {
      if (clazz.getInnerClassAttributeForThisClass() != null
          && appInfo.liveTypes.contains(clazz.type)
          && hasGenericEnclosingClass(clazz, enclosingClasses, genericClasses)) {
        result.add(clazz.type);
      }
    }
    return result;
  }

  public void run() {
    for (DexProgramClass clazz : appInfo.classes()) {
      stripAttributes(clazz);
      clazz.annotations = clazz.annotations.rewrite(this::rewriteAnnotation);
      clazz.forEachMethod(this::processMethod);
      clazz.forEachField(this::processField);
    }
  }

  private void processMethod(DexEncodedMethod method) {
    method.annotations = method.annotations.rewrite(this::rewriteAnnotation);
    method.parameterAnnotationsList =
        method.parameterAnnotationsList.keepIf(this::filterParameterAnnotations);
  }

  private void processField(DexEncodedField field) {
    field.annotations = field.annotations.rewrite(this::rewriteAnnotation);
  }

  private DexAnnotation rewriteAnnotation(DexAnnotation original) {
    // Check if we should keep this annotation first.
    if (!filterAnnotations(original)) {
      return null;
    }
    // Then, filter out values that refer to dead definitions.
    return original.rewrite(this::rewriteEncodedAnnotation);
  }

  private DexEncodedAnnotation rewriteEncodedAnnotation(DexEncodedAnnotation original) {
    DexType annotationType = original.type.toBaseType(appInfo.dexItemFactory);
    return original.rewrite(
        lense::lookupType,
        element -> rewriteAnnotationElement(lense.lookupType(annotationType), element));
  }

  private DexAnnotationElement rewriteAnnotationElement(
      DexType annotationType, DexAnnotationElement original) {
    DexClass definition = appInfo.definitionFor(annotationType);
    // TODO(b/73102187): How to handle annotations without definition.
    if (definition == null) {
      return original;
    }
    assert definition.isInterface();
    boolean liveGetter =
        definition.virtualMethods().stream()
            .anyMatch(method -> method.method.name == original.name);
    return liveGetter ? original : null;
  }

  private boolean enclosingMethodPinned(DexClass clazz) {
    return clazz.getEnclosingMethod() != null
        && clazz.getEnclosingMethod().getEnclosingClass() != null
        && appInfo.isPinned(clazz.getEnclosingMethod().getEnclosingClass());
  }

  private boolean innerClassPinned(DexClass clazz) {
    List<InnerClassAttribute> innerClasses = clazz.getInnerClasses();
    for (InnerClassAttribute innerClass : innerClasses) {
      if (appInfo.isPinned(innerClass.getInner())) {
        return true;
      }
      if (appInfo.isPinned(innerClass.getOuter())) {
        return true;
      }
    }
    return false;
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
        appInfo.isPinned(clazz.type)
            || enclosingMethodPinned(clazz)
            || innerClassPinned(clazz)
            || options.forceProguardCompatibility;
    boolean keepForThisInnerClass = false;
    boolean keepForThisEnclosingClass = false;
    if (!keptAnyway) {
      keepForThisInnerClass = classesToRetainInnerClassAttributeFor.contains(clazz.type);
      keepForThisEnclosingClass =
          hasInnerClassesFromSet(clazz, classesToRetainInnerClassAttributeFor);
    }
    if (keptAnyway || keepForThisInnerClass || keepForThisEnclosingClass) {
      if (!keep.enclosingMethod) {
        clazz.clearEnclosingMethod();
      }
      if (!keep.innerClasses) {
        clazz.clearInnerClasses();
      } else if (!keptAnyway) {
        // We're keeping this only because of classesToRetainInnerClassAttributeFor.
        final boolean finalKeepForThisInnerClass = keepForThisInnerClass;
        final boolean finalKeepForThisEnclosingClass = keepForThisEnclosingClass;
        clazz.removeInnerClasses(
            ica -> {
              if (finalKeepForThisInnerClass && ica.getInner() == clazz.type) {
                return false;
              }
              if (finalKeepForThisEnclosingClass
                  && ica.getOuter() == clazz.type
                  && classesToRetainInnerClassAttributeFor.contains(ica.getInner())) {
                return false;
              }
              return true;
            });
      }
    } else {
      // These attributes are only relevant for reflection, and this class is not used for
      // reflection. (Note that clearing these attributes can enable more vertical class merging.)
      clazz.clearEnclosingMethod();
      clazz.clearInnerClasses();
    }
  }
}
