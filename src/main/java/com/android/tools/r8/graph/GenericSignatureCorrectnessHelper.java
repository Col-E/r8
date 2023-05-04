// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_APPLICATION_COUNT;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_INTERFACE_COUNT;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_SUPER_TYPE;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.INVALID_TYPE_VARIABLE_UNDEFINED;
import static com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult.VALID;
import static com.google.common.base.Predicates.alwaysFalse;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.DexDefinitionSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.graph.GenericSignatureContextBuilder.TypeParameterContext;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class GenericSignatureCorrectnessHelper {

  private enum Mode {
    VERIFY,
    CLEAR_IF_INVALID;

    public boolean doNotVerify() {
      return clearIfInvalid();
    }

    public boolean clearIfInvalid() {
      return this == CLEAR_IF_INVALID;
    }
  }

  public enum SignatureEvaluationResult {
    INVALID_SUPER_TYPE,
    INVALID_INTERFACE_TYPE,
    INVALID_INTERFACE_COUNT,
    INVALID_APPLICATION_COUNT,
    INVALID_TYPE_VARIABLE_UNDEFINED,
    VALID;

    public boolean isValid() {
      return this == VALID;
    }

    public boolean isInvalid() {
      return this != VALID;
    }

    public SignatureEvaluationResult combine(SignatureEvaluationResult other) {
      return isInvalid() ? this : other;
    }

    public String getDescription() {
      switch (this) {
        case INVALID_APPLICATION_COUNT:
          return "The applied generic arguments have different count than the expected formals";
        case INVALID_INTERFACE_COUNT:
          return "The generic signature has a different number of interfaces than the class";
        case INVALID_SUPER_TYPE:
          return "The generic super type is not the same as the class super type";
        case INVALID_TYPE_VARIABLE_UNDEFINED:
          return "A type variable is not in scope";
        default:
          assert this.isValid();
          throw new Unreachable("Should not throw an error for a valid signature");
      }
    }
  }

  private final AppView<?> appView;
  private final Mode mode;
  private final InternalOptions options;
  private final GenericSignatureContextBuilder contextBuilder;

  private GenericSignatureCorrectnessHelper(
      AppView<?> appView, GenericSignatureContextBuilder contextBuilder, Mode mode) {
    this.appView = appView;
    this.contextBuilder = contextBuilder;
    this.mode = mode;
    this.options = appView.options();
  }

  public static GenericSignatureCorrectnessHelper createForInitialCheck(
      AppView<?> appView, GenericSignatureContextBuilder contextBuilder) {
    return new GenericSignatureCorrectnessHelper(appView, contextBuilder, Mode.CLEAR_IF_INVALID);
  }

  public static GenericSignatureCorrectnessHelper createForVerification(
      AppView<?> appView, GenericSignatureContextBuilder contextBuilder) {
    return new GenericSignatureCorrectnessHelper(appView, contextBuilder, Mode.VERIFY);
  }

  public SignatureEvaluationResult run(Collection<DexProgramClass> programClasses) {
    if (appView.options().disableGenericSignatureValidation
        || !appView.options().parseSignatureAttribute()) {
      return VALID;
    }
    SignatureEvaluationResult evaluationResult = VALID;
    for (DexProgramClass clazz : programClasses) {
      evaluationResult = evaluationResult.combine(evaluateSignaturesForClass(clazz));
    }
    return evaluationResult;
  }

  public SignatureEvaluationResult evaluateSignaturesForClass(DexProgramClass clazz) {
    if (appView.options().disableGenericSignatureValidation
        || !appView.options().parseSignatureAttribute()) {
      return VALID;
    }

    TypeParameterContext typeParameterContext =
        contextBuilder.computeTypeParameterContext(appView, clazz.type, alwaysFalse());

    GenericSignatureContextEvaluator genericSignatureContextEvaluator =
        new GenericSignatureContextEvaluator(appView, mode, clazz);

    SignatureEvaluationResult result =
        genericSignatureContextEvaluator.evaluateClassSignatureForContext(typeParameterContext);
    if (result.isInvalid() && mode.clearIfInvalid()) {
      // Only report info messages for classes that are kept explicitly. This is to ensure we do not
      // spam the developer with messages they can do nothing about.
      KeepClassInfo classInfo = appView.getKeepInfo().getClassInfo(clazz);
      if (appView.hasLiveness() && !classInfo.isShrinkingAllowed(appView.options())) {
        // If/when this no longer holds it should be moved into the condition.
        assert !classInfo.isSignatureRemovalAllowed(appView.options());
        appView
            .options()
            .reporter
            .info(
                GenericSignatureValidationDiagnostic.invalidClassSignature(
                    clazz.getClassSignature().toString(),
                    clazz.getTypeName(),
                    clazz.getOrigin(),
                    result));
      }
      clazz.clearClassSignature();
    }
    for (DexEncodedMethod method : clazz.methods()) {
      result =
          result.combine(
              evaluate(
                  method::getGenericSignature,
                  methodSignature ->
                      genericSignatureContextEvaluator.visitMethodSignature(
                          methodSignature, typeParameterContext),
                  invalidResult -> {
                    // Only report info messages for methods that are kept explicitly. This is to
                    // ensure we do not spam the developer with messages they can do nothing about.
                    KeepMethodInfo methodInfo = appView.getKeepInfo().getMethodInfo(method, clazz);
                    if (appView.hasLiveness()
                        && !methodInfo.isShrinkingAllowed(appView.options())) {
                      // If/when this no longer holds it should be moved into the condition.
                      assert !methodInfo.isSignatureRemovalAllowed(appView.options());
                      appView
                          .options()
                          .reporter
                          .info(
                              GenericSignatureValidationDiagnostic.invalidMethodSignature(
                                  method.getGenericSignature().toString(),
                                  method.toSourceString(),
                                  clazz.getOrigin(),
                                  invalidResult));
                    }
                    method.clearGenericSignature();
                  }));
    }
    for (DexEncodedField field : clazz.fields()) {
      result =
          result.combine(
              evaluate(
                  field::getGenericSignature,
                  fieldSignature ->
                      genericSignatureContextEvaluator.visitFieldTypeSignature(
                          fieldSignature, typeParameterContext),
                  invalidResult -> {
                    KeepFieldInfo fieldInfo = appView.getKeepInfo().getFieldInfo(field, clazz);
                    // Only report info messages for fields that are kept explicitly. This is to
                    // ensure we do not spam the developer with messages they can do nothing about.
                    if (appView.hasLiveness() && !fieldInfo.isShrinkingAllowed(appView.options())) {
                      // If/when this no longer holds it should be moved into the condition.
                      assert !fieldInfo.isSignatureRemovalAllowed(appView.options());
                      appView
                          .options()
                          .reporter
                          .info(
                              GenericSignatureValidationDiagnostic.invalidFieldSignature(
                                  field.getGenericSignature().toString(),
                                  field.toSourceString(),
                                  clazz.getOrigin(),
                                  invalidResult));
                    }
                    field.clearGenericSignature();
                  }));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private <T extends DexDefinitionSignature<?>> SignatureEvaluationResult evaluate(
      Supplier<T> getter,
      Function<T, SignatureEvaluationResult> evaluate,
      Consumer<SignatureEvaluationResult> invalidAction) {
    T signature = getter.get();
    if (signature.hasNoSignature() || signature.isInvalid()) {
      // Already marked as invalid, do nothing
      return VALID;
    }
    SignatureEvaluationResult signatureResult = evaluate.apply(signature);
    assert signatureResult.isValid() || mode.doNotVerify();
    if (signatureResult.isInvalid() && mode.clearIfInvalid()) {
      invalidAction.accept(signatureResult);
    }
    return signatureResult;
  }

  private static class GenericSignatureContextEvaluator {

    private final AppView<?> appView;
    private final DexProgramClass context;
    private final Mode mode;

    private GenericSignatureContextEvaluator(
        AppView<?> appView, Mode mode, DexProgramClass context) {
      this.appView = appView;
      this.mode = mode;
      this.context = context;
    }

    private SignatureEvaluationResult evaluateClassSignatureForContext(
        TypeParameterContext typeParameterContext) {
      ClassSignature classSignature = context.classSignature;
      if (classSignature.hasNoSignature() || classSignature.isInvalid()) {
        return VALID;
      }
      SignatureEvaluationResult signatureEvaluationResult =
          evaluateFormalTypeParameters(
              classSignature.getFormalTypeParameters(), typeParameterContext);
      if (signatureEvaluationResult.isInvalid()) {
        return signatureEvaluationResult;
      }
      ClassTypeSignature superClassSignature =
          classSignature.getSuperClassSignatureOrObject(appView.dexItemFactory());
      if (context.superType != superClassSignature.type()) {
        assert mode.doNotVerify() : "Super type inconsistency in generic signature";
        return INVALID_SUPER_TYPE;
      }
      signatureEvaluationResult =
          evaluateTypeArgumentsAppliedToType(
              superClassSignature.typeArguments(), context.superType, typeParameterContext);
      if (signatureEvaluationResult.isInvalid()) {
        return signatureEvaluationResult;
      }
      List<ClassTypeSignature> superInterfaces = classSignature.getSuperInterfaceSignatures();
      if (context.interfaces.size() != superInterfaces.size()) {
        assert mode.doNotVerify();
        return INVALID_INTERFACE_COUNT;
      }
      DexType[] actualInterfaces = context.interfaces.values;
      for (int i = 0; i < actualInterfaces.length; i++) {
        signatureEvaluationResult =
            evaluateTypeArgumentsAppliedToType(
                superInterfaces.get(i).typeArguments(), actualInterfaces[i], typeParameterContext);
        if (signatureEvaluationResult.isInvalid()) {
          return signatureEvaluationResult;
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult visitMethodSignature(
        MethodTypeSignature methodSignature, TypeParameterContext typeParameterContext) {
      // If the class context is invalid, we cannot reason about the method signatures.
      if (context.classSignature.isInvalid()) {
        return VALID;
      }
      TypeParameterContext methodContext =
          methodSignature.formalTypeParameters.isEmpty()
              ? typeParameterContext
              : typeParameterContext.addLiveParameters(
                  ListUtils.map(
                      methodSignature.getFormalTypeParameters(), FormalTypeParameter::getName));
      SignatureEvaluationResult evaluateResult =
          evaluateFormalTypeParameters(methodSignature.getFormalTypeParameters(), methodContext);
      if (evaluateResult.isInvalid()) {
        return evaluateResult;
      }
      evaluateResult = evaluateTypeArguments(methodSignature.typeSignatures, methodContext);
      if (evaluateResult.isInvalid()) {
        return evaluateResult;
      }
      evaluateResult = evaluateTypeArguments(methodSignature.throwsSignatures, methodContext);
      if (evaluateResult.isInvalid()) {
        return evaluateResult;
      }
      ReturnType returnType = methodSignature.returnType();
      if (!returnType.isVoidDescriptor()) {
        evaluateResult = evaluateTypeArgument(returnType.typeSignature(), methodContext);
        if (evaluateResult.isInvalid()) {
          return evaluateResult;
        }
      }
      return evaluateResult;
    }

    private SignatureEvaluationResult evaluateTypeArguments(
        List<TypeSignature> typeSignatures, TypeParameterContext typeParameterContext) {
      for (TypeSignature typeSignature : typeSignatures) {
        SignatureEvaluationResult signatureEvaluationResult =
            evaluateTypeArgument(typeSignature, typeParameterContext);
        if (signatureEvaluationResult.isInvalid()) {
          return signatureEvaluationResult;
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult visitFieldTypeSignature(
        FieldTypeSignature fieldSignature, TypeParameterContext typeParameterContext) {
      // If the class context is invalid, we cannot reason about the method signatures.
      if (context.classSignature.isInvalid()) {
        return VALID;
      }
      return evaluateTypeArgument(fieldSignature, typeParameterContext);
    }

    private SignatureEvaluationResult evaluateFormalTypeParameters(
        List<FormalTypeParameter> typeParameters, TypeParameterContext typeParameterContext) {
      for (FormalTypeParameter typeParameter : typeParameters) {
        SignatureEvaluationResult evaluationResult =
            evaluateTypeParameter(typeParameter, typeParameterContext);
        if (evaluationResult.isInvalid()) {
          return evaluationResult;
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult evaluateTypeParameter(
        FormalTypeParameter typeParameter, TypeParameterContext typeParameterContext) {
      SignatureEvaluationResult evaluationResult =
          evaluateTypeArgument(typeParameter.classBound, typeParameterContext);
      if (evaluationResult.isInvalid()) {
        return evaluationResult;
      }
      for (FieldTypeSignature interfaceBound : typeParameter.interfaceBounds) {
        evaluationResult = evaluateTypeArgument(interfaceBound, typeParameterContext);
        if (evaluationResult != VALID) {
          return evaluationResult;
        }
      }
      return VALID;
    }

    private SignatureEvaluationResult evaluateTypeArgument(
        TypeSignature typeSignature, TypeParameterContext typeParameterContext) {
      if (typeSignature.isBaseTypeSignature()) {
        return VALID;
      }
      FieldTypeSignature fieldTypeSignature = typeSignature.asFieldTypeSignature();
      if (fieldTypeSignature.hasNoSignature() || fieldTypeSignature.isStar()) {
        return VALID;
      }
      if (fieldTypeSignature.isTypeVariableSignature()) {
        // This is in an applied position, just check that the variable is registered.
        String typeVariable = fieldTypeSignature.asTypeVariableSignature().typeVariable();
        if (typeParameterContext.isLiveParameter(typeVariable)) {
          return VALID;
        }
        assert mode.doNotVerify();
        return INVALID_TYPE_VARIABLE_UNDEFINED;
      }
      if (fieldTypeSignature.isArrayTypeSignature()) {
        return evaluateTypeArgument(
            fieldTypeSignature.asArrayTypeSignature().elementSignature(), typeParameterContext);
      }
      assert fieldTypeSignature.isClassTypeSignature();
      return evaluateTypeArguments(fieldTypeSignature.asClassTypeSignature(), typeParameterContext);
    }

    private SignatureEvaluationResult evaluateTypeArguments(
        ClassTypeSignature classTypeSignature, TypeParameterContext typeParameterContext) {
      return evaluateTypeArgumentsAppliedToType(
          classTypeSignature.typeArguments, classTypeSignature.type(), typeParameterContext);
    }

    private SignatureEvaluationResult evaluateTypeArgumentsAppliedToType(
        List<FieldTypeSignature> typeArguments,
        DexType type,
        TypeParameterContext typeParameterContext) {
      for (FieldTypeSignature typeArgument : typeArguments) {
        SignatureEvaluationResult evaluationResult =
            evaluateTypeArgument(typeArgument, typeParameterContext);
        if (evaluationResult.isInvalid()) {
          assert mode.doNotVerify();
          return evaluationResult;
        }
      }
      // TODO(b/187035453): We should visit generic signatures in the enqueuer.
      DexClass clazz = appView.appInfo().definitionForWithoutExistenceAssert(type);
      if (clazz == null) {
        // We do not know if the application of arguments works or not.
        return VALID;
      }
      if (typeArguments.isEmpty()) {
        // When type arguments are empty we are using the raw type.
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
