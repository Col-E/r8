// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInitClass;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.InvalidCode;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.ClasspathEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Opcodes;

public class InterfaceDesugaringSyntheticHelper {

  // Any interface method desugared code can be version 1.7 at the most.
  // Note: we always desugar both default/static (v1.8) and private (v9) when targeting api < N.
  public static final CfVersion MAX_INTERFACE_DESUGARED_CF_VERSION = CfVersion.V1_7;

  public static CfVersion getInterfaceDesugaredCfVersion(CfVersion existing) {
    return Ordered.min(existing, MAX_INTERFACE_DESUGARED_CF_VERSION);
  }

  // Use InterfaceDesugaringForTesting for public accesses in tests.
  static final String EMULATE_LIBRARY_CLASS_NAME_SUFFIX = "$-EL";
  static final String COMPANION_CLASS_NAME_SUFFIX = "$-CC";
  static final String DEFAULT_METHOD_PREFIX = "$default$";
  static final String PRIVATE_METHOD_PREFIX = "$private$";

  private final AppView<?> appView;
  private final Map<DexType, DexType> emulatedInterfaces;
  private final Predicate<DexType> shouldIgnoreFromReportsPredicate;

  public InterfaceDesugaringSyntheticHelper(AppView<?> appView) {
    this.appView = appView;
    emulatedInterfaces =
        appView.options().desugaredLibraryConfiguration.getEmulateLibraryInterface();

    this.shouldIgnoreFromReportsPredicate = getShouldIgnoreFromReportsPredicate(appView);
  }

  boolean isEmulatedInterface(DexType itf) {
    return emulatedInterfaces.containsKey(itf);
  }

  boolean isRewrittenEmulatedInterface(DexType itf) {
    return emulatedInterfaces.containsValue(itf);
  }

  Set<DexType> getEmulatedInterfaces() {
    return emulatedInterfaces.keySet();
  }

  DexType getEmulatedInterface(DexType type) {
    return emulatedInterfaces.get(type);
  }

  boolean isInDesugaredLibrary(DexClass clazz) {
    assert clazz.isLibraryClass() || appView.options().isDesugaredLibraryCompilation();
    if (isEmulatedInterface(clazz.type)) {
      return true;
    }
    return appView.rewritePrefix.hasRewrittenType(clazz.type, appView);
  }

  boolean dontRewrite(DexClassAndMethod method) {
    for (Pair<DexType, DexString> dontRewrite :
        appView.options().desugaredLibraryConfiguration.getDontRewriteInvocation()) {
      if (method.getHolderType() == dontRewrite.getFirst()
          && method.getName() == dontRewrite.getSecond()) {
        return true;
      }
    }
    return false;
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

  public DexMethod emulateInterfaceLibraryMethod(DexClassAndMethod method) {
    DexItemFactory factory = appView.dexItemFactory();
    return factory.createMethod(
        getEmulateLibraryInterfaceClassType(method.getHolderType(), factory),
        factory.prependTypeToProto(method.getHolderType(), method.getProto()),
        method.getName());
  }

  private static String getEmulateLibraryInterfaceClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1)
        + EMULATE_LIBRARY_CLASS_NAME_SUFFIX
        + ";";
  }

  public static DexType getEmulateLibraryInterfaceClassType(DexType type, DexItemFactory factory) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String elTypeDescriptor = getEmulateLibraryInterfaceClassDescriptor(descriptor);
    return factory.createSynthesizedType(elTypeDescriptor);
  }

  public static String getCompanionClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1) + COMPANION_CLASS_NAME_SUFFIX + ";";
  }

  // Gets the companion class for the interface `type`.
  static DexType getCompanionClassType(DexType type, DexItemFactory factory) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String ccTypeDescriptor = getCompanionClassDescriptor(descriptor);
    return factory.createSynthesizedType(ccTypeDescriptor);
  }

  // Checks if `type` is a companion class.
  public static boolean isCompanionClassType(DexType type) {
    return type.descriptor.toString().endsWith(COMPANION_CLASS_NAME_SUFFIX + ";");
  }

  public static boolean isEmulatedLibraryClassType(DexType type) {
    return type.descriptor.toString().endsWith(EMULATE_LIBRARY_CLASS_NAME_SUFFIX + ";");
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

  DexClassAndMethod ensureEmulatedInterfaceMethod(
      DexClassAndMethod method, ClasspathEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    DexMethod emulatedInterfaceMethod = emulateInterfaceLibraryMethod(method);
    if (method.isProgramMethod()) {
      assert appView.options().isDesugaredLibraryCompilation();
      DexProgramClass emulatedInterface =
          appView
              .getSyntheticItems()
              .getExistingFixedClass(
                  SyntheticKind.EMULATED_INTERFACE_CLASS,
                  method.asProgramMethod().getHolder(),
                  appView);
      return emulatedInterface.lookupProgramMethod(emulatedInterfaceMethod);
    }
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClassMethod(
            emulatedInterfaceMethod.getName(),
            emulatedInterfaceMethod.getProto(),
            SyntheticKind.EMULATED_INTERFACE_CLASS,
            method.getHolder().asClasspathOrLibraryClass(),
            appView,
            classBuilder -> {},
            clazz -> {
              // TODO(b/183998768): When interface method desugaring is cf to cf in R8, the
              //  eventConsumer should always be non null.
              if (eventConsumer != null) {
                eventConsumer.acceptClasspathEmulatedInterface(clazz);
              }
            },
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(DexEncodedMethod::buildEmptyThrowingCfCode));
  }

  DexClassAndMethod ensureDefaultAsMethodOfCompanionClassStub(DexClassAndMethod method) {
    if (method.isProgramMethod()) {
      return ensureDefaultAsMethodOfProgramCompanionClassStub(method.asProgramMethod());
    }
    ClasspathOrLibraryClass context = method.getHolder().asClasspathOrLibraryClass();
    DexMethod companionMethodReference =
        defaultAsMethodOfCompanionClass(method.getReference(), appView.dexItemFactory());
    return ensureMethodOfClasspathCompanionClassStub(companionMethodReference, context, appView);
  }

  DexClassAndMethod ensureStaticAsMethodOfCompanionClassStub(
      DexClassAndMethod method, Consumer<ProgramMethod> companionClinitConsumer) {
    if (method.isProgramMethod()) {
      return ensureStaticAsMethodOfProgramCompanionClassStub(
          method.asProgramMethod(), companionClinitConsumer);
    } else {
      ClasspathOrLibraryClass context = method.getHolder().asClasspathOrLibraryClass();
      DexMethod companionMethodReference = staticAsMethodOfCompanionClass(method);
      return ensureMethodOfClasspathCompanionClassStub(companionMethodReference, context, appView);
    }
  }

  ProgramMethod ensureDefaultAsMethodOfProgramCompanionClassStub(ProgramMethod method) {
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
              .setAnnotations(
                  virtual
                      .annotations()
                      .methodParametersWithFakeThisArguments(appView.dexItemFactory()))
              .setParameterAnnotationsList(
                  virtual.getParameterAnnotations().withFakeThisParameter())
              // TODO(b/183998768): Once R8 desugars in the enqueuer this should set an invalid
              //  code to ensure it is never used before desugared and installed.
              .setCode(
                  syntheticMethod ->
                      appView.enableWholeProgramOptimizations()
                          ? virtual
                              .getCode()
                              .getCodeAsInlining(syntheticMethod, method.getReference())
                          : InvalidCode.getInstance());
        },
        ignored -> {});
  }

  ProgramMethod ensurePrivateAsMethodOfProgramCompanionClassStub(ProgramMethod method) {
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
              // TODO(b/183998768): Should this not also be updating with a fake 'this'
              .setParameterAnnotationsList(definition.getParameterAnnotations())
              // TODO(b/183998768): Once R8 desugars in the enqueuer this should set an invalid
              //  code to ensure it is never used before desugared and installed.
              .setCode(
                  syntheticMethod ->
                      appView.enableWholeProgramOptimizations()
                          ? definition
                              .getCode()
                              .getCodeAsInlining(syntheticMethod, method.getReference())
                          : InvalidCode.getInstance());
        },
        ignored -> {});
  }

  // Represent a static interface method as a method of companion class.
  private DexMethod staticAsMethodOfCompanionClass(DexClassAndMethod method) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType companionClassType = getCompanionClassType(method.getHolderType(), dexItemFactory);
    DexMethod rewritten = method.getReference().withHolder(companionClassType, dexItemFactory);
    return rewritten;
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
            SyntheticKind.COMPANION_CLASS,
            context,
            appView,
            classBuilder -> {},
            ignored -> {},
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(DexEncodedMethod::buildEmptyThrowingCfCode));
  }

  ProgramMethod ensureStaticAsMethodOfProgramCompanionClassStub(
      ProgramMethod method, Consumer<ProgramMethod> companionClinitConsumer) {
    if (!method.getDefinition().isClassInitializer() && method.getHolder().hasClassInitializer()) {
      ensureCompanionClassInitializesInterface(method.getHolder(), companionClinitConsumer);
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
              // TODO(b/183998768): Once R8 desugars in the enqueuer this should set an invalid
              //  code to ensure it is never used before desugared and installed.
              .setCode(
                  syntheticMethod ->
                      appView.enableWholeProgramOptimizations()
                          ? definition
                              .getCode()
                              .getCodeAsInlining(syntheticMethod, method.getReference())
                          : InvalidCode.getInstance());
        },
        ignored -> {});
  }

  private void ensureCompanionClassInitializesInterface(
      DexProgramClass iface, Consumer<ProgramMethod> companionClinitConsumer) {
    assert hasStaticMethodThatTriggersNonTrivialClassInitializer(iface);
    InterfaceProcessor.ensureCompanionMethod(
        iface,
        appView.dexItemFactory().classConstructorMethodName,
        appView.dexItemFactory().createProto(appView.dexItemFactory().voidType),
        appView,
        methodBuilder -> createCompanionClassInitializer(iface, methodBuilder),
        companionClinitConsumer);
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
    return new DexEncodedField(
        clinitFieldReference,
        FieldAccessFlags.builder().setPackagePrivate().setStatic().setSynthetic().build(),
        FieldTypeSignature.noSignature(),
        DexAnnotationSet.empty(),
        DexValueInt.DEFAULT);
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
                        new CfStackInstruction(Opcode.Pop),
                        new CfReturnVoid()),
                    ImmutableList.of(),
                    ImmutableList.of());
              }
              DexEncodedField clinitField =
                  ensureStaticClinitFieldToTriggerInterfaceInitialization(iface);
              boolean isWide = clinitField.getType().isWideType();
              return new CfCode(
                  method.holder,
                  isWide ? 2 : 1,
                  0,
                  ImmutableList.of(
                      new CfFieldInstruction(
                          Opcodes.GETSTATIC,
                          clinitField.getReference(),
                          clinitField.getReference()),
                      isWide
                          ? new CfStackInstruction(Opcode.Pop2)
                          : new CfStackInstruction(Opcode.Pop),
                      new CfReturnVoid()),
                  ImmutableList.of(),
                  ImmutableList.of());
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
      return appView.rewritePrefix.hasRewrittenType(type, appView)
          || descriptor.endsWith(companionClassNameDescriptorSuffix)
          || isRewrittenEmulatedInterface(type)
          || options.desugaredLibraryConfiguration.getCustomConversions().containsValue(type)
          || appView.getDontWarnConfiguration().matches(type);
    };
  }

  boolean shouldIgnoreFromReports(DexType missing) {
    return shouldIgnoreFromReportsPredicate.test(missing);
  }

  void warnMissingInterface(DexClass classToDesugar, DexClass implementing, DexType missing) {
    // We use contains() on non hashed collection, but we know it's a 8 cases collection.
    // j$ interfaces won't be missing, they are in the desugared library.
    if (shouldIgnoreFromReports(missing)) {
      return;
    }
    appView.options().warningMissingInterfaceForDesugar(classToDesugar, implementing, missing);
  }
}
