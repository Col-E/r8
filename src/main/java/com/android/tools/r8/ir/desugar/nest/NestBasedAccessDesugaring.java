// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LibraryMember;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.desugar.ProgramAdditions;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;

// NestBasedAccessDesugaring contains common code between the two subclasses
// which are specialized for d8 and r8
public class NestBasedAccessDesugaring implements CfInstructionDesugaring {

  // Short names to avoid creating long strings
  public static final String NEST_ACCESS_NAME_PREFIX = "-$$Nest$";
  public static final String NEST_ACCESS_METHOD_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "m";
  public static final String NEST_ACCESS_STATIC_METHOD_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "sm";
  public static final String NEST_ACCESS_FIELD_GET_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "fget";
  public static final String NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sfget";
  public static final String NEST_ACCESS_FIELD_PUT_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "fput";
  public static final String NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sfput";

  protected final AppView<?> appView;
  protected final DexItemFactory dexItemFactory;
  private final Map<DexType, DexClass> syntheticNestConstructorTypes = new ConcurrentHashMap<>();

  NestBasedAccessDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  public static NestBasedAccessDesugaring create(AppView<?> appView) {
    if (appView.options().shouldDesugarNests()) {
      return appView.enableWholeProgramOptimizations()
          ? new NestBasedAccessDesugaring(appView)
          : new D8NestBasedAccessDesugaring(appView);
    }
    return null;
  }

  void forEachNest(Consumer<Nest> consumer) {
    forEachNest(consumer, emptyConsumer());
  }

  void forEachNest(Consumer<Nest> consumer, Consumer<DexClass> missingHostConsumer) {
    Set<DexType> seenNestHosts = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (!clazz.isInANest() || !seenNestHosts.add(clazz.getNestHost())) {
        continue;
      }

      Nest nest = Nest.create(appView, clazz, missingHostConsumer);
      if (nest != null) {
        consumer.accept(nest);
      }
    }
  }

  static void forEachNest(
      Consumer<Nest> consumer, Consumer<DexClass> missingHostConsumer, AppView<?> appView) {
    Set<DexType> seenNestHosts = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (!clazz.isInANest() || !seenNestHosts.add(clazz.getNestHost())) {
        continue;
      }

      Nest nest = Nest.create(appView, clazz, missingHostConsumer);
      if (nest != null) {
        consumer.accept(nest);
      }
    }
  }

  private static class BridgeAndTarget<T extends DexClassAndMember<?, ?>> {
    private final DexMethod bridge;
    private final T target;

    @SuppressWarnings("ReferenceEquality")
    public BridgeAndTarget(DexMethod bridge, T target) {
      this.bridge = bridge;
      this.target = target;
      assert bridge.holder == target.getHolderType();
    }

    public DexMethod getBridge() {
      return bridge;
    }

    public T getTarget() {
      return target;
    }

    public boolean shouldAddBridge() {
      return target.isProgramMember() && target.getHolder().lookupDirectMethod(bridge) == null;
    }
  }

  @Override
  public void prepare(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramAdditions programAdditions) {
    method
        .getDefinition()
        .getCode()
        .asCfCode()
        .getInstructions()
        .forEach(
            instruction -> {
              if (instruction.isFieldInstruction()) {
                DexField field = instruction.asFieldInstruction().getField();
                if (needsDesugaring(field, method)) {
                  prepareDesugarFieldInstruction(
                      field,
                      instruction.asFieldInstruction().isFieldGet(),
                      method,
                      eventConsumer,
                      programAdditions);
                }
              } else if (instruction.isInvoke()) {
                DexMethod invokedMethod = instruction.asInvoke().getMethod();
                if (needsDesugaring(invokedMethod, method)) {
                  prepareDesugarMethodInstruction(
                      invokedMethod, method, eventConsumer, programAdditions);
                }
              } else if (instruction.isInvokeDynamic()) {
                // Starting from Java 17, lambda can use nest based access. We need to generate
                // bridges for the targeted lambda method.
                CfInvokeDynamic cfInvokeDynamic = instruction.asInvokeDynamic();
                LambdaDescriptor lambdaDescriptor =
                    LambdaDescriptor.tryInfer(
                        cfInvokeDynamic.getCallSite(),
                        appView,
                        appView.appInfoForDesugaring(),
                        method);
                if (lambdaDescriptor != null) {
                  DexMember<?, ?> member = lambdaDescriptor.implHandle.member;
                  if (needsDesugaring(member, method)) {
                    assert member.isDexMethod();
                    prepareDesugarMethodInstruction(
                        member.asDexMethod(), method, eventConsumer, programAdditions);
                  }
                }
              }
            });
  }

  private void prepareDesugarFieldInstruction(
      DexField field,
      boolean isGet,
      ProgramMethod context,
      NestBasedAccessDesugaringEventConsumer eventConsumer,
      ProgramAdditions programAdditions) {
    BridgeAndTarget<DexClassAndField> bridgeAndTarget =
        bridgeAndTargetForDesugaring(field, isGet, context);
    if (bridgeAndTarget == null || !bridgeAndTarget.shouldAddBridge()) {
      return;
    }

    ProgramField targetField = bridgeAndTarget.getTarget().asProgramField();
    ProgramMethod bridgeMethod =
        programAdditions.ensureMethod(
            bridgeAndTarget.getBridge(),
            () ->
                AccessBridgeFactory.createFieldAccessorBridge(
                    bridgeAndTarget.getBridge(), targetField, isGet));
    if (isGet) {
      eventConsumer.acceptNestFieldGetBridge(targetField, bridgeMethod, context);
    } else {
      eventConsumer.acceptNestFieldPutBridge(targetField, bridgeMethod, context);
    }
  }

  private void prepareDesugarMethodInstruction(
      DexMethod method,
      ProgramMethod context,
      NestBasedAccessDesugaringEventConsumer eventConsumer,
      ProgramAdditions programAdditions) {
    BridgeAndTarget<DexClassAndMethod> bridgeAndTarget =
        bridgeAndTargetForDesugaring(method, context, this::ensureConstructorArgumentClass);
    if (bridgeAndTarget == null || !bridgeAndTarget.shouldAddBridge()) {
      return;
    }
    ProgramMethod targetMethod = bridgeAndTarget.getTarget().asProgramMethod();
    ProgramMethod bridgeMethod =
        programAdditions.ensureMethod(
            bridgeAndTarget.getBridge(),
            () ->
                targetMethod.getDefinition().isInstanceInitializer()
                    ? AccessBridgeFactory.createInitializerAccessorBridge(
                        bridgeAndTarget.getBridge(), targetMethod, dexItemFactory)
                    : AccessBridgeFactory.createMethodAccessorBridge(
                        bridgeAndTarget.getBridge(), targetMethod, dexItemFactory));
    if (targetMethod.getDefinition().isInstanceInitializer()) {
      DexProgramClass argumentClass = getConstructorArgumentClass(targetMethod).asProgramClass();
      eventConsumer.acceptNestConstructorBridge(targetMethod, bridgeMethod, argumentClass, context);
    } else {
      eventConsumer.acceptNestMethodBridge(targetMethod, bridgeMethod, context);
    }
  }

  private BridgeAndTarget<DexClassAndMethod> bridgeAndTargetForDesugaring(
      DexMethod method,
      ProgramMethod context,
      Function<DexClassAndMethod, DexClass> constructorArgumentClassProvider) {
    if (!method.getHolderType().isClassType()) {
      return null;
    }
    // Since we only need to desugar accesses to private methods, and all accesses to private
    // methods must be accessing the private method directly on its holder, we can lookup the
    // method on the holder instead of resolving the method.
    DexClass holder = appView.definitionForHolder(method, context);
    DexClassAndMethod target = method.lookupMemberOnClass(holder);
    if (target == null || !needsDesugaring(target, context)) {
      return null;
    }
    DexMethod bridgeReference;
    if (target.getDefinition().isInstanceInitializer()) {
      DexClass constructorArgumentClass = constructorArgumentClassProvider.apply(target);
      bridgeReference = getConstructorBridgeReference(target, constructorArgumentClass);
    } else {
      bridgeReference = getMethodBridgeReference(target);
    }
    return new BridgeAndTarget<>(bridgeReference, target);
  }

  private BridgeAndTarget<DexClassAndField> bridgeAndTargetForDesugaring(
      DexField field, boolean isGet, ProgramMethod context) {
    // Since we only need to desugar accesses to private fields, and all accesses to private
    // fields must be accessing the private field directly on its holder, we can lookup the
    // field on the holder instead of resolving the field.
    DexClass holder = appView.definitionForHolder(field, context);
    DexClassAndField target = field.lookupMemberOnClass(holder);
    if (target == null || !needsDesugaring(target, context)) {
      return null;
    }
    return new BridgeAndTarget<>(getFieldAccessBridgeReference(target, isGet), target);
  }

  public boolean needsDesugaring(ProgramMethod method) {
    if (!method.getHolder().isInANest() || !method.getDefinition().hasCode()) {
      return false;
    }

    Code code = method.getDefinition().getCode();
    if (code.isDexCode()) {
      return false;
    }

    if (!code.isCfCode()) {
      throw new Unreachable("Unexpected attempt to determine if non-CF code needs desugaring");
    }

    return Iterables.any(
        code.asCfCode().getInstructions(),
        instruction -> compute(instruction, method).needsDesugaring());
  }

  public boolean needsDesugaring(DexMember<?, ?> memberReference, ProgramMethod context) {
    if (!context.getHolder().isInANest() || !memberReference.getHolderType().isClassType()) {
      return false;
    }
    DexClass holder = appView.definitionForHolder(memberReference, context);
    DexClassAndMember<?, ?> member = memberReference.lookupMemberOnClass(holder);
    return member != null && needsDesugaring(member, context);
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean needsDesugaring(DexClassAndMember<?, ?> member, DexClassAndMethod context) {
    return member.getAccessFlags().isPrivate()
        && member.getHolderType() != context.getHolderType()
        && member.getHolder().isInANest()
        && member.getHolder().getNestHost() == context.getHolder().getNestHost();
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isFieldInstruction()) {
      if (needsDesugaring(instruction.asFieldInstruction().getField(), context)) {
        return desugarFieldInstruction(instruction.asFieldInstruction());
      } else {
        return DesugarDescription.nothing();
      }
    }
    if (instruction.isInvoke()) {
      if (needsDesugaring(instruction.asInvoke().getMethod(), context)) {
        return desugarInvokeInstruction(instruction.asInvoke());
      } else {
        return DesugarDescription.nothing();
      }
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription desugarFieldInstruction(CfFieldInstruction instruction) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> {
              BridgeAndTarget<DexClassAndField> bridgeAndTarget =
                  bridgeAndTargetForDesugaring(
                      instruction.getField(), instruction.isFieldGet(), context);
              assert bridgeAndTarget != null;
              // All bridges for program fields must have been added through the prepare step.
              assert !bridgeAndTarget.getTarget().isProgramField()
                  || bridgeAndTarget
                          .getTarget()
                          .getHolder()
                          .lookupDirectMethod(bridgeAndTarget.getBridge())
                      != null;
              return ImmutableList.of(
                  new CfInvoke(
                      Opcodes.INVOKESTATIC,
                      bridgeAndTarget.getBridge(),
                      bridgeAndTarget.getTarget().getHolder().isInterface()));
            })
        .build();
  }

  private DesugarDescription desugarInvokeInstruction(CfInvoke invoke) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> {
              DexMethod invokedMethod = invoke.getMethod();
              BridgeAndTarget<DexClassAndMethod> bridgeAndTarget =
                  bridgeAndTargetForDesugaring(
                      invokedMethod, context, this::getConstructorArgumentClass);
              assert bridgeAndTarget != null;
              // All bridges for program methods must have been added through the prepare step.
              assert !bridgeAndTarget.getTarget().isProgramMethod()
                  || bridgeAndTarget
                          .getTarget()
                          .getHolder()
                          .lookupDirectMethod(bridgeAndTarget.getBridge())
                      != null;
              if (bridgeAndTarget.getTarget().getDefinition().isInstanceInitializer()) {
                assert !invoke.isInterface();
                // Ensure room on the stack for the extra null argument.
                localStackAllocator.allocateLocalStack(1);
                return ImmutableList.of(
                    new CfConstNull(),
                    new CfInvoke(Opcodes.INVOKESPECIAL, bridgeAndTarget.getBridge(), false));
              }

              return ImmutableList.of(
                  new CfInvoke(
                      Opcodes.INVOKESTATIC, bridgeAndTarget.getBridge(), invoke.isInterface()));
            })
        .build();
  }

  RuntimeException reportIncompleteNest(LibraryMember<?, ?> member) {
    Nest nest = Nest.create(appView, member.getHolder());
    assert nest != null : "Should be a compilation error if missing nest host on library class.";
    throw appView.options().errorMissingNestMember(nest);
  }

  DexMethod getFieldAccessBridgeReference(DexClassAndField field, boolean isGet) {
    int bridgeParameterCount =
        BooleanUtils.intValue(!field.getAccessFlags().isStatic()) + BooleanUtils.intValue(!isGet);
    DexType[] parameters = new DexType[bridgeParameterCount];
    if (!isGet) {
      parameters[parameters.length - 1] = field.getType();
    }
    if (!field.getAccessFlags().isStatic()) {
      parameters[0] = field.getHolderType();
    }
    DexType returnType = isGet ? field.getType() : dexItemFactory.voidType;
    DexProto proto = dexItemFactory.createProto(returnType, parameters);
    return dexItemFactory.createMethod(
        field.getHolderType(), proto, getFieldAccessBridgeName(field, isGet));
  }

  private DexString getFieldAccessBridgeName(DexClassAndField field, boolean isGet) {
    String prefix;
    if (isGet && !field.getAccessFlags().isStatic()) {
      prefix = NEST_ACCESS_FIELD_GET_NAME_PREFIX;
    } else if (isGet) {
      prefix = NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX;
    } else if (!field.getAccessFlags().isStatic()) {
      prefix = NEST_ACCESS_FIELD_PUT_NAME_PREFIX;
    } else {
      prefix = NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX;
    }
    return dexItemFactory.createString(prefix + field.getName().toString());
  }

  private DexClass getConstructorArgumentClass(DexClassAndMethod constructor) {
    return syntheticNestConstructorTypes.get(constructor.getHolderType());
  }

  DexClass ensureConstructorArgumentClass(DexClassAndMethod constructor) {
    assert constructor.getDefinition().isInstanceInitializer();
    return syntheticNestConstructorTypes.computeIfAbsent(
        constructor.getHolderType(),
        holder -> {
          if (constructor.isProgramMethod()) {
            return appView
                .getSyntheticItems()
                .createFixedClass(
                    kinds -> kinds.INIT_TYPE_ARGUMENT,
                    constructor.asProgramMethod().getHolder(),
                    appView,
                    builder -> {});
          } else {
            assert constructor.isClasspathMethod();
            return appView
                .getSyntheticItems()
                .ensureFixedClasspathClass(
                    kinds -> kinds.INIT_TYPE_ARGUMENT,
                    constructor.asClasspathMethod().getHolder(),
                    appView,
                    ignored -> {},
                    ignored -> {});
          }
        });
  }

  DexMethod getConstructorBridgeReference(
      DexClassAndMethod method, DexClass constructorArgumentClass) {
    assert method.getDefinition().isInstanceInitializer();
    DexProto newProto =
        dexItemFactory.appendTypeToProto(method.getProto(), constructorArgumentClass.getType());
    return method.getReference().withProto(newProto, dexItemFactory);
  }

  DexMethod getMethodBridgeReference(DexClassAndMethod method) {
    assert !method.getDefinition().isInstanceInitializer();
    DexProto proto =
        method.getAccessFlags().isStatic()
            ? method.getProto()
            : dexItemFactory.prependHolderToProto(method.getReference());
    return dexItemFactory.createMethod(method.getHolderType(), proto, getMethodBridgeName(method));
  }

  private DexString getMethodBridgeName(DexClassAndMethod method) {
    String prefix =
        method.getAccessFlags().isStatic()
            ? NEST_ACCESS_STATIC_METHOD_NAME_PREFIX
            : NEST_ACCESS_METHOD_NAME_PREFIX;
    return dexItemFactory.createString(prefix + method.getName().toString());
  }
}
