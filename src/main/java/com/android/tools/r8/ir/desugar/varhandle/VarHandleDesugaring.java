// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.varhandle;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.contexts.CompilationContext.ClassSynthesisDesugaringContext;
import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexApplicationReadFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.utils.BitUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Opcodes;

public class VarHandleDesugaring implements CfInstructionDesugaring, CfClassSynthesizerDesugaring {

  private static final int SYNTHESIZED_METHOD_HANDLES_LOOKUP_CLASS = 1;
  private static final int SYNTHESIZED_VAR_HANDLE_CLASS_FLAG = 2;

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public static VarHandleDesugaring create(AppView<?> appView) {
    return appView.options().shouldDesugarVarHandle() ? new VarHandleDesugaring(appView) : null;
  }

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    VarHandleDesugaringMethods.registerSynthesizedCodeReferences(factory);
  }

  public VarHandleDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void scan(
      ProgramMethod programMethod, CfInstructionDesugaringEventConsumer eventConsumer) {
    if (programMethod.getHolderType() == factory.varHandleType) {
      return;
    }
    CfCode cfCode = programMethod.getDefinition().getCode().asCfCode();
    int synthesizedClasses = 0;
    for (CfInstruction instruction : cfCode.getInstructions()) {
      synthesizedClasses =
          scanInstruction(instruction, eventConsumer, programMethod, synthesizedClasses);
    }
  }

  private int scanInstruction(
      CfInstruction instruction,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      int synthesizedClasses) {
    assert !instruction.isInitClass();
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      if (BitUtils.isBitInMaskUnset(synthesizedClasses, SYNTHESIZED_VAR_HANDLE_CLASS_FLAG)
          && refersToVarHandle(cfInvoke.getMethod(), factory)) {
        ensureVarHandleClass(eventConsumer, context);
        synthesizedClasses |= SYNTHESIZED_VAR_HANDLE_CLASS_FLAG;
      }
      if (BitUtils.isBitInMaskUnset(synthesizedClasses, SYNTHESIZED_METHOD_HANDLES_LOOKUP_CLASS)
          && refersToMethodHandlesLookup(cfInvoke.getMethod(), factory)) {
        ensureMethodHandlesLookupClass(eventConsumer, context);
        synthesizedClasses |= SYNTHESIZED_METHOD_HANDLES_LOOKUP_CLASS;
      }
    }
    return synthesizedClasses;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean refersToVarHandle(DexType type, DexItemFactory factory) {
    if (type == factory.desugarVarHandleType) {
      // All references to java.lang.invoke.VarHandle is rewritten during application writing.
      assert false;
      return true;
    }
    return type == factory.varHandleType;
  }

  private static boolean refersToVarHandle(DexType[] types, DexItemFactory factory) {
    for (DexType type : types) {
      if (refersToVarHandle(type, factory)) {
        return true;
      }
    }
    return false;
  }

  public static boolean refersToVarHandle(DexMethod method, DexItemFactory factory) {
    if (refersToVarHandle(method.holder, factory)) {
      return true;
    }
    return refersToVarHandle(method.proto, factory);
  }

  private static boolean refersToVarHandle(DexProto proto, DexItemFactory factory) {
    if (refersToVarHandle(proto.returnType, factory)) {
      return true;
    }
    return refersToVarHandle(proto.parameters.values, factory);
  }

  public static boolean refersToVarHandle(DexField field, DexItemFactory factory) {
    if (refersToVarHandle(field.holder, factory)) {
      assert false : "The VarHandle class has no fields.";
      return true;
    }
    return refersToVarHandle(field.type, factory);
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean refersToMethodHandlesLookup(DexType type, DexItemFactory factory) {
    if (type == factory.desugarMethodHandlesLookupType) {
      // All references to java.lang.invoke.MethodHandles$Lookup is rewritten during application
      // writing.
      assert false;
      return true;
    }
    return type == factory.methodHandlesLookupType;
  }

  private static boolean refersToMethodHandlesLookup(DexType[] types, DexItemFactory factory) {
    for (DexType type : types) {
      if (refersToMethodHandlesLookup(type, factory)) {
        return true;
      }
    }
    return false;
  }

  public static boolean refersToMethodHandlesLookup(DexMethod method, DexItemFactory factory) {
    if (refersToMethodHandlesLookup(method.holder, factory)) {
      return true;
    }
    return refersToMethodHandlesLookup(method.proto, factory);
  }

  private static boolean refersToMethodHandlesLookup(DexProto proto, DexItemFactory factory) {
    if (refersToMethodHandlesLookup(proto.returnType, factory)) {
      return true;
    }
    return refersToMethodHandlesLookup(proto.parameters.values, factory);
  }

  public static boolean refersToMethodHandlesLookup(DexField field, DexItemFactory factory) {
    if (refersToMethodHandlesLookup(field.holder, factory)) {
      return true;
    }
    return refersToMethodHandlesLookup(field.type, factory);
  }

  @SuppressWarnings({"InconsistentOverloads", "ReferenceEquality"})
  public static void ensureMethodHandlesLookupClass(
      AppView<?> appView,
      VarHandleDesugaringEventConsumer eventConsumer,
      Collection<? extends ProgramDefinition> contexts) {
    assert contexts.stream()
        .allMatch(context -> context.getContextType() != appView.dexItemFactory().lookupType);
    DexProgramClass clazz =
        appView
            .getSyntheticItems()
            .ensureGlobalClass(
                () ->
                    new MissingGlobalSyntheticsConsumerDiagnostic("MethodHandlesLookup desugaring"),
                kinds -> kinds.METHOD_HANDLES_LOOKUP,
                appView.dexItemFactory().lookupType,
                contexts,
                appView,
                builder ->
                    VarHandleDesugaringMethods.generateDesugarMethodHandlesLookupClass(
                        builder, appView.dexItemFactory()),
                eventConsumer::acceptVarHandleDesugaringClass);
    for (ProgramDefinition context : contexts) {
      eventConsumer.acceptVarHandleDesugaringClassContext(clazz, context);
    }
  }

  private void ensureMethodHandlesLookupClass(
      VarHandleDesugaringEventConsumer eventConsumer, ProgramDefinition context) {
    ensureMethodHandlesLookupClass(appView, eventConsumer, ImmutableList.of(context));
  }

  @SuppressWarnings({"InconsistentOverloads", "ReferenceEquality"})
  public static void ensureVarHandleClass(
      AppView<?> appView,
      VarHandleDesugaringEventConsumer eventConsumer,
      Collection<? extends ProgramDefinition> contexts) {
    assert contexts.stream()
        .allMatch(context -> context.getContextType() != appView.dexItemFactory().varHandleType);
    appView
        .getSyntheticItems()
        .ensureGlobalClass(
            () -> new MissingGlobalSyntheticsConsumerDiagnostic("VarHandle desugaring"),
            kinds -> kinds.VAR_HANDLE,
            appView.dexItemFactory().varHandleType,
            contexts,
            appView,
            builder ->
                VarHandleDesugaringMethods.generateDesugarVarHandleClass(
                    builder, appView.dexItemFactory()),
            eventConsumer::acceptVarHandleDesugaringClass,
            clazz -> {
              for (ProgramDefinition context : contexts) {
                eventConsumer.acceptVarHandleDesugaringClassContext(clazz, context);
              }
            });
  }

  @SuppressWarnings("ReferenceEquality")
  private void ensureVarHandleClass(
      VarHandleDesugaringEventConsumer eventConsumer, ProgramDefinition context) {
    if (context.getContextType() != factory.varHandleType) {
      ensureVarHandleClass(appView, eventConsumer, ImmutableList.of(context));
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvoke()) {
      return DesugarDescription.nothing();
    }
    CfInvoke invoke = instruction.asInvoke();
    DexType holder = invoke.getMethod().getHolderType();
    if (holder != factory.methodHandlesType
        && holder != factory.methodHandlesLookupType
        && holder != factory.varHandleType) {
      return DesugarDescription.nothing();
    }

    DexMethod method = invoke.getMethod();
    if (method.getHolderType() == factory.methodHandlesType) {
      if (method.getName().equals(factory.lookupString)
          && method.getReturnType() == factory.lookupType
          && method.getArity() == 0
          && invoke.isInvokeStatic()) {
        return computeMethodHandlesLookup(factory);
      } else if (method.getName().equals(factory.privateLookupInString)
          && method.getReturnType() == factory.lookupType
          && method.getArity() == 2
          && method.getParameter(0) == factory.classType
          && method.getParameter(1) == factory.lookupType
          && invoke.isInvokeStatic()) {
        return computeMethodHandlesPrivateLookupIn(factory);
      } else if (method.getName().equals(factory.createString("arrayElementVarHandle"))
          && method.getReturnType() == factory.varHandleType
          && method.getArity() == 1
          && method.getParameter(0) == factory.classType
          && invoke.isInvokeStatic()) {
        return computeMethodHandlesArrayElementVarHandle(factory);
      } else {
        return DesugarDescription.nothing();
      }
    }

    if (method.getHolderType() == factory.varHandleType) {
      if (!invoke.isInvokeVirtual()) {
        // Right now only <init> should be hit from MethodHandles.Lookup desugaring creating
        // a VarHandle instance.
        assert invoke.isInvokeSpecial();
        return DesugarDescription.nothing();
      }
      assert invoke.isInvokeVirtual();
      DexString name = method.getName();
      int arity = method.getProto().getArity();
      if (name.equals(factory.compareAndSetString)
          || name.equals(factory.weakCompareAndSetString)) {
        assert arity == 3 || arity == 4;
        return computeDesugarSignaturePolymorphicMethod(invoke, arity - 2);
      } else if (name.equals(factory.getString) || name.equals(factory.getVolatileString)) {
        assert arity == 1 || arity == 2;
        return computeDesugarSignaturePolymorphicMethod(invoke, arity);
      } else if (name.equals(factory.setString)
          || name.equals(factory.setVolatileString)
          || name.equals(factory.setReleaseString)) {
        assert arity == 2 || arity == 3;
        return computeDesugarSignaturePolymorphicMethod(invoke, arity - 1);
      } else {
        // TODO(b/247076137): Insert runtime exception - unsupported VarHandle operation.
        return DesugarDescription.nothing();
      }
    }

    return DesugarDescription.nothing();
  }

  public DesugarDescription computeMethodHandlesLookup(DexItemFactory factory) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> {
              ensureMethodHandlesLookupClass(eventConsumer, context);
              localStackAllocator.allocateLocalStack(2);
              return ImmutableList.of(
                  new CfNew(factory.lookupType),
                  new CfStackInstruction(Opcode.Dup),
                  new CfInvoke(
                      Opcodes.INVOKESPECIAL,
                      factory.createMethod(
                          factory.lookupType,
                          factory.createProto(factory.voidType),
                          factory.constructorMethodName),
                      false));
            })
        .build();
  }

  public DesugarDescription computeMethodHandlesPrivateLookupIn(DexItemFactory factory) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> {
              ensureMethodHandlesLookupClass(eventConsumer, context);
              // Rewrite MethodHandles.privateLookupIn(class, lookup) to
              // lookup.toPrivateLookupIn(class).
              return ImmutableList.of(
                  new CfStackInstruction(Opcode.Swap),
                  new CfInvoke(
                      Opcodes.INVOKEVIRTUAL,
                      factory.createMethod(
                          factory.lookupType,
                          factory.createProto(factory.lookupType, factory.classType),
                          factory.createString("toPrivateLookupIn")),
                      false));
            })
        .build();
  }

  public DesugarDescription computeMethodHandlesArrayElementVarHandle(DexItemFactory factory) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> {
              localStackAllocator.allocateLocalStack(2);
              return ImmutableList.of(
                  new CfNew(factory.varHandleType),
                  new CfStackInstruction(Opcode.DupX1),
                  new CfStackInstruction(Opcode.Swap),
                  new CfInvoke(
                      Opcodes.INVOKESPECIAL,
                      factory.createMethod(
                          factory.varHandleType,
                          factory.createProto(factory.voidType, factory.classType),
                          factory.constructorMethodName),
                      false));
            })
        .build();
  }

  public DesugarDescription computeDesugarSignaturePolymorphicMethod(
      CfInvoke invoke, int coordinates) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> {
              ensureVarHandleClass(eventConsumer, context);
              return desugarSignaturePolymorphicMethod(
                  invoke, coordinates, freshLocalProvider, localStackAllocator);
            })
        .build();
  }

  private boolean isPrimitiveThatIsNotBoxed(DexType type) {
    return type.isIntType() || type.isLongType();
  }

  private DexType objectOrPrimitiveReturnType(DexType type) {
    return isPrimitiveThatIsNotBoxed(type) || type.isVoidType() ? type : factory.objectType;
  }

  private DexType objectOrPrimitiveParameterType(DexType type) {
    return isPrimitiveThatIsNotBoxed(type) || type.isVoidType() ? type : factory.objectType;
  }

  @SuppressWarnings("ReferenceEquality")
  private Collection<CfInstruction> desugarSignaturePolymorphicMethod(
      CfInvoke invoke,
      int coordinates,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator) {
    assert invoke.isInvokeVirtual();
    // TODO(b/247076137): Support two coordinates (array element VarHandle).
    assert (coordinates == 1 || coordinates == 2)
        && invoke.getMethod().getProto().getArity() >= coordinates;
    // Only support zero, one and two arguments after coordinates.
    int nonCoordinateArguments = invoke.getMethod().getProto().getArity() - coordinates;
    assert nonCoordinateArguments <= 2;

    DexProto proto = invoke.getMethod().getProto();
    DexType ct1Type = invoke.getMethod().getProto().getParameter(0);
    if (!ct1Type.isClassType() && !ct1Type.isArrayType()) {
      return null;
    }
    DexType ct1ElementType = null;
    if (ct1Type.isArrayType()) {
      ct1ElementType = ct1Type.toArrayElementType(factory);
      if (ct1ElementType != factory.intType
          && ct1ElementType != factory.longType
          && !ct1ElementType.isReferenceType()) {
        return null;
      }
      DexType ct2Type = invoke.getMethod().getProto().getParameter(1);
      if (ct2Type != factory.intType) {
        return null;
      }
    }

    // Convert the arguments by boxing except for primitive int and long.
    ImmutableList.Builder<CfInstruction> builder = ImmutableList.builder();
    List<DexType> newParameters = new ArrayList<>(proto.parameters.size());
    DexType argumentType = null;
    boolean hasWideArgument = false;
    if (nonCoordinateArguments > 0) {
      argumentType = objectOrPrimitiveParameterType(proto.parameters.get(coordinates));
      for (int i = coordinates; i < proto.parameters.size(); i++) {
        hasWideArgument = hasWideArgument || proto.parameters.get(i).isWideType();
        DexType type = objectOrPrimitiveParameterType(proto.parameters.get(i));
        if (type != argumentType) {
          argumentType = factory.objectType;
        }
      }
      assert isPrimitiveThatIsNotBoxed(argumentType) || argumentType == factory.objectType;
    }
    DexString name = invoke.getMethod().getName();
    boolean popReturnValue = false;
    DexType returnType =
        factory.polymorphicMethods.varHandleCompareAndSetMethodNames.contains(name)
            ? proto.returnType
            : objectOrPrimitiveReturnType(proto.returnType);
    if (returnType.isVoidType()
        && factory.polymorphicMethods.varHandleMethodsWithPolymorphicReturnType.contains(name)) {
      returnType = factory.objectType;
      popReturnValue = true;
    }

    if (coordinates == 1) {
      newParameters.add(factory.objectType);
    } else {
      assert coordinates == 2;
      // For array VarHandle only use the method with primitive arguments if all relevant parts of
      // the signature has the same primitive type.
      assert ct1ElementType != null;
      boolean usePrimitiveArray =
          ct1ElementType.isPrimitiveType()
              && (argumentType == null || argumentType == ct1ElementType)
              && (factory.polymorphicMethods.varHandleCompareAndSetMethodNames.contains(name)
                  || returnType.isVoidType()
                  || returnType == ct1ElementType);
      newParameters.add(usePrimitiveArray ? ct1Type : factory.objectType);
      newParameters.add(factory.intType);
      if (!usePrimitiveArray) {
        if (argumentType != null) {
          argumentType = factory.objectType;
        }
        if (!factory.polymorphicMethods.varHandleCompareAndSetMethodNames.contains(name)
            && !returnType.isVoidType()) {
          returnType = factory.objectType;
        }
      }
    }

    // Ensure all arguments are boxed if required.
    for (int i = coordinates; i < proto.parameters.size(); i++) {
      if (argumentType.isPrimitiveType()) {
        newParameters.add(argumentType);
      } else {
        boolean lastArgument = i == proto.parameters.size() - 1;
        // Pass all boxed objects as Object.
        newParameters.add(factory.objectType);
        if (!proto.parameters.get(i).isPrimitiveType()) {
          continue;
        }
        int local = -1;
        // For boxing of the second to last argument (we only have one or two) bring it to TOS.
        if (!lastArgument) {
          if (hasWideArgument) {
            local = freshLocalProvider.getFreshLocal(2);
            localStackAllocator.allocateLocalStack(1);
            builder.add(new CfStore(ValueType.fromDexType(proto.parameters.get(i + 1)), local));
          } else {
            builder.add(new CfStackInstruction(Opcode.Swap));
          }
        }
        localStackAllocator.allocateLocalStack(1);
        builder.add(
            new CfInvoke(
                Opcodes.INVOKESTATIC,
                factory.getBoxPrimitiveMethod(proto.parameters.get(i)),
                false));
        // When boxing of the second to last argument (we only have one or two) bring last
        // argument back to TOS.
        if (!lastArgument) {
          if (hasWideArgument) {
            assert local != -1;
            builder.add(new CfLoad(ValueType.fromDexType(proto.parameters.get(i + 1)), local));
          } else {
            builder.add(new CfStackInstruction(Opcode.Swap));
          }
        }
      }
    }
    assert newParameters.size() == proto.parameters.size();
    if (proto.returnType != returnType && proto.returnType != factory.voidType) {
      if (proto.returnType.isPrimitiveType()) {
        builder.add(new CfConstClass(factory.getBoxedForPrimitiveType(proto.returnType)));
      } else {
        builder.add(new CfConstClass(proto.returnType));
      }
      newParameters.add(factory.classType);
    }
    DexProto newProto = factory.createProto(returnType, newParameters);
    DexMethod newMethod = factory.createMethod(factory.varHandleType, newProto, name);
    builder.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, newMethod, false));
    if (popReturnValue) {
      localStackAllocator.allocateLocalStack(1);
      builder.add(new CfStackInstruction(Opcode.Pop));
    } else if (proto.returnType.isPrimitiveType() && !newProto.returnType.isPrimitiveType()) {
      assert newProto.returnType == factory.objectType;
      localStackAllocator.allocateLocalStack(2);
      builder.add(new CfCheckCast(factory.getBoxedForPrimitiveType(proto.returnType)));
      builder.add(
          new CfInvoke(
              Opcodes.INVOKEVIRTUAL, factory.getUnboxPrimitiveMethod(proto.returnType), false));
    } else if (proto.returnType.isClassType()
        && proto.returnType != factory.objectType
        && proto.returnType != factory.voidType) {
      localStackAllocator.allocateLocalStack(1);
      builder.add(new CfCheckCast(proto.returnType));
    }
    return builder.build();
  }

  @Override
  public String uniqueIdentifier() {
    return "$varhandle";
  }

  @Override
  // TODO(b/247076137): Is synthesizeClasses needed? Can DesugarVarHandle be created during
  //  desugaring instead? For R8 creating up-front and replacing the library definition seems to
  //  be the way to go.
  public void synthesizeClasses(
      ClassSynthesisDesugaringContext processingContext,
      CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    DexApplicationReadFlags flags = appView.appInfo().app().getFlags();
    synthesizeClassIfReferenced(
        flags,
        DexApplicationReadFlags::hasReadMethodHandlesLookupReferenceFromProgramClass,
        DexApplicationReadFlags::getMethodHandlesLookupWitnesses,
        classes -> ensureMethodHandlesLookupClass(appView, eventConsumer, classes));
    synthesizeClassIfReferenced(
        flags,
        DexApplicationReadFlags::hasReadVarHandleReferenceFromProgramClass,
        DexApplicationReadFlags::getVarHandleWitnesses,
        classes -> ensureVarHandleClass(appView, eventConsumer, classes));
  }

  private void synthesizeClassIfReferenced(
      DexApplicationReadFlags flags,
      Predicate<DexApplicationReadFlags> hasReadReferenceFromProgramClass,
      Function<DexApplicationReadFlags, Set<DexType>> getWitnesses,
      Consumer<? super List<DexProgramClass>> consumeProgramWitnesses) {
    if (hasReadReferenceFromProgramClass.test(flags)) {
      List<DexProgramClass> classes = new ArrayList<>();
      for (DexType witness : getWitnesses.apply(flags)) {
        DexClass dexClass = appView.contextIndependentDefinitionFor(witness);
        assert dexClass != null;
        assert dexClass.isProgramClass();
        classes.add(dexClass.asProgramClass());
      }
      consumeProgramWitnesses.accept(classes);
    }
  }
}
