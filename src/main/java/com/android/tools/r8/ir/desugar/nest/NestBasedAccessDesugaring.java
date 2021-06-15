// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
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
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;

// NestBasedAccessDesugaring contains common code between the two subclasses
// which are specialized for d8 and r8
public class NestBasedAccessDesugaring implements CfInstructionDesugaring {

  // Short names to avoid creating long strings
  public static final String NEST_ACCESS_NAME_PREFIX = "-$$Nest$";
  private static final String NEST_ACCESS_METHOD_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "m";
  private static final String NEST_ACCESS_STATIC_METHOD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sm";
  private static final String NEST_ACCESS_FIELD_GET_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "fget";
  private static final String NEST_ACCESS_STATIC_GET_FIELD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sfget";
  private static final String NEST_ACCESS_FIELD_PUT_NAME_PREFIX = NEST_ACCESS_NAME_PREFIX + "fput";
  private static final String NEST_ACCESS_STATIC_PUT_FIELD_NAME_PREFIX =
      NEST_ACCESS_NAME_PREFIX + "sfput";

  protected final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final Map<DexType, DexType> syntheticNestConstructorTypes = new ConcurrentHashMap<>();

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
        code.asCfCode().getInstructions(), instruction -> needsDesugaring(instruction, method));
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isFieldInstruction()) {
      return needsDesugaring(instruction.asFieldInstruction().getField(), context);
    }
    if (instruction.isInvoke()) {
      return needsDesugaring(instruction.asInvoke().getMethod(), context);
    }
    return false;
  }

  public boolean needsDesugaring(DexMember<?, ?> memberReference, ProgramMethod context) {
    if (!context.getHolder().isInANest() || !memberReference.getHolderType().isClassType()) {
      return false;
    }
    DexClass holder = appView.definitionForHolder(memberReference, context);
    DexClassAndMember<?, ?> member = memberReference.lookupMemberOnClass(holder);
    return member != null && needsDesugaring(member, context);
  }

  public boolean needsDesugaring(DexClassAndMember<?, ?> member, DexClassAndMethod context) {
    return member.getAccessFlags().isPrivate()
        && member.getHolderType() != context.getHolderType()
        && member.getHolder().isInANest()
        && member.getHolder().getNestHost() == context.getHolder().getNestHost();
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DexItemFactory dexItemFactory) {
    if (instruction.isFieldInstruction()) {
      return desugarFieldInstruction(instruction.asFieldInstruction(), context, eventConsumer);
    }
    if (instruction.isInvoke()) {
      return desugarInvokeInstruction(
          instruction.asInvoke(), localStackAllocator, context, eventConsumer);
    }
    return null;
  }

  private List<CfInstruction> desugarFieldInstruction(
      CfFieldInstruction instruction,
      ProgramMethod context,
      NestBasedAccessDesugaringEventConsumer eventConsumer) {
    // Since we only need to desugar accesses to private fields, and all accesses to private
    // fields must be accessing the private field directly on its holder, we can lookup the
    // field on the holder instead of resolving the field.
    DexClass holder = appView.definitionForHolder(instruction.getField(), context);
    DexClassAndField field = instruction.getField().lookupMemberOnClass(holder);
    if (field == null || !needsDesugaring(field, context)) {
      return null;
    }

    DexMethod bridge = ensureFieldAccessBridge(field, instruction.isFieldGet(), eventConsumer);
    return ImmutableList.of(
        new CfInvoke(Opcodes.INVOKESTATIC, bridge, field.getHolder().isInterface()));
  }

  private List<CfInstruction> desugarInvokeInstruction(
      CfInvoke invoke,
      LocalStackAllocator localStackAllocator,
      ProgramMethod context,
      NestBasedAccessDesugaringEventConsumer eventConsumer) {
    DexMethod invokedMethod = invoke.getMethod();
    if (!invokedMethod.getHolderType().isClassType()) {
      return null;
    }

    // Since we only need to desugar accesses to private methods, and all accesses to private
    // methods must be accessing the private method directly on its holder, we can lookup the
    // method on the holder instead of resolving the method.
    DexClass holder = appView.definitionForHolder(invokedMethod, context);
    DexClassAndMethod target = invokedMethod.lookupMemberOnClass(holder);
    if (target == null || !needsDesugaring(target, context)) {
      return null;
    }

    DexMethod bridge = ensureMethodBridge(target, eventConsumer);
    if (target.getDefinition().isInstanceInitializer()) {
      assert !invoke.isInterface();
      // Ensure room on the stack for the extra null argument.
      localStackAllocator.allocateLocalStack(1);
      return ImmutableList.of(
          new CfConstNull(), new CfInvoke(Opcodes.INVOKESPECIAL, bridge, false));
    }

    return ImmutableList.of(new CfInvoke(Opcodes.INVOKESTATIC, bridge, invoke.isInterface()));
  }

  private RuntimeException reportIncompleteNest(LibraryMember<?, ?> member) {
    Nest nest = Nest.create(appView, member.getHolder());
    assert nest != null : "Should be a compilation error if missing nest host on library class.";
    throw appView.options().errorMissingNestMember(nest);
  }

  DexMethod ensureFieldAccessBridge(
      DexClassAndField field, boolean isGet, NestBasedAccessDesugaringEventConsumer eventConsumer) {
    if (field.isProgramField()) {
      return ensureFieldAccessBridge(field.asProgramField(), isGet, eventConsumer);
    }
    if (field.isClasspathField()) {
      return getFieldAccessBridgeReference(field, isGet);
    }
    assert field.isLibraryField();
    throw reportIncompleteNest(field.asLibraryField());
  }

  private DexMethod ensureFieldAccessBridge(
      ProgramField field, boolean isGet, NestBasedAccessDesugaringEventConsumer eventConsumer) {
    DexMethod bridgeReference = getFieldAccessBridgeReference(field, isGet);
    synchronized (field.getHolder().getMethodCollection()) {
      ProgramMethod bridge = field.getHolder().lookupProgramMethod(bridgeReference);
      if (bridge == null) {
        bridge = AccessBridgeFactory.createFieldAccessorBridge(bridgeReference, field, isGet);
        bridge.getHolder().addDirectMethod(bridge.getDefinition());
        if (eventConsumer != null) {
          if (isGet) {
            eventConsumer.acceptNestFieldGetBridge(field, bridge);
          } else {
            eventConsumer.acceptNestFieldPutBridge(field, bridge);
          }
        }
      }
      return bridge.getReference();
    }
  }

  private DexMethod getFieldAccessBridgeReference(DexClassAndField field, boolean isGet) {
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

  DexMethod ensureMethodBridge(
      DexClassAndMethod method, NestBasedAccessDesugaringEventConsumer eventConsumer) {
    if (method.isProgramMethod()) {
      return ensureMethodBridge(method.asProgramMethod(), eventConsumer);
    }
    if (method.isClasspathMethod()) {
      return getMethodBridgeReference(method);
    }
    assert method.isLibraryMethod();
    throw reportIncompleteNest(method.asLibraryMethod());
  }

  private DexMethod ensureMethodBridge(
      ProgramMethod method, NestBasedAccessDesugaringEventConsumer eventConsumer) {
    DexMethod bridgeReference = getMethodBridgeReference(method);
    synchronized (method.getHolder().getMethodCollection()) {
      ProgramMethod bridge = method.getHolder().lookupProgramMethod(bridgeReference);
      if (bridge == null) {
        DexEncodedMethod definition = method.getDefinition();
        bridge =
            definition.isInstanceInitializer()
                ? AccessBridgeFactory.createInitializerAccessorBridge(
                    bridgeReference, method, dexItemFactory)
                : AccessBridgeFactory.createMethodAccessorBridge(
                    bridgeReference, method, dexItemFactory);
        bridge.getHolder().addDirectMethod(bridge.getDefinition());
        if (eventConsumer != null) {
          eventConsumer.acceptNestMethodBridge(method, bridge);
        }
      }
    }
    return bridgeReference;
  }

  private DexMethod getMethodBridgeReference(DexClassAndMethod method) {
    if (method.getDefinition().isInstanceInitializer()) {
      DexType nestConstructorType =
          syntheticNestConstructorTypes.computeIfAbsent(
              method.getHolderType(),
              holder -> {
                if (method.isProgramMethod()) {
                  return appView
                      .getSyntheticItems()
                      .createFixedClass(
                          SyntheticKind.INIT_TYPE_ARGUMENT,
                          method.asProgramMethod().getHolder(),
                          appView,
                          builder -> {})
                      .getType();
                } else {
                  assert method.isClasspathMethod();
                  return appView
                      .getSyntheticItems()
                      .createFixedClasspathClass(
                          SyntheticKind.INIT_TYPE_ARGUMENT,
                          method.asClasspathMethod().getHolder(),
                          dexItemFactory)
                      .getType();
                }
              });
      DexProto newProto = dexItemFactory.appendTypeToProto(method.getProto(), nestConstructorType);
      return method.getReference().withProto(newProto, dexItemFactory);
    }
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
