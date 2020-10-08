// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.synthetic.ForwardMethodSourceCode;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.base.Suppliers;
import com.google.common.primitives.Longs;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.zip.CRC32;

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

  final AppView<?> appView;
  final LambdaRewriter rewriter;
  public final DexType type;
  public LambdaDescriptor descriptor;
  public final DexMethod constructor;
  final DexMethod classConstructor;
  public final DexField lambdaField;
  public final Target target;
  public final AtomicBoolean addToMainDexList = new AtomicBoolean(false);
  private final Collection<DexProgramClass> synthesizedFrom = new ArrayList<>(1);
  private final Supplier<DexProgramClass> lazyDexClass =
      Suppliers.memoize(this::synthesizeLambdaClass); // NOTE: thread-safe.

  LambdaClass(
      AppView<?> appView,
      LambdaRewriter rewriter,
      ProgramMethod accessedFrom,
      DexType lambdaClassType,
      LambdaDescriptor descriptor) {
    assert rewriter != null;
    assert lambdaClassType != null;
    assert descriptor != null;

    this.appView = appView;
    this.rewriter = rewriter;
    this.type = lambdaClassType;
    this.descriptor = descriptor;

    DexItemFactory factory = appView.dexItemFactory();
    DexProto constructorProto = factory.createProto(
        factory.voidType, descriptor.captures.values);
    this.constructor =
        factory.createMethod(lambdaClassType, constructorProto, factory.constructorMethodName);

    this.target = createTarget(accessedFrom);

    boolean stateless = isStateless();
    this.classConstructor =
        !stateless
            ? null
            : factory.createMethod(
                lambdaClassType, constructorProto, factory.classConstructorMethodName);
    this.lambdaField =
        !stateless
            ? null
            : factory.createField(lambdaClassType, lambdaClassType, rewriter.instanceFieldName);
  }

  // Generate unique lambda class type for lambda descriptor and instantiation point context.
  public static DexType createLambdaClassType(
      AppView<?> appView, ProgramMethod accessedFrom, LambdaDescriptor match) {
    StringBuilder lambdaClassDescriptor = new StringBuilder("L");

    // We always create lambda class in the same package where it is referenced.
    String packageDescriptor = accessedFrom.getHolderType().getPackageDescriptor();
    if (!packageDescriptor.isEmpty()) {
      lambdaClassDescriptor.append(packageDescriptor).append('/');
    }

    // Lambda class name prefix
    lambdaClassDescriptor.append(LambdaRewriter.LAMBDA_CLASS_NAME_PREFIX);

    // If the lambda class should match 1:1 the class it is accessed from, we
    // just add the name of this type to make lambda class name unique.
    // It also helps link the class lambda originated from in some cases.
    if (match.delegatesToLambdaImplMethod() || match.needsAccessor(accessedFrom)) {
      lambdaClassDescriptor.append(accessedFrom.getHolderType().getName()).append('$');
    }

    // Add unique lambda descriptor id
    lambdaClassDescriptor.append(match.uniqueId).append(';');
    return appView.dexItemFactory().createType(lambdaClassDescriptor.toString());
  }

  public final DexProgramClass getOrCreateLambdaClass() {
    return lazyDexClass.get();
  }

  private DexProgramClass synthesizeLambdaClass() {
    DexMethod mainMethod =
        appView.dexItemFactory().createMethod(type, descriptor.erasedProto, descriptor.name);

    DexProgramClass clazz =
        new DexProgramClass(
            type,
            null,
            new SynthesizedOrigin("lambda desugaring", getClass()),
            // Make the synthesized class public, as it might end up being accessed from a different
            // classloader (package private access is not allowed across classloaders, b/72538146).
            ClassAccessFlags.fromDexAccessFlags(
                Constants.ACC_FINAL | Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC),
            appView.dexItemFactory().objectType,
            buildInterfaces(),
            appView.dexItemFactory().createString("lambda"),
            null,
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            synthesizeStaticFields(),
            synthesizeInstanceFields(),
            synthesizeDirectMethods(),
            synthesizeVirtualMethods(mainMethod),
            appView.dexItemFactory().getSkipNameValidationForTesting(),
            LambdaClass::computeChecksumForSynthesizedClass);
    appView.appInfo().addSynthesizedClass(clazz, false);

    // The method addSynthesizedFrom() may be called concurrently. To avoid a Concurrent-
    // ModificationException we must use synchronization.
    synchronized (synthesizedFrom) {
      synthesizedFrom.forEach(clazz::addSynthesizedFrom);
    }
    return clazz;
  }

  private static long computeChecksumForSynthesizedClass(DexProgramClass clazz) {
    // Checksum of synthesized classes are compute based off the depending input. This might
    // create false positives (ie: unchanged lambda class detected as changed even thought only
    // an unrelated part from a synthesizedFrom class is changed).

    // Ideally, we should use some hashcode of the dex program class that is deterministic across
    // compiles.
    Collection<DexProgramClass> synthesizedFrom = clazz.getSynthesizedFrom();
    ByteBuffer buffer = ByteBuffer.allocate(synthesizedFrom.size() * Longs.BYTES);
    for (DexProgramClass from : synthesizedFrom) {
      buffer.putLong(from.getChecksum());
    }
    CRC32 crc = new CRC32();
    byte[] array = buffer.array();
    crc.update(array, 0, array.length);
    return crc.getValue();
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

  void addSynthesizedFrom(DexProgramClass clazz) {
    assert clazz != null;
    synchronized (synthesizedFrom) {
      if (synthesizedFrom.add(clazz)) {
        // The lambda class may already have been synthesized, and we therefore need to update the
        // synthesized lambda class as well.
        getOrCreateLambdaClass().addSynthesizedFrom(clazz);
      }
    }
  }

  // Synthesize virtual methods.
  private DexEncodedMethod[] synthesizeVirtualMethods(DexMethod mainMethod) {
    DexEncodedMethod[] methods = new DexEncodedMethod[1 + descriptor.bridges.size()];
    int index = 0;

    // Synthesize main method.
    methods[index++] =
        new DexEncodedMethod(
            mainMethod,
            MethodAccessFlags.fromSharedAccessFlags(
                Constants.ACC_PUBLIC | Constants.ACC_FINAL, false),
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            LambdaMainMethodSourceCode.build(this, mainMethod),
            true);

    // Synthesize bridge methods.
    for (DexProto bridgeProto : descriptor.bridges) {
      DexMethod bridgeMethod =
          appView.dexItemFactory().createMethod(type, bridgeProto, descriptor.name);
      methods[index++] =
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
              true);
    }
    return methods;
  }

  // Synthesize direct methods.
  private DexEncodedMethod[] synthesizeDirectMethods() {
    boolean stateless = isStateless();
    DexEncodedMethod[] methods = new DexEncodedMethod[stateless ? 2 : 1];

    // Constructor.
    methods[0] =
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
            true);

    // Class constructor for stateless lambda classes.
    if (stateless) {
      methods[1] =
          new DexEncodedMethod(
              classConstructor,
              MethodAccessFlags.fromSharedAccessFlags(
                  Constants.ACC_SYNTHETIC | Constants.ACC_STATIC, true),
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              LambdaClassConstructorSourceCode.build(this),
              true);
    }
    return methods;
  }

  // Synthesize instance fields to represent captured values.
  private DexEncodedField[] synthesizeInstanceFields() {
    DexType[] fieldTypes = descriptor.captures.values;
    int fieldCount = fieldTypes.length;
    DexEncodedField[] fields = new DexEncodedField[fieldCount];
    for (int i = 0; i < fieldCount; i++) {
      FieldAccessFlags accessFlags =
          FieldAccessFlags.fromSharedAccessFlags(
              Constants.ACC_FINAL | Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC);
      fields[i] =
          new DexEncodedField(
              getCaptureField(i),
              accessFlags,
              FieldTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              null);
    }
    return fields;
  }

  // Synthesize static fields to represent singleton instance.
  private DexEncodedField[] synthesizeStaticFields() {
    if (!isStateless()) {
      return DexEncodedField.EMPTY_ARRAY;
    }

    // Create instance field for stateless lambda.
    assert this.lambdaField != null;
    DexEncodedField[] fields = new DexEncodedField[1];
    fields[0] =
        new DexEncodedField(
            this.lambdaField,
            FieldAccessFlags.fromSharedAccessFlags(
                Constants.ACC_PUBLIC
                    | Constants.ACC_FINAL
                    | Constants.ACC_SYNTHETIC
                    | Constants.ACC_STATIC),
            FieldTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            DexValueNull.NULL);
    return fields;
  }

  // Build a list of implemented interfaces.
  private DexTypeList buildInterfaces() {
    List<DexType> interfaces = descriptor.interfaces;
    return interfaces.isEmpty()
        ? DexTypeList.empty()
        : new DexTypeList(interfaces.toArray(DexType.EMPTY_ARRAY));
  }

  // Creates a delegation target for this particular lambda class. Note that we
  // should always be able to create targets for the lambdas we support.
  private Target createTarget(ProgramMethod accessedFrom) {
    if (descriptor.delegatesToLambdaImplMethod()) {
      return createLambdaImplMethodTarget(accessedFrom);
    }

    // Method referenced directly, without lambda$ method.
    switch (descriptor.implHandle.type) {
      case INVOKE_SUPER:
        throw new Unimplemented("Method references to super methods are not yet supported");
      case INVOKE_INTERFACE:
        return createInterfaceMethodTarget(accessedFrom);
      case INVOKE_CONSTRUCTOR:
        return createConstructorTarget(accessedFrom);
      case INVOKE_STATIC:
        return createStaticMethodTarget(accessedFrom);
      case INVOKE_DIRECT:
      case INVOKE_INSTANCE:
        return createInstanceMethodTarget(accessedFrom);
      default:
        throw new Unreachable("Unexpected method handle type in " + descriptor.implHandle);
    }
  }

  private Target createLambdaImplMethodTarget(ProgramMethod accessedFrom) {
    DexMethodHandle implHandle = descriptor.implHandle;
    assert implHandle != null;
    DexMethod implMethod = implHandle.asMethod();

    // Lambda$ method. We should always find it. If not found an ICCE can be expected to be thrown.
    assert implMethod.holder == accessedFrom.getHolderType();
    assert descriptor.verifyTargetFoundInClass(accessedFrom.getHolderType());
    if (implHandle.type.isInvokeStatic()) {
      ResolutionResult resolution =
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

    assert implHandle.type.isInvokeInstance() || implHandle.type.isInvokeDirect();

    // If the lambda$ method is an instance-private method on an interface we convert it into a
    // public static method as it will be placed on the companion class.
    if (implHandle.type.isInvokeDirect()
        && appView.definitionFor(implMethod.holder).isInterface()) {
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

    if (!descriptor.needsAccessor(accessedFrom)) {
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

    if (!descriptor.needsAccessor(accessedFrom)) {
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

    if (!descriptor.needsAccessor(accessedFrom)) {
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
    assert !descriptor.needsAccessor(accessedFrom);
    return new NoAccessorMethodTarget(Invoke.Type.INTERFACE);
  }

  private DexString generateUniqueLambdaMethodName() {
    return appView
        .dexItemFactory()
        .createString(LambdaRewriter.EXPECTED_LAMBDA_METHOD_PREFIX + descriptor.uniqueId);
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
    abstract ProgramMethod ensureAccessibility(boolean allowMethodModification);

    // Ensure access of the referenced symbol(s).
    public ProgramMethod ensureAccessibilityIfNeeded(boolean allowMethodModification) {
      if (!hasEnsuredAccessibility) {
        accessibilityBridge = ensureAccessibility(allowMethodModification);
        hasEnsuredAccessibility = true;
      }
      return accessibilityBridge;
    }

    boolean isInterface() {
      return descriptor.implHandle.isInterface;
    }
  }

  // Used for targeting methods referenced directly without creating accessors.
  private final class NoAccessorMethodTarget extends Target {

    NoAccessorMethodTarget(Invoke.Type invokeType) {
      super(descriptor.implHandle.asMethod(), invokeType);
    }

    @Override
    ProgramMethod ensureAccessibility(boolean allowMethodModification) {
      return null;
    }
  }

  // Used for static private lambda$ methods. Only needs access relaxation.
  private final class StaticLambdaImplTarget extends Target {

    final ProgramMethod target;

    StaticLambdaImplTarget(ProgramMethod target) {
      super(descriptor.implHandle.asMethod(), Invoke.Type.STATIC);
      this.target = target;
    }

    @Override
    ProgramMethod ensureAccessibility(boolean allowMethodModification) {
      // We already found the static method to be called, just relax its accessibility.
      target.getDefinition().accessFlags.unsetPrivate();
      if (target.getHolder().isInterface()) {
        target.getDefinition().accessFlags.setPublic();
      }
      return null;
    }
  }

  // Used for instance private lambda$ methods on interfaces which need to be converted to public
  // static methods. They can't remain instance methods as they will end up on the companion class.
  private class InterfaceLambdaImplTarget extends Target {

    InterfaceLambdaImplTarget(DexMethod staticMethod) {
      super(staticMethod, Type.STATIC);
    }

    @Override
    ProgramMethod ensureAccessibility(boolean allowMethodModification) {
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
                    // Always make the method public to provide access when r8 minification is
                    // allowed to move the lambda class accessing this method to another package
                    // (-allowaccessmodification).
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
                    rewriter.forcefullyMoveMethod(encodedMethod.method, callTarget);

                    DexEncodedMethod.setDebugInfoWithFakeThisParameter(
                        newMethod.getCode(), callTarget.getArity(), appView);
                    return newMethod;
                  });

      assert replacement != null
          : "Unexpected failure to find direct lambda target for: " + implMethod.qualifiedName();

      return new ProgramMethod(implMethodHolder, replacement);
    }
  }

  class InvalidLambdaImplTarget extends Target {

    final DexType exceptionType;

    public InvalidLambdaImplTarget(DexMethod callTarget, Type invokeType, DexType exceptionType) {
      super(callTarget, invokeType);
      this.exceptionType = exceptionType;
    }

    @Override
    ProgramMethod ensureAccessibility(boolean allowMethodModification) {
      return null;
    }
  }

  // Used for instance private lambda$ methods which need to be converted to public methods.
  private class InstanceLambdaImplTarget extends Target {

    InstanceLambdaImplTarget(DexMethod staticMethod) {
      super(staticMethod, Type.VIRTUAL);
    }

    @Override
    ProgramMethod ensureAccessibility(boolean allowMethodModification) {
      // When compiling with whole program optimization, check that we are not inplace modifying.
      assert !(appView.enableWholeProgramOptimizations() && allowMethodModification);
      // For all instantiation points for which the compiler creates lambda$
      // methods, it creates these methods in the same class/interface.
      DexMethod implMethod = descriptor.implHandle.asMethod();
      DexProgramClass implMethodHolder = appView.definitionFor(implMethod.holder).asProgramClass();
      return allowMethodModification
          ? modifyLambdaImplementationMethod(implMethod, implMethodHolder)
          : createSyntheticAccessor(implMethod, implMethodHolder);
    }

    private ProgramMethod modifyLambdaImplementationMethod(
        DexMethod implMethod, DexProgramClass implMethodHolder) {
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
                    rewriter.forcefullyMoveMethod(encodedMethod.method, callTarget);
                    return newMethod;
                  });
      return new ProgramMethod(implMethodHolder, replacement);
    }

    private ProgramMethod createSyntheticAccessor(
        DexMethod implMethod, DexProgramClass implMethodHolder) {
      MethodAccessFlags accessorFlags =
          MethodAccessFlags.fromSharedAccessFlags(
              Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC, false);

      ForwardMethodSourceCode.Builder forwardSourceCodeBuilder =
          ForwardMethodSourceCode.builder(callTarget)
              .setReceiver(implMethod.holder)
              .setTargetReceiver(implMethod.holder)
              .setTarget(implMethod)
              .setInvokeType(Type.DIRECT)
              .setIsInterface(false);

      DexEncodedMethod accessorEncodedMethod =
          new DexEncodedMethod(
              callTarget,
              accessorFlags,
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              new SynthesizedCode(
                  forwardSourceCodeBuilder::build,
                  registry -> registry.registerInvokeDirect(implMethod)),
              true);
      accessorEncodedMethod.setLibraryMethodOverride(OptionalBool.FALSE);

      implMethodHolder.addVirtualMethod(accessorEncodedMethod);
      return new ProgramMethod(implMethodHolder, accessorEncodedMethod);
    }
  }

  // Used for instance/static methods or constructors accessed via
  // synthesized accessor method. Needs accessor method to be created.
  private class ClassMethodWithAccessorTarget extends Target {

    ClassMethodWithAccessorTarget(DexMethod accessorMethod) {
      super(accessorMethod, Invoke.Type.STATIC);
    }

    @Override
    ProgramMethod ensureAccessibility(boolean allowMethodModification) {
      // Create a static accessor with proper accessibility.
      DexProgramClass accessorClass = appView.definitionForProgramType(callTarget.holder);
      assert accessorClass != null;

      // Always make the method public to provide access when r8 minification is allowed to move
      // the lambda class accessing this method to another package (-allowaccessmodification).
      MethodAccessFlags accessorFlags =
          MethodAccessFlags.fromSharedAccessFlags(
              Constants.ACC_SYNTHETIC | Constants.ACC_STATIC | Constants.ACC_PUBLIC,
              false);

      DexEncodedMethod accessorEncodedMethod =
          new DexEncodedMethod(
              callTarget,
              accessorFlags,
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              AccessorMethodSourceCode.build(LambdaClass.this, callTarget),
              true);

      // We may arrive here concurrently so we need must update the methods of the class atomically.
      synchronized (accessorClass) {
        accessorClass.addDirectMethod(accessorEncodedMethod);
      }

      return new ProgramMethod(accessorClass, accessorEncodedMethod);
    }
  }
}
