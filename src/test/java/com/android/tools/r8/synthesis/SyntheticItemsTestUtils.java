// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.synthesis.SyntheticNaming.EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticNaming.Phase;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.lang.reflect.Method;
import org.hamcrest.Matcher;

public class SyntheticItemsTestUtils {

  // Private copy of the synthetic namings. This is not the compiler instance, but checking on the
  // id/descriptor content is safe.
  private static final SyntheticNaming naming = new SyntheticNaming();

  public static String syntheticMethodName() {
    return SyntheticNaming.INTERNAL_SYNTHETIC_METHOD_NAME;
  }

  public static ClassReference syntheticCompanionClass(Class<?> clazz) {
    return syntheticCompanionClass(Reference.classFromClass(clazz));
  }

  public static ClassReference syntheticCompanionClass(ClassReference clazz) {
    return Reference.classFromDescriptor(
        InterfaceDesugaringForTesting.getCompanionClassDescriptor(clazz.getDescriptor()));
  }

  private static ClassReference syntheticClass(Class<?> clazz, SyntheticKind kind, int id) {
    return syntheticClass(Reference.classFromClass(clazz), kind, id);
  }

  private static ClassReference syntheticClass(ClassReference clazz, SyntheticKind kind, int id) {
    return SyntheticNaming.makeSyntheticReferenceForTest(clazz, kind, "" + id);
  }

  public static MethodReference syntheticBackportMethod(Class<?> clazz, int id, Method method) {
    ClassReference syntheticHolder = syntheticBackportClass(clazz, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        syntheticMethodName(),
        originalMethod.getMethodDescriptor());
  }

  public static ClassReference syntheticOutlineClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, naming.OUTLINE, id);
  }

  public static ClassReference syntheticOutlineClass(ClassReference clazz, int id) {
    return syntheticClass(clazz, naming.OUTLINE, id);
  }

  public static ClassReference syntheticLambdaClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, naming.LAMBDA, id);
  }

  public static ClassReference syntheticApiOutlineClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, naming.API_MODEL_OUTLINE, id);
  }

  public static ClassReference syntheticApiOutlineClass(ClassReference classReference, int id) {
    return syntheticClass(classReference, naming.API_MODEL_OUTLINE, id);
  }

  public static String syntheticApiOutlineClassPrefix(Class<?> clazz) {
    return clazz.getTypeName()
        + EXTERNAL_SYNTHETIC_CLASS_SEPARATOR
        + naming.API_MODEL_OUTLINE.getDescriptor();
  }

  public static ClassReference syntheticBackportClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, naming.BACKPORT, id);
  }

  public static ClassReference syntheticBackportClass(ClassReference classReference, int id) {
    return syntheticClass(classReference, naming.BACKPORT, id);
  }

  public static ClassReference syntheticTwrCloseResourceClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, naming.TWR_CLOSE_RESOURCE, id);
  }

  public static ClassReference syntheticTwrCloseResourceClass(ClassReference reference, int id) {
    return syntheticClass(reference, naming.TWR_CLOSE_RESOURCE, id);
  }

  public static MethodReference syntheticLambdaMethod(Class<?> clazz, int id, Method method) {
    ClassReference syntheticHolder = syntheticLambdaClass(clazz, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        originalMethod.getMethodName(),
        originalMethod.getMethodDescriptor());
  }

  public static boolean isEnumUnboxingSharedUtilityClass(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, naming.ENUM_UNBOXING_SHARED_UTILITY_CLASS);
  }

  public static boolean isExternalSynthetic(ClassReference reference) {
    for (SyntheticKind kind : naming.kinds()) {
      if (kind.isGlobal()) {
        continue;
      }
      if (kind.isFixedSuffixSynthetic()) {
        if (SyntheticNaming.isSynthetic(reference, null, kind)) {
          return true;
        }
      } else {
        if (SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, kind)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInternalLambda(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.INTERNAL, naming.LAMBDA);
  }

  public static boolean isExternalLambda(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.LAMBDA);
  }

  public static boolean isExternalStaticInterfaceCall(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.STATIC_INTERFACE_CALL);
  }

  public static boolean isExternalTwrCloseMethod(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.TWR_CLOSE_RESOURCE);
  }

  public static boolean isMaybeExternalSuppressedExceptionMethod(ClassReference reference) {
    // The suppressed exception methods are grouped with the backports.
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.BACKPORT);
  }

  public static boolean isExternalOutlineClass(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, naming.OUTLINE);
  }

  public static boolean isInitializerTypeArgument(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, naming.INIT_TYPE_ARGUMENT);
  }

  public static boolean isExternalNonFixedInitializerTypeArgument(ClassReference reference) {
    return SyntheticNaming.isSynthetic(
        reference, Phase.EXTERNAL, naming.NON_FIXED_INIT_TYPE_ARGUMENT);
  }

  public static boolean isHorizontalInitializerTypeArgument(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, naming.HORIZONTAL_INIT_TYPE_ARGUMENT_1)
        || SyntheticNaming.isSynthetic(reference, null, naming.HORIZONTAL_INIT_TYPE_ARGUMENT_2)
        || SyntheticNaming.isSynthetic(reference, null, naming.HORIZONTAL_INIT_TYPE_ARGUMENT_3);
  }

  public static boolean isWrapper(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, naming.WRAPPER)
        || SyntheticNaming.isSynthetic(reference, null, naming.VIVIFIED_WRAPPER);
  }

  public static Matcher<String> containsInternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.INTERNAL));
  }

  public static Matcher<String> containsExternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.EXTERNAL));
  }

  public static boolean isInternalThrowNSME(MethodReference method) {
    return SyntheticNaming.isSynthetic(method.getHolderClass(), Phase.INTERNAL, naming.THROW_NSME);
  }
}
