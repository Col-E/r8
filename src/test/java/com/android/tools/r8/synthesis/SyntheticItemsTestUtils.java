// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

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
    ClassReference syntheticHolder =
        syntheticClass(clazz, SyntheticNaming.SyntheticKind.BACKPORT, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        syntheticMethodName(),
        originalMethod.getMethodDescriptor());
  }

  public static ClassReference syntheticOutlineClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, SyntheticKind.OUTLINE, id);
  }

  public static ClassReference syntheticOutlineClass(ClassReference clazz, int id) {
    return syntheticClass(clazz, SyntheticKind.OUTLINE, id);
  }

  public static ClassReference syntheticLambdaClass(Class<?> clazz, int id) {
    return syntheticClass(clazz, SyntheticNaming.SyntheticKind.LAMBDA, id);
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
    return SyntheticNaming.isSynthetic(
        reference, null, SyntheticKind.ENUM_UNBOXING_SHARED_UTILITY_CLASS);
  }

  public static boolean isExternalSynthetic(ClassReference reference) {
    for (SyntheticKind kind : SyntheticKind.values()) {
      if (kind == SyntheticKind.RECORD_TAG
          || kind == SyntheticKind.EMULATED_INTERFACE_MARKER_CLASS) {
        continue;
      }
      if (kind.isFixedSuffixSynthetic) {
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
    return SyntheticNaming.isSynthetic(reference, Phase.INTERNAL, SyntheticKind.LAMBDA);
  }

  public static boolean isExternalLambda(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, SyntheticKind.LAMBDA);
  }

  public static boolean isExternalStaticInterfaceCall(ClassReference reference) {
    return SyntheticNaming.isSynthetic(
        reference, Phase.EXTERNAL, SyntheticKind.STATIC_INTERFACE_CALL);
  }

  public static boolean isExternalTwrCloseMethod(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, SyntheticKind.TWR_CLOSE_RESOURCE);
  }

  public static boolean isExternalOutlineClass(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, Phase.EXTERNAL, SyntheticKind.OUTLINE);
  }

  public static boolean isInitializerTypeArgument(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, SyntheticKind.INIT_TYPE_ARGUMENT);
  }

  public static boolean isHorizontalInitializerTypeArgument(ClassReference reference) {
    return SyntheticNaming.isSynthetic(
            reference, null, SyntheticKind.HORIZONTAL_INIT_TYPE_ARGUMENT_1)
        || SyntheticNaming.isSynthetic(
            reference, null, SyntheticKind.HORIZONTAL_INIT_TYPE_ARGUMENT_2)
        || SyntheticNaming.isSynthetic(
            reference, null, SyntheticKind.HORIZONTAL_INIT_TYPE_ARGUMENT_3);
  }

  public static boolean isWrapper(ClassReference reference) {
    return SyntheticNaming.isSynthetic(reference, null, SyntheticKind.WRAPPER)
        || SyntheticNaming.isSynthetic(reference, null, SyntheticKind.VIVIFIED_WRAPPER);
  }

  public static Matcher<String> containsInternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.INTERNAL));
  }

  public static Matcher<String> containsExternalSyntheticReference() {
    return containsString(SyntheticNaming.getPhaseSeparator(Phase.EXTERNAL));
  }

  public static boolean isInternalThrowNSME(MethodReference method) {
    return SyntheticNaming.isSynthetic(
        method.getHolderClass(), Phase.INTERNAL, SyntheticKind.THROW_NSME);
  }
}
