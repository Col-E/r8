// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda.kstyle;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.ir.optimize.lambda.CaptureSignature;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup.LambdaStructureError;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroupId;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;

public final class KStyleLambdaGroupIdFactory implements KStyleConstants {
  private KStyleLambdaGroupIdFactory() {
  }

  // Creates a lambda group id for kotlin style lambda. Should never return null, if the lambda
  // does not pass pre-requirements (mostly by not meeting high-level structure expectations)
  // should throw LambdaStructureError leaving the caller to decide if/how it needs to be reported.
  //
  // At this point we only perform high-level checks before qualifying the lambda as a candidate
  // for merging and assigning lambda group id. We can NOT perform checks on method bodies since
  // they may not be converted yet, we'll do that in KStyleLambdaClassValidator.
  public static LambdaGroupId create(Kotlin kotlin, DexClass lambda, InternalOptions options)
      throws LambdaStructureError {
    boolean accessRelaxed = options.proguardConfiguration.isAccessModificationAllowed();

    checkAccessFlags("class access flags", lambda.accessFlags,
        PUBLIC_LAMBDA_CLASS_FLAGS, LAMBDA_CLASS_FLAGS);

    validateStaticFields(kotlin, lambda);
    String captureSignature = validateInstanceFields(lambda, accessRelaxed);
    validateDirectMethods(lambda);
    DexEncodedMethod mainMethod = validateVirtualMethods(lambda);
    DexType iface = validateInterfaces(kotlin, lambda);
    String genericSignature = validateAnnotations(kotlin, lambda);
    InnerClassAttribute innerClass = validateInnerClasses(lambda);

    return new KStyleLambdaGroupId(captureSignature, iface,
        accessRelaxed ? "" : lambda.type.getPackageDescriptor(),
        genericSignature, mainMethod, innerClass, lambda.getEnclosingMethod());
  }

  private static DexEncodedMethod validateVirtualMethods(DexClass lambda)
      throws LambdaStructureError {
    DexEncodedMethod mainMethod = null;

    for (DexEncodedMethod method : lambda.virtualMethods()) {
      if (method.accessFlags.equals(MAIN_METHOD_FLAGS)) {
        if (mainMethod != null) {
          throw new LambdaStructureError("more than one main method found");
        }
        mainMethod = method;
      } else {
        checkAccessFlags("unexpected virtual method access flags",
            method.accessFlags, BRIDGE_METHOD_FLAGS, BRIDGE_METHOD_FLAGS_FIXED);
        checkDirectMethodAnnotations(method);
      }
    }

    if (mainMethod == null) {
      throw new LambdaStructureError("no main method found");
    }
    return mainMethod;
  }

  private static InnerClassAttribute validateInnerClasses(DexClass lambda)
      throws LambdaStructureError {
    List<InnerClassAttribute> innerClasses = lambda.getInnerClasses();
    InnerClassAttribute innerClass = null;
    if (innerClasses != null) {
      for (InnerClassAttribute inner : innerClasses) {
        if (inner.getInner() == lambda.type) {
          innerClass = inner;
          if (!innerClass.isAnonymous()) {
            throw new LambdaStructureError("is not anonymous");
          }
          return innerClass;
        }
      }
    }
    return null;
  }

  private static String validateAnnotations(Kotlin kotlin, DexClass lambda)
      throws LambdaStructureError {
    String signature = null;
    if (!lambda.annotations.isEmpty()) {
      for (DexAnnotation annotation : lambda.annotations.annotations) {
        if (DexAnnotation.isSignatureAnnotation(annotation, kotlin.factory)) {
          signature = DexAnnotation.getSignature(annotation);
          continue;
        }

        if (annotation.annotation.type == kotlin.metadata.kotlinMetadataType) {
          // Ignore kotlin metadata on lambda classes. Metadata on synthetic
          // classes exists but is not used in the current Kotlin version (1.2.21)
          // and newly generated lambda _group_ class is not exactly a kotlin class.
          continue;
        }

        throw new LambdaStructureError(
            "unexpected annotation: " + annotation.annotation.type.toSourceString());
      }
    }
    return signature;
  }

  private static void validateStaticFields(Kotlin kotlin, DexClass lambda)
      throws LambdaStructureError {
    DexEncodedField[] staticFields = lambda.staticFields();
    if (staticFields.length == 1) {
      DexEncodedField field = staticFields[0];
      if (field.field.name != kotlin.functional.kotlinStyleLambdaInstanceName ||
          field.field.type != lambda.type || !field.accessFlags.isPublic() ||
          !field.accessFlags.isFinal() || !field.accessFlags.isStatic()) {
        throw new LambdaStructureError("unexpected static field " + field.toSourceString());
      }
      // No state if the lambda is a singleton.
      if (lambda.instanceFields().length > 0) {
        throw new LambdaStructureError("has instance fields along with INSTANCE");
      }
      checkAccessFlags("static field access flags", field.accessFlags, SINGLETON_FIELD_FLAGS);
      checkFieldAnnotations(field);

    } else if (staticFields.length > 1) {
      throw new LambdaStructureError(
          "only one static field max expected, found " + staticFields.length);

    } else if (lambda.instanceFields().length == 0) {
      throw new LambdaStructureError("stateless lambda without INSTANCE field");
    }
  }

  private static DexType validateInterfaces(Kotlin kotlin, DexClass lambda)
      throws LambdaStructureError {
    if (lambda.interfaces.size() == 0) {
      throw new LambdaStructureError("does not implement any interfaces");
    }
    if (lambda.interfaces.size() > 1) {
      throw new LambdaStructureError(
          "implements more than one interface: " + lambda.interfaces.size());
    }
    DexType iface = lambda.interfaces.values[0];
    if (!kotlin.functional.isFunctionInterface(iface)) {
      throw new LambdaStructureError("implements " + iface.toSourceString() +
          " instead of kotlin functional interface.");
    }
    return iface;
  }

  private static String validateInstanceFields(DexClass lambda, boolean accessRelaxed)
      throws LambdaStructureError {
    DexEncodedField[] instanceFields = lambda.instanceFields();
    for (DexEncodedField field : instanceFields) {
      checkAccessFlags("capture field access flags", field.accessFlags,
          accessRelaxed ? CAPTURE_FIELD_FLAGS_RELAXED : CAPTURE_FIELD_FLAGS);
      checkFieldAnnotations(field);
    }
    return CaptureSignature.getCaptureSignature(instanceFields);
  }

  private static void validateDirectMethods(DexClass lambda) throws LambdaStructureError {
    DexEncodedMethod[] directMethods = lambda.directMethods();
    for (DexEncodedMethod method : directMethods) {
      if (method.isClassInitializer()) {
        // We expect to see class initializer only if there is a singleton field.
        if (lambda.staticFields().length != 1) {
          throw new LambdaStructureError("has static initializer, but no singleton field");
        }
        checkAccessFlags("unexpected static initializer access flags",
            method.accessFlags, CLASS_INITIALIZER_FLAGS);
        checkDirectMethodAnnotations(method);

      } else if (method.isStaticMethod()) {
        throw new LambdaStructureError(
            "unexpected static method: " + method.method.toSourceString());

      } else if (method.isInstanceInitializer()) {
        // Lambda class is expected to have one constructor
        // with parameters matching capture signature.
        DexType[] parameters = method.method.proto.parameters.values;
        DexEncodedField[] instanceFields = lambda.instanceFields();
        if (parameters.length != instanceFields.length) {
          throw new LambdaStructureError("constructor parameters don't match captured values.");
        }
        for (int i = 0; i < parameters.length; i++) {
          if (parameters[i] != instanceFields[i].field.type) {
            throw new LambdaStructureError("constructor parameters don't match captured values.");
          }
        }
        checkAccessFlags("unexpected constructor access flags",
            method.accessFlags, CONSTRUCTOR_FLAGS, CONSTRUCTOR_FLAGS_RELAXED);
        checkDirectMethodAnnotations(method);

      } else {
        throw new Unreachable();
      }
    }
  }

  private static void checkDirectMethodAnnotations(DexEncodedMethod method)
      throws LambdaStructureError {
    if (!method.annotations.isEmpty()) {
      throw new LambdaStructureError("unexpected method annotations [" +
          method.annotations.toSmaliString() + "] on " + method.method.toSourceString());
    }
    if (!method.parameterAnnotations.isEmpty()) {
      throw new LambdaStructureError("unexpected method parameters annotations [" +
          method.annotations.toSmaliString() + "] on " + method.method.toSourceString());
    }
  }

  private static void checkFieldAnnotations(DexEncodedField field) throws LambdaStructureError {
    if (!field.annotations.isEmpty()) {
      throw new LambdaStructureError("unexpected field annotations [" +
          field.annotations.toSmaliString() + "] on " + field.field.toSourceString());
    }
  }

  @SafeVarargs
  private static <T extends AccessFlags> void checkAccessFlags(
      String message, T actual, T... expected) throws LambdaStructureError {
    for (T flag : expected) {
      if (flag.equals(actual)) {
        return;
      }
    }
    throw new LambdaStructureError(message);
  }
}
