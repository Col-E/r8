// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_APPLICATION_COUNT;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_INTERFACE_COUNT;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_SUPER_TYPE;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_TYPE_VARIABLE_UNDEFINED;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.VALID;

import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.DexDefinitionSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class GenericSignatureCorrectnessHelper {

  private enum Mode {
    VERIFY,
    MARK_AS_INVALID;

    public boolean doNotVerify() {
      return markAsInvalid();
    }

    public boolean markAsInvalid() {
      return this == MARK_AS_INVALID;
    }
  }

  public enum SignatureEvaluationResult {
    INVALID_SUPER_TYPE,
    INVALID_INTERFACE_COUNT,
    INVALID_APPLICATION_COUNT,
    INVALID_TYPE_VARIABLE_UNDEFINED,
    VALID;

    boolean isValid() {
      return this == VALID;
    }

    boolean isInvalid() {
      return this != VALID;
    }
  }

  private final AppView<?> appView;
  private final Mode mode;

  private GenericSignatureCorrectnessHelper(AppView<?> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
  }

  public static GenericSignatureCorrectnessHelper createForInitialCheck(AppView<?> appView) {
    return new GenericSignatureCorrectnessHelper(appView, Mode.MARK_AS_INVALID);
  }

  public static GenericSignatureCorrectnessHelper createForVerification(AppView<?> appView) {
    return new GenericSignatureCorrectnessHelper(appView, Mode.VERIFY);
  }

  public void run() {
    appView.appInfo().classes().forEach(this::evaluateSignaturesForClass);
  }

  public SignatureEvaluationResult evaluateSignaturesForClass(DexProgramClass clazz) {
    GenericSignatureContextEvaluator genericSignatureContextEvaluator =
        new GenericSignatureContextEvaluator(appView, clazz, mode);
    ClassSignature classSignature = clazz.getClassSignature();
    SignatureEvaluationResult result = VALID;
    if (classSignature.hasNoSignature() || !classSignature.isInvalid()) {
      result = genericSignatureContextEvaluator.evaluateClassSignature(classSignature);
      if (result.isInvalid() && mode.markAsInvalid()) {
        clazz.setClassSignature(classSignature.toInvalid());
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      SignatureEvaluationResult methodResult =
          evaluate(
              method::getGenericSignature,
              genericSignatureContextEvaluator::visitMethodSignature,
              method::setGenericSignature);
      if (result.isValid() && methodResult.isInvalid()) {
        result = methodResult;
      }
    }
    for (DexEncodedField field : clazz.fields()) {
      SignatureEvaluationResult fieldResult =
          evaluate(
              field::getGenericSignature,
              genericSignatureContextEvaluator::visitFieldTypeSignature,
              field::setGenericSignature);
      if (result.isValid() && fieldResult.isInvalid()) {
        result = fieldResult;
      }
    }
    return result;
  }

  private <T extends DexDefinitionSignature<?>> SignatureEvaluationResult evaluate(
      Supplier<T> getter, Function<T, SignatureEvaluationResult> evaluate, Consumer<T> setter) {
    T signature = getter.get();
    if (signature.hasNoSignature() || signature.isInvalid()) {
      // Already marked as invalid, do nothing
      return VALID;
    }
    SignatureEvaluationResult signatureResult = evaluate.apply(signature);
    assert signatureResult.isValid() || mode.doNotVerify();
    if (signatureResult.isInvalid() && mode.doNotVerify()) {
      setter.accept((T) signature.toInvalid());
    }
    return signatureResult;
  }

  private static class GenericSignatureContextEvaluator {

    private final AppView<?> appView;
    private final DexProgramClass context;
    private final Set<String> classFormalTypeParameters = new HashSet<>();
    private final Set<String> methodTypeArguments = new HashSet<>();
    private final Mode mode;

    public GenericSignatureContextEvaluator(
        AppView<?> appView, DexProgramClass context, Mode mode) {
      this.appView = appView;
      this.context = context;
      this.mode = mode;
    }

    private SignatureEvaluationResult evaluateClassSignature(ClassSignature classSignature) {
      classSignature
          .getFormalTypeParameters()
          .forEach(param -> classFormalTypeParameters.add(param.name));
      if (classSignature.hasNoSignature()) {
        return VALID;
      }
      SignatureEvaluationResult signatureEvaluationResult =
          evaluateFormalTypeParameters(classSignature.formalTypeParameters);
      if (signatureEvaluationResult.isInvalid()) {
        return signatureEvaluationResult;
      }
      if ((context.superType != appView.dexItemFactory().objectType
              && context.superType != classSignature.superClassSignature().type())
          || (context.superType == appView.dexItemFactory().objectType
              && classSignature.superClassSignature().hasNoSignature())) {
        assert mode.doNotVerify();
        return INVALID_SUPER_TYPE;
      }
      signatureEvaluationResult =
          evaluateTypeArgumentsAppliedToType(
              classSignature.superClassSignature().typeArguments(), context.superType);
      if (signatureEvaluationResult.isInvalid()) {
        return signatureEvaluationResult;
      }
      List<ClassTypeSignature> superInterfaces = classSignature.superInterfaceSignatures();
      if (context.interfaces.size() != superInterfaces.size()) {
        assert mode.doNotVerify();
        return INVALID_INTERFACE_COUNT;
      }
      DexType[] actualInterfaces = context.interfaces.values;
      for (int i = 0; i < actualInterfaces.length; i++) {
        signatureEvaluationResult =
            evaluateTypeArgumentsAppliedToType(
                superInterfaces.get(i).typeArguments(), actualInterfaces[i]);
        if (signatureEvaluationResult.isInvalid()) {
          return signatureEvaluationResult;
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult visitMethodSignature(MethodTypeSignature methodSignature) {
      methodSignature
          .getFormalTypeParameters()
          .forEach(param -> methodTypeArguments.add(param.name));
      SignatureEvaluationResult evaluateResult =
          evaluateFormalTypeParameters(methodSignature.getFormalTypeParameters());
      if (evaluateResult.isInvalid()) {
        return evaluateResult;
      }
      evaluateResult = evaluateTypeArguments(methodSignature.typeSignatures);
      if (evaluateResult.isInvalid()) {
        return evaluateResult;
      }
      evaluateResult = evaluateTypeArguments(methodSignature.throwsSignatures);
      if (evaluateResult.isInvalid()) {
        return evaluateResult;
      }
      ReturnType returnType = methodSignature.returnType();
      if (!returnType.isVoidDescriptor()) {
        evaluateResult = evaluateTypeArgument(returnType.typeSignature());
        if (evaluateResult.isInvalid()) {
          return evaluateResult;
        }
      }
      methodTypeArguments.clear();
      return evaluateResult;
    }

    private SignatureEvaluationResult evaluateTypeArguments(List<TypeSignature> typeSignatures) {
      for (TypeSignature typeSignature : typeSignatures) {
        SignatureEvaluationResult signatureEvaluationResult = evaluateTypeArgument(typeSignature);
        if (signatureEvaluationResult.isInvalid()) {
          return signatureEvaluationResult;
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
      return evaluateTypeArgument(fieldSignature);
    }

    private SignatureEvaluationResult evaluateFormalTypeParameters(
        List<FormalTypeParameter> typeParameters) {
      for (FormalTypeParameter typeParameter : typeParameters) {
        SignatureEvaluationResult evaluationResult = evaluateTypeParameter(typeParameter);
        if (evaluationResult.isInvalid()) {
          return evaluationResult;
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult evaluateTypeParameter(FormalTypeParameter typeParameter) {
      SignatureEvaluationResult evaluationResult = evaluateTypeArgument(typeParameter.classBound);
      if (evaluationResult.isInvalid()) {
        return evaluationResult;
      }
      if (typeParameter.interfaceBounds != null) {
        for (FieldTypeSignature interfaceBound : typeParameter.interfaceBounds) {
          evaluationResult = evaluateTypeArgument(interfaceBound);
          if (evaluationResult != VALID) {
            return evaluationResult;
          }
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult evaluateTypeArgument(TypeSignature typeSignature) {
      if (typeSignature.isBaseTypeSignature()) {
        return VALID;
      }
      FieldTypeSignature fieldTypeSignature = typeSignature.asFieldTypeSignature();
      if (fieldTypeSignature.hasNoSignature()) {
        return VALID;
      }
      if (fieldTypeSignature.isTypeVariableSignature()) {
        // This is in an applied position, just check that the variable is registered.
        String typeVariable = fieldTypeSignature.asTypeVariableSignature().typeVariable();
        if (classFormalTypeParameters.contains(typeVariable)
            || methodTypeArguments.contains(typeVariable)) {
          return VALID;
        }
        assert mode.doNotVerify();
        return INVALID_TYPE_VARIABLE_UNDEFINED;
      }
      if (fieldTypeSignature.isArrayTypeSignature()) {
        return evaluateTypeArgument(fieldTypeSignature.asArrayTypeSignature().elementSignature());
      }
      assert fieldTypeSignature.isClassTypeSignature();
      return evaluateTypeArguments(fieldTypeSignature.asClassTypeSignature());
    }

    private SignatureEvaluationResult evaluateTypeArguments(ClassTypeSignature classTypeSignature) {
      return evaluateTypeArgumentsAppliedToType(
          classTypeSignature.typeArguments, classTypeSignature.type());
    }

    private SignatureEvaluationResult evaluateTypeArgumentsAppliedToType(
        List<FieldTypeSignature> typeArguments, DexType type) {
      for (FieldTypeSignature typeArgument : typeArguments) {
        SignatureEvaluationResult evaluationResult = evaluateTypeArgument(typeArgument);
        if (evaluationResult.isInvalid()) {
          assert mode.doNotVerify();
          return evaluationResult;
        }
      }
      DexClass clazz = appView.definitionFor(type);
      if (clazz == null) {
        // We do not know if the application of arguments works or not.
        return VALID;
      }
      if (typeArguments.size() != clazz.classSignature.getFormalTypeParameters().size()) {
        assert mode.doNotVerify();
        return INVALID_APPLICATION_COUNT;
      }
      return VALID;
    }
  }
}
