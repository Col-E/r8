// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;

public class AnnotationRemover {

  private final AppInfoWithLiveness appInfo;
  private final ProguardKeepAttributes keep;
  private final InternalOptions options;
  private final ProguardConfiguration.Builder compatibility;

  public AnnotationRemover(AppInfoWithLiveness appInfo,
      ProguardConfiguration.Builder compatibility, InternalOptions options) {
    this.appInfo = appInfo;
    this.keep = options.proguardConfiguration.getKeepAttributes();
    this.compatibility = compatibility;
    this.options = options;
  }

  /**
   * Used to filter annotations on classes, methods and fields.
   */
  private boolean filterAnnotations(DexAnnotation annotation) {
    switch (annotation.visibility) {
      case DexAnnotation.VISIBILITY_SYSTEM:
        DexItemFactory factory = appInfo.dexItemFactory;
        // InnerClass and EnclosingMember are represented in class attributes, not annotations.
        assert !DexAnnotation.isInnerClassAnnotation(annotation, factory);
        assert !DexAnnotation.isMemberClassesAnnotation(annotation, factory);
        assert !DexAnnotation.isEnclosingMethodAnnotation(annotation, factory);
        assert !DexAnnotation.isEnclosingClassAnnotation(annotation, factory);
        if (keep.exceptions && DexAnnotation.isThrowingAnnotation(annotation, factory)) {
          return true;
        }
        if (keep.signature && DexAnnotation.isSignatureAnnotation(annotation, factory)) {
          return true;
        }
        if (keep.sourceDebugExtension
            && DexAnnotation.isSourceDebugExtension(annotation, factory)) {
          return true;
        }
        if (options.canUseParameterNameAnnotations()
            && DexAnnotation.isParameterNameAnnotation(annotation, factory)) {
          return true;
        }
        if (DexAnnotation.isAnnotationDefaultAnnotation(annotation, factory)) {
          // These have to be kept if the corresponding annotation class is kept to retain default
          // values.
          return true;
        }
        return false;
      case DexAnnotation.VISIBILITY_RUNTIME:
        if (!keep.runtimeVisibleAnnotations) {
          return false;
        }
        break;
      case DexAnnotation.VISIBILITY_BUILD:
        if (DexAnnotation.isSynthesizedClassMapAnnotation(annotation, appInfo.dexItemFactory)) {
          // TODO(sgjesse) When should these be removed?
          return true;
        }
        if (!keep.runtimeInvisibleAnnotations) {
          return false;
        }
        break;
      default:
        throw new Unreachable("Unexpected annotation visibility.");
    }
    return isAnnotationTypeLive(annotation);
  }

  private boolean isAnnotationTypeLive(DexAnnotation annotation) {
    DexType annotationType = annotation.annotation.type.toBaseType(appInfo.dexItemFactory);
    DexClass definition = appInfo.definitionFor(annotationType);
    // TODO(73102187): How to handle annotations without definition.
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

  public void run() {
    keep.ensureValid(options.forceProguardCompatibility, compatibility);
    for (DexProgramClass clazz : appInfo.classes()) {
      stripAttributes(clazz);
      clazz.annotations = clazz.annotations.keepIf(this::filterAnnotations);
      clazz.forEachMethod(this::processMethod);
      clazz.forEachField(this::processField);
    }
  }

  private void processMethod(DexEncodedMethod method) {
    method.annotations = method.annotations.keepIf(this::filterAnnotations);
    method.parameterAnnotationsList =
        method.parameterAnnotationsList.keepIf(this::filterParameterAnnotations);
  }

  private void processField(DexEncodedField field) {
    field.annotations = field.annotations.keepIf(this::filterAnnotations);
  }

  private void stripAttributes(DexProgramClass clazz) {
    // If [clazz] is mentioned by a keep rule, it could be used for reflection, and we therefore
    // need to keep the enclosing method and inner classes attributes, if requested. In Proguard
    // compatibility mode we keep these attributes independent of whether the given class is kept.
    if (appInfo.isPinned(clazz.type) || options.forceProguardCompatibility) {
      if (!keep.enclosingMethod) {
        clazz.clearEnclosingMethod();
      }
      if (!keep.innerClasses) {
        clazz.clearInnerClasses();
      }
    } else {
      // These attributes are only relevant for reflection, and this class is not used for
      // reflection. (Note that clearing these attributes can enable more vertical class merging.)
      clazz.clearEnclosingMethod();
      clazz.clearInnerClasses();
    }
  }

}
