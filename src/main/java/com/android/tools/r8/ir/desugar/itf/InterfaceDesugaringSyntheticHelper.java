// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodDesugaringEventConsumer.emptyInterfaceMethodDesugaringEventConsumer;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfInitClass;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.InvalidCode;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ThrowNullCode;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.ClasspathEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.L8ProgramEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.synthesis.SyntheticClassBuilder;
import com.android.tools.r8.synthesis.SyntheticItems.SyntheticKindSelector;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.util.function.Predicate;

public class InterfaceDesugaringSyntheticHelper {

  // Any interface method desugared code can be version 1.7 at the most.
  // Note: we always desugar both default/static (v1.8) and private (v9) when targeting api < N.
  public static final CfVersion MAX_INTERFACE_DESUGARED_CF_VERSION = CfVersion.V1_7;

  public static CfVersion getInterfaceDesugaredCfVersion(CfVersion existing) {
    return Ordered.min(existing, MAX_INTERFACE_DESUGARED_CF_VERSION);
  }

  // Use InterfaceDesugaringForTesting for public accesses in tests.
  static final String COMPANION_CLASS_NAME_SUFFIX = "$-CC";
  static final String DEFAULT_METHOD_PREFIX = "$default$";
  public static final String PRIVATE_METHOD_PREFIX = "$private$";

  private final AppView<?> appView;
  private final Predicate<DexType> shouldIgnoreFromReportsPredicate;

  public InterfaceDesugaringSyntheticHelper(AppView<?> appView) {
    this.appView = appView;
    this.shouldIgnoreFromReportsPredicate = getShouldIgnoreFromReportsPredicate(appView);
  }

  public enum InterfaceMethodDesugaringMode {
    ALL,
    EMULATED_INTERFACE_ONLY,
    NONE
  }

  public static InterfaceMethodDesugaringMode getInterfaceMethodDesugaringMode(
      InternalOptions options) {
    if (options.isInterfaceMethodDesugaringEnabled()) {
      return InterfaceMethodDesugaringMode.ALL;
    }
    if (options.machineDesugaredLibrarySpecification.getEmulatedInterfaces().isEmpty()) {
      return InterfaceMethodDesugaringMode.NONE;
    }
    return InterfaceMethodDesugaringMode.EMULATED_INTERFACE_ONLY;
  }

  boolean isEmulatedInterface(DexType itf) {
    return appView
        .options()
        .machineDesugaredLibrarySpecification
        .getEmulatedInterfaces()
        .containsKey(itf);
  }

  boolean isRewrittenEmulatedInterface(DexType itf) {
    return appView
        .options()
        .machineDesugaredLibrarySpecification
        .isEmulatedInterfaceRewrittenType(itf);
  }

  DexType getEmulatedInterface(DexType type) {
    EmulatedInterfaceDescriptor interfaceDescriptor =
        appView.options().machineDesugaredLibrarySpecification.getEmulatedInterfaces().get(type);
    return interfaceDescriptor == null ? null : interfaceDescriptor.getRewrittenType();
  }

  boolean isInDesugaredLibrary(DexClass clazz) {
    assert clazz.isLibraryClass() || appView.options().isDesugaredLibraryCompilation();
    if (isEmulatedInterface(clazz.type)) {
      return true;
    }
    if (appView
        .options()
        .machineDesugaredLibrarySpecification
        .getMaintainType()
        .contains(clazz.type)) {
      return true;
    }
    return appView.typeRewriter.hasRewrittenType(clazz.type, appView);
  }

  final boolean isCompatibleDefaultMethod(DexEncodedMethod method) {
    assert !method.accessFlags.isConstructor();
    assert !method.accessFlags.isStatic();

    if (method.accessFlags.isAbstract()) {
      return false;
    }
    if (method.accessFlags.isNative()) {
      throw new Unimplemented("Native default interface methods are not yet supported.");
    }
    if (!method.accessFlags.isPublic()) {
      // NOTE: even though the class is allowed to have non-public interface methods
      // with code, for example private methods, all such methods we are aware of are
      // created by the compiler for stateful lambdas and they must be converted into
      // static methods by lambda desugaring by this time.
      throw new Unimplemented("Non public default interface methods are not yet supported.");
    }
    return true;
  }

  public boolean verifyKind(DerivedMethod method, SyntheticKindSelector kindSelector) {
    SyntheticKind kind = kindSelector.select(appView.getSyntheticItems().getNaming());
    assert method.getHolderKind(appView).equals(kind);
    return true;
  }

  DexMethod emulatedInterfaceDispatchMethod(DerivedMethod method, DexType holder) {
    assert verifyKind(method, kinds -> kinds.EMULATED_INTERFACE_CLASS);
    DexProto newProto = appView.dexItemFactory().prependHolderToProto(method.getMethod());
    return appView.dexItemFactory().createMethod(holder, newProto, method.getName());
  }

  DexMethod emulatedInterfaceInterfaceMethod(DerivedMethod method) {
    assert method.getHolderKind(appView) == null;
    return method.getMethod();
  }

  public static String getCompanionClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1) + COMPANION_CLASS_NAME_SUFFIX + ";";
  }

  // Gets the companion class for the interface `type`.
  public static DexType getCompanionClassType(DexType type, DexItemFactory factory) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String ccTypeDescriptor = getCompanionClassDescriptor(descriptor);
    return factory.createSynthesizedType(ccTypeDescriptor);
  }

  // Checks if `type` is a companion class.
  public static boolean isCompanionClassType(DexType type) {
    return type.descriptor.toString().endsWith(COMPANION_CLASS_NAME_SUFFIX + ";");
  }

  // Gets the interface class for a companion class `type`.
  DexType getInterfaceClassType(DexType type) {
    return getInterfaceClassType(type, appView.dexItemFactory());
  }

  // Gets the interface class for a companion class `type`.
  public static DexType getInterfaceClassType(DexType type, DexItemFactory factory) {
    assert isCompanionClassType(type);
    String descriptor = type.descriptor.toString();
    String interfaceTypeDescriptor =
        descriptor.substring(0, descriptor.length() - 1 - COMPANION_CLASS_NAME_SUFFIX.length())
            + ";";
    return factory.createType(interfaceTypeDescriptor);
  }

  // TODO(b/198273164): This should take the context class and not just a type.
  DexClasspathClass ensureEmulatedInterfaceMarkerInterface(DexType type) {
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClassFromType(
            kinds -> kinds.EMULATED_INTERFACE_MARKER_CLASS,
            type,
            appView,
            SyntheticClassBuilder::setInterface,
            ignored -> {});
  }

  DexClassAndMethod lookupMaximallySpecificIncludingSelf(
      DexClass initialResolutionHolder, DexClassAndMethod method) {
    assert method.getHolderType().isClassType();
    if (method.getHolder().isInterface()) {
      return method;
    }
    return appView
        .appInfoForDesugaring()
        .lookupMaximallySpecificMethod(initialResolutionHolder, method.getReference());
  }

  EmulatedDispatchMethodDescriptor getEmulatedDispatchDescriptor(
      DexClass initialResolutionHolder, DexClassAndMethod method) {
    if (method == null) {
      return null;
    }
    assert initialResolutionHolder != null;
    if (!requiresEmulatedDispatch(method)) {
      return null;
    }
    DexClassAndMethod maximallySpecificMethod =
        lookupMaximallySpecificIncludingSelf(initialResolutionHolder, method);
    if (maximallySpecificMethod == null) {
      return null;
    }
    return appView
        .options()
        .machineDesugaredLibrarySpecification
        .getEmulatedInterfaceEmulatedDispatchMethodDescriptor(
            maximallySpecificMethod.getReference());
  }

  private boolean requiresEmulatedDispatch(DexClassAndMethod method) {
    return method.isLibraryMethod()
        || isEmulatedInterface(method.getHolderType())
        || appView
            .options()
            .machineDesugaredLibrarySpecification
            .getEmulatedVirtualRetargetThroughEmulatedInterface()
            .containsKey(method.getReference());
  }

  DerivedMethod computeEmulatedInterfaceDispatchMethod(MethodResolutionResult resolutionResult) {
    EmulatedDispatchMethodDescriptor descriptor =
        getEmulatedDispatchDescriptor(
            resolutionResult.getInitialResolutionHolder(), resolutionResult.getResolutionPair());
    return descriptor == null ? null : descriptor.getEmulatedDispatchMethod();
  }

  DerivedMethod computeEmulatedInterfaceForwardingMethod(
      DexClass initialResolutionHolder, DexClassAndMethod method) {
    if (method == null) {
      return null;
    }
    DexMethod retarget =
        appView
            .options()
            .machineDesugaredLibrarySpecification
            .getEmulatedVirtualRetargetThroughEmulatedInterface()
            .get(method.getReference());
    if (retarget != null) {
      return new DerivedMethod(retarget);
    }
    EmulatedDispatchMethodDescriptor descriptor =
        getEmulatedDispatchDescriptor(initialResolutionHolder, method);
    return descriptor == null ? null : descriptor.getForwardingMethod();
  }

  /**
   * In the {@link ClassProcessor}, we only conditionally add the synthesized methods to the program
   * in {@link ClassProcessor#resolveForwardingMethods}. Therefore, we do not report the synthesis
   * to the given {@param eventConsumer} at this point, as this would lead to over-reporting.
   */
  // TODO(b/267144253): Avoid over-synthesizing in the ClassProcessor. Then we should be able to
  //  always report the synthesized method to the InterfaceProcessingDesugaringEventConsumer at this
  //  point.
  DexMethod ensureEmulatedInterfaceForwardingMethod(
      DerivedMethod method, InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    return ensureEmulatedInterfaceForwardingMethod(
        method, emptyInterfaceMethodDesugaringEventConsumer());
  }

  /**
   * Forwarding methods synthesized by the {@link ProgramEmulatedInterfaceSynthesizer} are currently
   * not reported to the {@link L8ProgramEmulatedInterfaceSynthesizerEventConsumer}, since we
   * already report the entire synthetic class after all of its methods have been created to the
   * event consumer in {@link
   * ProgramEmulatedInterfaceSynthesizer#synthesizeProgramEmulatedInterface}.
   */
  DexMethod ensureEmulatedInterfaceForwardingMethod(
      DerivedMethod method, L8ProgramEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    return ensureEmulatedInterfaceForwardingMethod(
        method, emptyInterfaceMethodDesugaringEventConsumer());
  }

  DexMethod ensureEmulatedInterfaceForwardingMethod(
      DerivedMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    if (method.getHolderKind(appView) == null) {
      return method.getMethod();
    }
    assert verifyKind(method, kinds -> kinds.COMPANION_CLASS);
    DexClassAndMethod resolvedMethod =
        appView
            .appInfoForDesugaring()
            .resolveMethodLegacy(method.getMethod(), true)
            .getResolutionPair();
    return ensureDefaultAsMethodOfCompanionClassStub(resolvedMethod, eventConsumer).getReference();
  }

  DexClassAndMethod ensureEmulatedInterfaceDispatchMethod(
      DerivedMethod emulatedDispatchMethod,
      ClasspathEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    assert verifyKind(emulatedDispatchMethod, kinds -> kinds.EMULATED_INTERFACE_CLASS);
    DexClassAndMethod method =
        appView
            .appInfoForDesugaring()
            .resolveMethodLegacy(emulatedDispatchMethod.getMethod(), true)
            .getResolutionPair();
    assert verifyKind(emulatedDispatchMethod, kinds -> kinds.EMULATED_INTERFACE_CLASS);
    if (method.isProgramMethod()) {
      assert appView.options().isDesugaredLibraryCompilation();
      DexProgramClass emulatedInterface =
          appView
              .getSyntheticItems()
              .getExistingFixedClass(
                  kinds -> kinds.EMULATED_INTERFACE_CLASS,
                  method.asProgramMethod().getHolder(),
                  appView);
      DexMethod emulatedInterfaceMethod =
          emulatedInterfaceDispatchMethod(emulatedDispatchMethod, emulatedInterface.type);
      assert emulatedInterface.lookupProgramMethod(emulatedInterfaceMethod) != null;
      return emulatedInterface.lookupProgramMethod(emulatedInterfaceMethod);
    }
    // The holder is not used.
    DexMethod emulatedInterfaceMethod =
        emulatedInterfaceDispatchMethod(
            emulatedDispatchMethod, appView.dexItemFactory().objectType);
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClassMethod(
            emulatedInterfaceMethod.getName(),
            emulatedInterfaceMethod.getProto(),
            kinds -> kinds.EMULATED_INTERFACE_CLASS,
            method.getHolder().asClasspathOrLibraryClass(),
            appView,
            classBuilder -> {},
            eventConsumer::acceptClasspathEmulatedInterface,
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(ignore -> ThrowNullCode.get()));
  }

  /**
   * In the {@link ClassProcessor}, we only conditionally add the synthesized methods to the program
   * in {@link ClassProcessor#resolveForwardingMethods}. Therefore, we do not report the synthesis
   * to the given {@param eventConsumer} at this point, as this would lead to over-reporting.
   */
  // TODO(b/267144253): Avoid over-synthesizing in the ClassProcessor. Then we should be able to
  //  always report the synthesized method to the InterfaceProcessingDesugaringEventConsumer at this
  //  point.
  DexClassAndMethod ensureDefaultAsMethodOfCompanionClassStub(
      DexClassAndMethod method, InterfaceProcessingDesugaringEventConsumer eventConsumer) {
    return ensureDefaultAsMethodOfCompanionClassStub(
        method, emptyInterfaceMethodDesugaringEventConsumer());
  }

  DexClassAndMethod ensureDefaultAsMethodOfCompanionClassStub(
      DexClassAndMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    if (method.isProgramMethod()) {
      return ensureDefaultAsMethodOfProgramCompanionClassStub(
          method.asProgramMethod(), eventConsumer);
    }
    ClasspathOrLibraryClass context = method.getHolder().asClasspathOrLibraryClass();
    DexMethod companionMethodReference =
        defaultAsMethodOfCompanionClass(method.getReference(), appView.dexItemFactory());
    return ensureMethodOfClasspathCompanionClassStub(companionMethodReference, context, appView);
  }

  DexClassAndMethod ensureStaticAsMethodOfCompanionClassStub(
      DexClassAndMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    if (method.isProgramMethod()) {
      return ensureStaticAsMethodOfProgramCompanionClassStub(
          method.asProgramMethod(), eventConsumer);
    } else {
      ClasspathOrLibraryClass context = method.getHolder().asClasspathOrLibraryClass();
      DexMethod companionMethodReference = staticAsMethodOfCompanionClass(method);
      return ensureMethodOfClasspathCompanionClassStub(companionMethodReference, context, appView);
    }
  }

  ProgramMethod ensureDefaultAsMethodOfProgramCompanionClassStub(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    DexEncodedMethod virtual = method.getDefinition();
    DexMethod companionMethod =
        defaultAsMethodOfCompanionClass(method.getReference(), appView.dexItemFactory());
    return InterfaceProcessor.ensureCompanionMethod(
        method.getHolder(),
        companionMethod.getName(),
        companionMethod.getProto(),
        appView,
        methodBuilder -> {
          MethodAccessFlags newFlags = method.getAccessFlags().copy();
          newFlags.promoteToStatic();
          methodBuilder
              .setAccessFlags(newFlags)
              .setGenericSignature(MethodTypeSignature.noSignature())
              // Will be traced by the enqueuer.
              .disableAndroidApiLevelCheck()
              .setAnnotations(
                  virtual
                      .annotations()
                      .methodParametersWithFakeThisArguments(appView.dexItemFactory()))
              .setParameterAnnotationsList(
                  virtual.getParameterAnnotations().withFakeThisParameter())
              .setCode(ignored -> InvalidCode.getInstance());
        },
        companion -> eventConsumer.acceptDefaultAsCompanionMethod(method, companion));
  }

  ProgramMethod ensurePrivateAsMethodOfProgramCompanionClassStub(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    DexMethod companionMethod =
        privateAsMethodOfCompanionClass(method.getReference(), appView.dexItemFactory());
    DexEncodedMethod definition = method.getDefinition();
    return InterfaceProcessor.ensureCompanionMethod(
        method.getHolder(),
        companionMethod.getName(),
        companionMethod.getProto(),
        appView,
        methodBuilder -> {
          MethodAccessFlags newFlags = definition.getAccessFlags().copy();
          assert newFlags.isPrivate();
          newFlags.promoteToPublic();
          newFlags.promoteToStatic();
          methodBuilder
              .setAccessFlags(newFlags)
              .setGenericSignature(definition.getGenericSignature())
              .setAnnotations(definition.annotations())
              // Will be traced by the enqueuer.
              .disableAndroidApiLevelCheck()
              // TODO(b/200938394): Should this not also be updating with a fake 'this'
              .setParameterAnnotationsList(definition.getParameterAnnotations())
              .setCode(ignored -> InvalidCode.getInstance());
        },
        companion -> eventConsumer.acceptPrivateAsCompanionMethod(method, companion));
  }

  // Represent a static interface method as a method of companion class.
  private DexMethod staticAsMethodOfCompanionClass(DexClassAndMethod method) {
    return staticAsMethodOfCompanionClass(method.getReference(), appView.dexItemFactory());
  }

  public static DexMethod staticAsMethodOfCompanionClass(DexMethod method, DexItemFactory factory) {
    DexType companionClassType = getCompanionClassType(method.getHolderType(), factory);
    return method.withHolder(companionClassType, factory);
  }

  private static DexMethod instanceAsMethodOfCompanionClass(
      DexMethod method, String prefix, DexItemFactory factory) {
    // Add an implicit argument to represent the receiver.
    DexType[] params = method.proto.parameters.values;
    DexType[] newParams = new DexType[params.length + 1];
    newParams[0] = method.holder;
    System.arraycopy(params, 0, newParams, 1, params.length);

    // Add prefix to avoid name conflicts.
    return factory.createMethod(
        getCompanionClassType(method.holder, factory),
        factory.createProto(method.proto.returnType, newParams),
        factory.createString(prefix + method.name.toString()));
  }

  // Represent a default interface method as a method of companion class.
  public static DexMethod defaultAsMethodOfCompanionClass(
      DexMethod method, DexItemFactory factory) {
    return instanceAsMethodOfCompanionClass(method, DEFAULT_METHOD_PREFIX, factory);
  }

  // Represent a private instance interface method as a method of companion class.
  static DexMethod privateAsMethodOfCompanionClass(DexMethod method, DexItemFactory factory) {
    // Add an implicit argument to represent the receiver.
    return instanceAsMethodOfCompanionClass(method, PRIVATE_METHOD_PREFIX, factory);
  }

  DexMethod privateAsMethodOfCompanionClass(DexClassAndMethod method) {
    return privateAsMethodOfCompanionClass(method.getReference(), appView.dexItemFactory());
  }

  private static DexClassAndMethod ensureMethodOfClasspathCompanionClassStub(
      DexMethod companionMethodReference, ClasspathOrLibraryClass context, AppView<?> appView) {
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClassMethod(
            companionMethodReference.getName(),
            companionMethodReference.getProto(),
            kinds -> kinds.COMPANION_CLASS,
            context,
            appView,
            classBuilder -> {},
            ignored -> {},
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(ignore -> ThrowNullCode.get()));
  }

  ProgramMethod ensureStaticAsMethodOfProgramCompanionClassStub(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    assert !method.getDefinition().isClassInitializer();
    if (method.getHolder().hasClassInitializer()) {
      ensureCompanionClassInitializesInterface(method.getHolder(), eventConsumer);
    }
    DexMethod companionMethodReference = staticAsMethodOfCompanionClass(method);
    DexEncodedMethod definition = method.getDefinition();
    return InterfaceProcessor.ensureCompanionMethod(
        method.getHolder(),
        companionMethodReference.getName(),
        companionMethodReference.getProto(),
        appView,
        methodBuilder -> {
          MethodAccessFlags newFlags = definition.getAccessFlags().copy();
          newFlags.promoteToPublic();
          methodBuilder
              .setAccessFlags(newFlags)
              .setGenericSignature(definition.getGenericSignature())
              .setAnnotations(definition.annotations())
              .setParameterAnnotationsList(definition.getParameterAnnotations())
              // Will be traced by the enqueuer.
              .disableAndroidApiLevelCheck()
              .setCode(ignored -> InvalidCode.getInstance());
        },
        companion -> eventConsumer.acceptStaticAsCompanionMethod(method, companion));
  }

  public ProgramMethod ensureMethodOfProgramCompanionClassStub(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    DexEncodedMethod definition = method.getDefinition();
    assert method.getHolder().isInterface();
    assert definition.isNonAbstractNonNativeMethod();
    assert definition.getCode() != null;
    assert !InvalidCode.isInvalidCode(definition.getCode());
    if (definition.isStatic()) {
      return ensureStaticAsMethodOfProgramCompanionClassStub(method, eventConsumer);
    }
    if (definition.isPrivate()) {
      return ensurePrivateAsMethodOfProgramCompanionClassStub(method, eventConsumer);
    }
    return ensureDefaultAsMethodOfProgramCompanionClassStub(method, eventConsumer);
  }

  private void ensureCompanionClassInitializesInterface(
      DexProgramClass iface, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    assert hasStaticMethodThatTriggersNonTrivialClassInitializer(iface);
    InterfaceProcessor.ensureCompanionMethod(
        iface,
        appView.dexItemFactory().classConstructorMethodName,
        appView.dexItemFactory().createProto(appView.dexItemFactory().voidType),
        appView,
        methodBuilder -> createCompanionClassInitializer(iface, methodBuilder),
        companionMethod ->
            eventConsumer.acceptCompanionClassClinit(
                iface.getProgramClassInitializer(), companionMethod));
  }

  private DexEncodedField ensureStaticClinitFieldToTriggerInterfaceInitialization(
      DexProgramClass iface) {
    DexEncodedField clinitField =
        findExistingStaticClinitFieldToTriggerInterfaceInitialization(iface);
    if (clinitField == null) {
      clinitField = createStaticClinitFieldToTriggerInterfaceInitialization(iface);
      iface.appendStaticField(clinitField);
    }
    return clinitField;
  }

  private boolean hasStaticMethodThatTriggersNonTrivialClassInitializer(DexProgramClass iface) {
    return iface.hasClassInitializer()
        && iface
            .getMethodCollection()
            .hasDirectMethods(method -> method.isStatic() && !method.isClassInitializer());
  }

  private DexEncodedField findExistingStaticClinitFieldToTriggerInterfaceInitialization(
      DexProgramClass iface) {
    // Don't select a field that has been marked dead, since we'll assert later that these fields
    // have been dead code eliminated.
    for (DexEncodedField field :
        iface.staticFields(field -> !field.isPrivate() && !field.getOptimizationInfo().isDead())) {
      return field;
    }
    return null;
  }

  private DexEncodedField createStaticClinitFieldToTriggerInterfaceInitialization(
      DexProgramClass iface) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexField clinitFieldReference =
        dexItemFactory.createFreshFieldNameWithoutHolder(
            iface.getType(),
            dexItemFactory.intType,
            "$desugar$clinit",
            candidate -> iface.lookupField(candidate) == null);
    return DexEncodedField.syntheticBuilder()
        .setField(clinitFieldReference)
        .setAccessFlags(
            FieldAccessFlags.builder().setPackagePrivate().setStatic().setSynthetic().build())
        .setStaticValue(DexValueInt.DEFAULT)
        // The api level is computed when tracing.
        .disableAndroidApiLevelCheck()
        .build();
  }

  private void createCompanionClassInitializer(
      DexProgramClass iface, SyntheticMethodBuilder methodBuilder) {
    methodBuilder
        .setAccessFlags(
            MethodAccessFlags.builder().setConstructor().setPackagePrivate().setStatic().build())
        .setClassFileVersion(getInterfaceDesugaredCfVersion(iface.getInitialClassFileVersion()))
        .setCode(
            method -> {
              if (appView.canUseInitClass()) {
                return new CfCode(
                    method.holder,
                    1,
                    0,
                    ImmutableList.of(
                        new CfInitClass(iface.getType()),
                        CfStackInstruction.POP,
                        CfReturnVoid.INSTANCE));
              }
              DexEncodedField clinitField =
                  ensureStaticClinitFieldToTriggerInterfaceInitialization(iface);
              DexType fieldType = clinitField.getType();
              boolean isWide = fieldType.isWideType();
              return new CfCode(
                  method.holder,
                  isWide ? 2 : 1,
                  0,
                  ImmutableList.of(
                      new CfStaticFieldRead(clinitField.getReference(), clinitField.getReference()),
                          CfStackInstruction.popType(fieldType),
                      CfReturnVoid.INSTANCE));
            });
  }

  private Predicate<DexType> getShouldIgnoreFromReportsPredicate(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    InternalOptions options = appView.options();
    DexString companionClassNameDescriptorSuffix =
        dexItemFactory.createString(
            InterfaceDesugaringSyntheticHelper.COMPANION_CLASS_NAME_SUFFIX + ";");

    return type -> {
      DexString descriptor = type.getDescriptor();
      return appView.typeRewriter.hasRewrittenType(type, appView)
          || descriptor.endsWith(companionClassNameDescriptorSuffix)
          || isRewrittenEmulatedInterface(type)
          || options.machineDesugaredLibrarySpecification.isCustomConversionRewrittenType(type)
          || appView.getDontWarnConfiguration().matches(type);
    };
  }

  boolean shouldIgnoreFromReports(DexType missing) {
    return shouldIgnoreFromReportsPredicate.test(missing);
  }

  public void warnMissingInterface(
      DexClass classToDesugar, DexClass implementing, DexType missing) {
    // We use contains() on non hashed collection, but we know it's a 8 cases collection.
    // j$ interfaces won't be missing, they are in the desugared library.
    if (shouldIgnoreFromReports(missing)) {
      return;
    }
    appView.options().warningMissingInterfaceForDesugar(classToDesugar, implementing, missing);
  }
}
