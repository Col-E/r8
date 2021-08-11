// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import static com.android.tools.r8.ir.desugar.lambda.ForcefullyMovedLambdaMethodConsumer.emptyForcefullyMovedLambdaMethodConsumer;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.getApiLevelIfEnabledForNewMember;
import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.desugar.lambda.ForcefullyMovedLambdaMethodConsumer;
import com.android.tools.r8.ir.desugar.lambda.LambdaInstructionDesugaring;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents lambda class generated for a lambda descriptor in context of lambda instantiation
 * point.
 *
 * <p>Even though call sites, and thus lambda descriptors, are canonicalized across the application,
 * the context may require several lambda classes to be generated for the same lambda descriptor.
 *
 * <p>One reason is that we always generate a lambda class in the same package lambda instantiation
 * point is located in, so if same call site is used in two classes from different packages (which
 * can happen if same public method is being references via method reference expression) we generate
 * separate lambda classes in those packages.
 *
 * <p>Another reason is that if we generate an accessor, we generate it in the class referencing the
 * call site, and thus two such classes will require two separate lambda classes.
 */
public final class LambdaClass {

  public static final String LAMBDA_INSTANCE_FIELD_NAME = "INSTANCE";
  public static final String JAVAC_EXPECTED_LAMBDA_METHOD_PREFIX = "lambda$";
  public static final String R8_LAMBDA_ACCESSOR_METHOD_PREFIX = "$r8$lambda$";

  private static final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  final AppView<?> appView;
  final LambdaInstructionDesugaring desugaring;
  public final DexType type;
  public LambdaDescriptor descriptor;
  public final DexMethod constructor;
  final DexMethod classConstructor;
  public final DexField lambdaField;
  public final Target target;

  // Considered final but is set after due to circularity in allocation.
  private DexProgramClass clazz = null;

  public LambdaClass(
      SyntheticProgramClassBuilder builder,
      AppView<?> appView,
      LambdaInstructionDesugaring desugaring,
      ProgramMethod accessedFrom,
      LambdaDescriptor descriptor) {
    assert desugaring != null;
    assert descriptor != null;
    this.type = builder.getType();
    this.appView = appView;
    this.desugaring = desugaring;
    this.descriptor = descriptor;

    DexItemFactory factory = builder.getFactory();
    DexProto constructorProto = factory.createProto(
        factory.voidType, descriptor.captures.values);
    this.constructor = factory.createMethod(type, constructorProto, factory.constructorMethodName);

    this.target = createTarget(accessedFrom);

    boolean stateless = isStateless();
    this.classConstructor =
        !stateless
            ? null
            : factory.createMethod(type, constructorProto, factory.classConstructorMethodName);
    this.lambdaField =
        !stateless ? null : factory.createField(type, type, factory.lambdaInstanceFieldName);

    // Synthesize the program class one all fields are set.
    synthesizeLambdaClass(builder);
  }

  public final DexProgramClass getLambdaProgramClass() {
    assert clazz != null;
    return clazz;
  }

  public DexType getType() {
    return type;
  }

  public void setClass(DexProgramClass clazz) {
    assert this.clazz == null;
    assert clazz != null;
    assert type == clazz.type;
    this.clazz = clazz;
  }

  private void synthesizeLambdaClass(SyntheticProgramClassBuilder builder) {
    builder.setInterfaces(descriptor.interfaces);
    synthesizeStaticFields(builder);
    synthesizeInstanceFields(builder);
    synthesizeDirectMethods(builder);
    synthesizeVirtualMethods(builder);
  }

  final DexField getCaptureField(int index) {
    return appView
        .dexItemFactory()
        .createField(
            this.type,
            descriptor.captures.values[index],
            appView.dexItemFactory().createString("f$" + index));
  }

  public final boolean isStateless() {
    return descriptor.isStateless();
  }

  // Synthesize virtual methods.
  private void synthesizeVirtualMethods(SyntheticProgramClassBuilder builder) {
    DexMethod mainMethod =
        appView.dexItemFactory().createMethod(type, descriptor.erasedProto, descriptor.name);

    List<DexEncodedMethod> methods = new ArrayList<>(1 + descriptor.bridges.size());

    // Synthesize main method.
    methods.add(
        new DexEncodedMethod(
            mainMethod,
            MethodAccessFlags.fromSharedAccessFlags(
                Constants.ACC_PUBLIC | Constants.ACC_FINAL, false),
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            LambdaMainMethodSourceCode.build(this, mainMethod),
            true));

    // Synthesize bridge methods.
    for (DexProto bridgeProto : descriptor.bridges) {
      DexMethod bridgeMethod =
          appView.dexItemFactory().createMethod(type, bridgeProto, descriptor.name);
      methods.add(
          new DexEncodedMethod(
              bridgeMethod,
              MethodAccessFlags.fromSharedAccessFlags(
                  Constants.ACC_PUBLIC
                      | Constants.ACC_FINAL
                      | Constants.ACC_SYNTHETIC
                      | Constants.ACC_BRIDGE,
                  false),
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              LambdaBridgeMethodSourceCode.build(this, bridgeMethod, mainMethod),
              true));
    }
    builder.setVirtualMethods(methods);
  }

  // Synthesize direct methods.
  private void synthesizeDirectMethods(SyntheticProgramClassBuilder builder) {
    boolean stateless = isStateless();
    List<DexEncodedMethod> methods = new ArrayList<>(stateless ? 2 : 1);

    // Constructor.
    methods.add(
        new DexEncodedMethod(
            constructor,
            MethodAccessFlags.fromSharedAccessFlags(
                (stateless ? Constants.ACC_PRIVATE : Constants.ACC_PUBLIC)
                    | Constants.ACC_SYNTHETIC,
                true),
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            LambdaConstructorSourceCode.build(this),
            true));

    // Class constructor for stateless lambda classes.
    if (stateless) {
      methods.add(
          new DexEncodedMethod(
              classConstructor,
              MethodAccessFlags.fromSharedAccessFlags(
                  Constants.ACC_SYNTHETIC | Constants.ACC_STATIC, true),
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              LambdaClassConstructorSourceCode.build(this),
              true));
      feedback.classInitializerMayBePostponed(methods.get(1));
    }
    builder.setDirectMethods(methods);
  }

  // Synthesize instance fields to represent captured values.
  private void synthesizeInstanceFields(SyntheticProgramClassBuilder builder) {
    DexType[] fieldTypes = descriptor.captures.values;
    int fieldCount = fieldTypes.length;
    List<DexEncodedField> fields = new ArrayList<>(fieldCount);
    for (int i = 0; i < fieldCount; i++) {
      boolean deprecated = false;
      boolean d8R8Synthesized = true;
      fields.add(
          new DexEncodedField(
              getCaptureField(i),
              FieldAccessFlags.createPublicFinalSynthetic(),
              FieldTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              null,
              deprecated,
              d8R8Synthesized,
              getApiLevelIfEnabledForNewMember(appView, Function.identity())));
    }
    builder.setInstanceFields(fields);
  }

  // Synthesize static fields to represent singleton instance.
  private void synthesizeStaticFields(SyntheticProgramClassBuilder builder) {
    if (isStateless()) {
      // Create instance field for stateless lambda.
      assert this.lambdaField != null;
      boolean deprecated = false;
      boolean d8R8Synthesized = true;
      builder.setStaticFields(
          Collections.singletonList(
              new DexEncodedField(
                  this.lambdaField,
                  FieldAccessFlags.fromSharedAccessFlags(
                      Constants.ACC_PUBLIC
                          | Constants.ACC_FINAL
                          | Constants.ACC_SYNTHETIC
                          | Constants.ACC_STATIC),
                  FieldTypeSignature.noSignature(),
                  DexAnnotationSet.empty(),
                  DexValueNull.NULL,
                  deprecated,
                  d8R8Synthesized,
                  getApiLevelIfEnabledForNewMember(appView, Function.identity()))));
    }
  }

  // Creates a delegation target for this particular lambda class. Note that we
  // should always be able to create targets for the lambdas we support.
  private Target createTarget(ProgramMethod accessedFrom) {
    switch (descriptor.implHandle.type) {
      case INVOKE_SUPER:
        throw new Unimplemented("Method references to super methods are not yet supported");
      case INVOKE_INTERFACE:
        return createInterfaceMethodTarget(accessedFrom);
      case INVOKE_CONSTRUCTOR:
        return createConstructorTarget(accessedFrom);
      case INVOKE_STATIC:
        return canAccessModifyLambdaImplMethod()
            ? createLambdaImplMethodTarget(accessedFrom)
            : createStaticMethodTarget(accessedFrom);
      case INVOKE_DIRECT:
        return canAccessModifyLambdaImplMethod()
            ? createLambdaImplMethodTarget(accessedFrom)
            : createInstanceMethodTarget(accessedFrom);
      case INVOKE_INSTANCE:
        return createInstanceMethodTarget(accessedFrom);
      default:
        throw new Unreachable("Unexpected method handle type in " + descriptor.implHandle);
    }
  }

  private boolean doesNotNeedAccessor(ProgramMethod accessedFrom) {
    return canAccessModifyLambdaImplMethod() || !descriptor.needsAccessor(accessedFrom);
  }

  private boolean canAccessModifyLambdaImplMethod() {
    MethodHandleType invokeType = descriptor.implHandle.type;
    return appView.options().canAccessModifyLambdaImplementationMethods(appView)
        && (invokeType.isInvokeDirect() || invokeType.isInvokeStatic())
        && descriptor.delegatesToLambdaImplMethod(appView.dexItemFactory())
        && !desugaring.isDirectTargetedLambdaImplementationMethod(descriptor.implHandle);
  }

  private Target createLambdaImplMethodTarget(ProgramMethod accessedFrom) {
    DexMethodHandle implHandle = descriptor.implHandle;
    assert implHandle != null;
    DexMethod implMethod = implHandle.asMethod();

    // Lambda$ method. We should always find it. If not found an ICCE can be expected to be thrown.
    assert descriptor.delegatesToLambdaImplMethod(appView.dexItemFactory());
    assert implMethod.holder == accessedFrom.getHolderType();
    assert descriptor.verifyTargetFoundInClass(accessedFrom.getHolderType());
    if (implHandle.type.isInvokeStatic()) {
      MethodResolutionResult resolution =
          appView.appInfoForDesugaring().resolveMethod(implMethod, implHandle.isInterface);
      if (resolution.isFailedResolution()) {
        return new InvalidLambdaImplTarget(
            implMethod, Type.STATIC, appView.dexItemFactory().icceType);
      }
      SingleResolutionResult result = resolution.asSingleResolution();
      assert result.getResolvedMethod().isStatic();
      assert result.getResolvedHolder().isProgramClass();
      return new StaticLambdaImplTarget(
          new ProgramMethod(
              result.getResolvedHolder().asProgramClass(), result.getResolvedMethod()));
    }

    assert implHandle.type.isInvokeDirect();
    // If the lambda$ method is an instance-private method on an interface we convert it into a
    // public static method as it will be placed on the companion class.
    if (appView.definitionFor(implMethod.holder).isInterface()) {
      DexProto implProto = implMethod.proto;
      DexType[] implParams = implProto.parameters.values;
      DexType[] newParams = new DexType[implParams.length + 1];
      newParams[0] = implMethod.holder;
      System.arraycopy(implParams, 0, newParams, 1, implParams.length);

      DexProto newProto = appView.dexItemFactory().createProto(implProto.returnType, newParams);
      return new InterfaceLambdaImplTarget(
          appView.dexItemFactory().createMethod(implMethod.holder, newProto, implMethod.name));
    } else {
      // Otherwise we need to ensure the method can be reached publicly by virtual dispatch.
      // To avoid potential conflicts on the name of the lambda method once dispatch becomes virtual
      // we add the method-holder name as suffix to the lambda-method name.
      return new InstanceLambdaImplTarget(
          appView
              .dexItemFactory()
              .createMethod(
                  implMethod.holder,
                  implMethod.proto,
                  appView
                      .dexItemFactory()
                      .createString(
                          implMethod.name.toString() + "$" + implMethod.holder.getName())));
    }
  }

  // Create targets for instance method referenced directly without
  // lambda$ methods. It may require creation of accessors in some cases.
  private Target createInstanceMethodTarget(ProgramMethod accessedFrom) {
    assert descriptor.implHandle.type.isInvokeInstance() ||
        descriptor.implHandle.type.isInvokeDirect();

    if (doesNotNeedAccessor(accessedFrom)) {
      return new NoAccessorMethodTarget(Invoke.Type.VIRTUAL);
    }
    // We need to generate an accessor method in `accessedFrom` class/interface
    // for accessing the original instance impl-method. Note that impl-method's
    // holder does not have to be the same as `accessedFrom`.
    DexMethod implMethod = descriptor.implHandle.asMethod();
    DexProto implProto = implMethod.proto;
    DexType[] implParams = implProto.parameters.values;

    // The accessor method will be static, package private, and take the
    // receiver as the first argument. The receiver must be captured and
    // be the first captured value in case there are more than one.
    DexType[] accessorParams = new DexType[1 + implParams.length];
    accessorParams[0] = descriptor.getImplReceiverType();
    System.arraycopy(implParams, 0, accessorParams, 1, implParams.length);
    DexProto accessorProto =
        appView.dexItemFactory().createProto(implProto.returnType, accessorParams);
    DexMethod accessorMethod =
        appView
            .dexItemFactory()
            .createMethod(
                accessedFrom.getHolderType(), accessorProto, generateUniqueLambdaMethodName());

    return new ClassMethodWithAccessorTarget(accessorMethod);
  }

  // Create targets for static method referenced directly without
  // lambda$ methods. It may require creation of accessors in some cases.
  private Target createStaticMethodTarget(ProgramMethod accessedFrom) {
    assert descriptor.implHandle.type.isInvokeStatic();

    if (doesNotNeedAccessor(accessedFrom)) {
      return new NoAccessorMethodTarget(Invoke.Type.STATIC);
    }

    // We need to generate an accessor method in `accessedFrom` class/interface
    // for accessing the original static impl-method. The accessor method will be
    // static, package private with exactly same signature and the original method.
    DexMethod accessorMethod =
        appView
            .dexItemFactory()
            .createMethod(
                accessedFrom.getHolderType(),
                descriptor.implHandle.asMethod().proto,
                generateUniqueLambdaMethodName());
    return new ClassMethodWithAccessorTarget(accessorMethod);
  }

  // Create targets for constructor referenced directly without lambda$ methods.
  // It may require creation of accessors in some cases.
  private Target createConstructorTarget(ProgramMethod accessedFrom) {
    DexMethodHandle implHandle = descriptor.implHandle;
    assert implHandle != null;
    assert implHandle.type.isInvokeConstructor();

    if (doesNotNeedAccessor(accessedFrom)) {
      return new NoAccessorMethodTarget(Invoke.Type.DIRECT);
    }

    // We need to generate an accessor method in `accessedFrom` class/interface for
    // instantiating the class and calling constructor on it. The accessor method will
    // be static, package private with exactly same parameters as the constructor,
    // and return the newly created instance.
    DexMethod implMethod = implHandle.asMethod();
    DexType returnType = implMethod.holder;
    DexProto accessorProto =
        appView.dexItemFactory().createProto(returnType, implMethod.proto.parameters.values);
    DexMethod accessorMethod =
        appView
            .dexItemFactory()
            .createMethod(
                accessedFrom.getHolderType(), accessorProto, generateUniqueLambdaMethodName());
    return new ClassMethodWithAccessorTarget(accessorMethod);
  }

  // Create targets for interface methods.
  private Target createInterfaceMethodTarget(ProgramMethod accessedFrom) {
    assert descriptor.implHandle.type.isInvokeInterface();
    assert doesNotNeedAccessor(accessedFrom);
    return new NoAccessorMethodTarget(Invoke.Type.INTERFACE);
  }

  private DexString generateUniqueLambdaMethodName() {
    return appView
        .dexItemFactory()
        .createString(R8_LAMBDA_ACCESSOR_METHOD_PREFIX + descriptor.uniqueId);
  }

  // Represents information about the method lambda class need to delegate the call to. It may
  // be the same method as specified in lambda descriptor or a newly synthesized accessor.
  // Also provides action for ensuring accessibility of the referenced symbols.
  public abstract class Target {

    final DexMethod callTarget;
    final Invoke.Type invokeType;

    private boolean hasEnsuredAccessibility;
    private ProgramMethod accessibilityBridge;

    Target(DexMethod callTarget, Invoke.Type invokeType) {
      assert callTarget != null;
      assert invokeType != null;
      this.callTarget = callTarget;
      this.invokeType = invokeType;
    }

    // Ensure access of the referenced symbol(s).
    abstract ProgramMethod ensureAccessibility(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer);

    public final void ensureAccessibilityIfNeeded() {
      ensureAccessibilityIfNeeded(emptyForcefullyMovedLambdaMethodConsumer(), emptyConsumer());
    }

    // Ensure access of the referenced symbol(s).
    public final void ensureAccessibilityIfNeeded(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer) {
      if (!hasEnsuredAccessibility) {
        accessibilityBridge =
            ensureAccessibility(forcefullyMovedLambdaMethodConsumer, needsProcessingConsumer);
        hasEnsuredAccessibility = true;
      }
    }

    boolean isInterface() {
      return descriptor.implHandle.isInterface;
    }
  }

  public abstract class D8SpecificTarget extends Target {
    D8SpecificTarget(DexMethod callTarget, Type invokeType) {
      super(callTarget, invokeType);
      assert !appView.enableWholeProgramOptimizations();
    }
  }

  // Used for targeting methods referenced directly without creating accessors.
  private final class NoAccessorMethodTarget extends Target {

    NoAccessorMethodTarget(Invoke.Type invokeType) {
      super(descriptor.implHandle.asMethod(), invokeType);
    }

    @Override
    ProgramMethod ensureAccessibility(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer) {
      return null;
    }
  }

  // Used for static private lambda$ methods. Only needs access relaxation.
  private final class StaticLambdaImplTarget extends D8SpecificTarget {

    final ProgramMethod target;

    StaticLambdaImplTarget(ProgramMethod target) {
      super(descriptor.implHandle.asMethod(), Invoke.Type.STATIC);
      this.target = target;
    }

    @Override
    ProgramMethod ensureAccessibility(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer) {
      // We already found the static method to be called, just relax its accessibility.
      MethodAccessFlags flags = target.getAccessFlags();
      flags.unsetPrivate();
      if (target.getHolder().isInterface()) {
        flags.setPublic();
      }
      return null;
    }
  }

  // Used for instance private lambda$ methods on interfaces which need to be converted to public
  // static methods. They can't remain instance methods as they will end up on the companion class.
  private class InterfaceLambdaImplTarget extends D8SpecificTarget {

    InterfaceLambdaImplTarget(DexMethod staticMethod) {
      super(staticMethod, Type.STATIC);
    }

    @Override
    ProgramMethod ensureAccessibility(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer) {
      // For all instantiation points for which the compiler creates lambda$
      // methods, it creates these methods in the same class/interface.
      DexMethod implMethod = descriptor.implHandle.asMethod();
      DexProgramClass implMethodHolder = appView.definitionFor(implMethod.holder).asProgramClass();

      DexEncodedMethod replacement =
          implMethodHolder
              .getMethodCollection()
              .replaceDirectMethod(
                  implMethod,
                  encodedMethod -> {
                    // We need to create a new static method with the same code to be able to safely
                    // relax its accessibility without making it virtual.
                    MethodAccessFlags newAccessFlags = encodedMethod.accessFlags.copy();
                    newAccessFlags.setStatic();
                    newAccessFlags.unsetPrivate();
                    // Always make the method public to provide access.
                    newAccessFlags.setPublic();
                    DexEncodedMethod newMethod =
                        new DexEncodedMethod(
                            callTarget,
                            newAccessFlags,
                            encodedMethod.getGenericSignature(),
                            encodedMethod.annotations(),
                            encodedMethod.parameterAnnotationsList,
                            encodedMethod.getCode(),
                            true);
                    newMethod.copyMetadata(encodedMethod);
                    forcefullyMovedLambdaMethodConsumer.acceptForcefullyMovedLambdaMethod(
                        encodedMethod.getReference(), callTarget);

                    DexEncodedMethod.setDebugInfoWithFakeThisParameter(
                        newMethod.getCode(), callTarget.getArity(), appView);
                    return newMethod;
                  });
      if (replacement != null) {
        // Since we've copied the code object from an existing method, the code should already be
        // processed, and thus we don't need to schedule it for processing in D8.
        assert !appView.options().isGeneratingClassFiles() || replacement.getCode().isCfCode();
        assert !appView.options().isGeneratingDex() || replacement.getCode().isDexCode();
        ProgramMethod newMethod = new ProgramMethod(implMethodHolder, replacement);
        if (appView.options().isDesugaredLibraryCompilation()) {
          assert appView.options().isGeneratingClassFiles();
          needsProcessingConsumer.accept(newMethod);
        }
        return newMethod;
      }
      // The method might already have been moved by another invoke-dynamic targeting it.
      // If so, it must be defined on the holder.
      ProgramMethod modified = implMethodHolder.lookupProgramMethod(callTarget);
      assert modified != null;
      assert modified.getDefinition().isNonPrivateVirtualMethod();
      return modified;
    }
  }

  class InvalidLambdaImplTarget extends Target {

    final DexType exceptionType;

    public InvalidLambdaImplTarget(DexMethod callTarget, Type invokeType, DexType exceptionType) {
      super(callTarget, invokeType);
      this.exceptionType = exceptionType;
    }

    @Override
    ProgramMethod ensureAccessibility(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer) {
      return null;
    }
  }

  // Used for instance private lambda$ methods which need to be converted to public methods.
  private class InstanceLambdaImplTarget extends D8SpecificTarget {

    InstanceLambdaImplTarget(DexMethod staticMethod) {
      super(staticMethod, Type.VIRTUAL);
    }

    @Override
    ProgramMethod ensureAccessibility(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer) {
      // When compiling with whole program optimization, check that we are not inplace modifying.
      // For all instantiation points for which the compiler creates lambda$
      // methods, it creates these methods in the same class/interface.
      DexMethod implMethod = descriptor.implHandle.asMethod();
      DexProgramClass implMethodHolder = appView.definitionFor(implMethod.holder).asProgramClass();

      DexEncodedMethod replacement =
          implMethodHolder
              .getMethodCollection()
              .replaceDirectMethodWithVirtualMethod(
                  implMethod,
                  encodedMethod -> {
                    assert encodedMethod.isDirectMethod();
                    // We need to create a new method with the same code to be able to safely relax
                    // its accessibility and make it virtual.
                    MethodAccessFlags newAccessFlags = encodedMethod.accessFlags.copy();
                    newAccessFlags.unsetPrivate();
                    newAccessFlags.setPublic();
                    DexEncodedMethod newMethod =
                        new DexEncodedMethod(
                            callTarget,
                            newAccessFlags,
                            encodedMethod.getGenericSignature(),
                            encodedMethod.annotations(),
                            encodedMethod.parameterAnnotationsList,
                            encodedMethod.getCode(),
                            true);
                    newMethod.copyMetadata(encodedMethod);
                    forcefullyMovedLambdaMethodConsumer.acceptForcefullyMovedLambdaMethod(
                        encodedMethod.getReference(), callTarget);
                    return newMethod;
                  });
      if (replacement != null) {
        // Since we've copied the code object from an existing method, the code should already be
        // processed, and thus we don't need to schedule it for processing in D8.
        assert !appView.options().isGeneratingClassFiles() || replacement.getCode().isCfCode();
        assert !appView.options().isGeneratingDex() || replacement.getCode().isDexCode();
        ProgramMethod newMethod = new ProgramMethod(implMethodHolder, replacement);
        if (appView.options().isDesugaredLibraryCompilation()) {
          assert appView.options().isGeneratingClassFiles();
          needsProcessingConsumer.accept(newMethod);
        }
        return newMethod;
      }
      // The method might already have been moved by another invoke-dynamic targeting it.
      // If so, it must be defined on the holder.
      ProgramMethod modified = implMethodHolder.lookupProgramMethod(callTarget);
      assert modified != null;
      assert modified.getDefinition().isNonPrivateVirtualMethod();
      return modified;
    }
  }

  // Used for instance/static methods or constructors accessed via
  // synthesized accessor method. Needs accessor method to be created.
  private class ClassMethodWithAccessorTarget extends Target {

    ClassMethodWithAccessorTarget(DexMethod accessorMethod) {
      super(accessorMethod, Invoke.Type.STATIC);
    }

    @Override
    ProgramMethod ensureAccessibility(
        ForcefullyMovedLambdaMethodConsumer forcefullyMovedLambdaMethodConsumer,
        Consumer<ProgramMethod> needsProcessingConsumer) {
      // Create a static accessor with proper accessibility.
      DexProgramClass accessorClass = appView.definitionForProgramType(callTarget.holder);
      assert accessorClass != null;

      // The accessor might already have been created by another invoke-dynamic targeting it.
      ProgramMethod existing = accessorClass.lookupProgramMethod(callTarget);
      if (existing != null) {
        assert existing.getAccessFlags().isSynthetic();
        assert existing.getAccessFlags().isPublic();
        assert existing.getAccessFlags().isStatic();
        return existing;
      }

      // Always make the method public to provide access when r8 minification is allowed to move
      // the lambda class accessing this method to another package (-allowaccessmodification).
      ProgramMethod accessorMethod =
          new ProgramMethod(
              accessorClass,
              new DexEncodedMethod(
                  callTarget,
                  MethodAccessFlags.createPublicStaticSynthetic(),
                  MethodTypeSignature.noSignature(),
                  DexAnnotationSet.empty(),
                  ParameterAnnotationsList.empty(),
                  AccessorMethodSourceCode.build(LambdaClass.this, callTarget),
                  true));
      accessorClass.addDirectMethod(accessorMethod.getDefinition());
      if (appView.options().isDesugaredLibraryCompilation()
          || appView.options().isGeneratingDex()) {
        needsProcessingConsumer.accept(accessorMethod);
      }
      return accessorMethod;
    }
  }
}
