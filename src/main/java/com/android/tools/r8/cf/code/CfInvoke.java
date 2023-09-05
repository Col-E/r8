// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Arrays;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfInvoke extends CfInstruction {

  private final DexMethod method;
  private final int opcode;
  private final boolean itf;

  private static void specify(StructuralSpecification<CfInvoke, ?> spec) {
    spec.withBool(CfInvoke::isInterface).withItem(CfInvoke::getMethod);
  }

  public CfInvoke(int opcode, DexMethod method, boolean itf) {
    assert Opcodes.INVOKEVIRTUAL <= opcode && opcode <= Opcodes.INVOKEINTERFACE;
    assert !(opcode == Opcodes.INVOKEVIRTUAL && itf) : "InvokeVirtual on interface type";
    assert !(opcode == Opcodes.INVOKEINTERFACE && !itf) : "InvokeInterface on class type";
    this.opcode = opcode;
    this.method = method;
    this.itf = itf;
  }

  @Override
  public int getCompareToId() {
    return getOpcode();
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    CfInvoke otherInvoke = other.asInvoke();
    return visitor.visit(this, otherInvoke, CfInvoke::specify);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, CfInvoke::specify);
  }

  public DexMethod getMethod() {
    return method;
  }

  public int getOpcode() {
    return opcode;
  }

  public boolean isInterface() {
    return itf;
  }

  @Override
  public CfInvoke asInvoke() {
    return this;
  }

  @Override
  public boolean isInvoke() {
    return true;
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    InvokeType invokeType = InvokeType.fromCfOpcode(opcode, method, context, appView);
    if (invokeType == InvokeType.POLYMORPHIC) {
      assert dexItemFactory.polymorphicMethods.isPolymorphicInvoke(method);
      // The method is one of java.lang.MethodHandle.invoke/invokeExact.
      // Only the method signature (getProto()) is to be type rewritten.
      DexProto rewrittenProto = rewriter.rewriteProto(method.getProto());
      visitor.visitMethodInsn(
          invokeType.getCfOpcode(),
          DescriptorUtils.descriptorToInternalName(method.holder.toDescriptorString()),
          method.getName().toString(),
          rewrittenProto.toDescriptorString(namingLens),
          itf);
    } else {
      MethodLookupResult lookup =
          graphLens.lookupMethod(method, context.getReference(), invokeType);
      InvokeType rewrittenType = lookup.getType();
      DexMethod rewrittenMethod = lookup.getReference();
      String owner = namingLens.lookupInternalName(rewrittenMethod.holder);
      String name = namingLens.lookupName(rewrittenMethod).toString();
      String desc = rewrittenMethod.proto.toDescriptorString(namingLens);
      visitor.visitMethodInsn(
          rewrittenType.getCfOpcode(), owner, name, desc, rewrittenType.isInterface() || itf);
    }
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return opcode == Opcodes.INVOKEINTERFACE ? 5 : 3;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        registry.registerInvokeInterface(method);
        break;
      case Opcodes.INVOKESPECIAL:
        registry.registerInvokeSpecial(method, itf);
        break;
      case Opcodes.INVOKESTATIC:
        registry.registerInvokeStatic(method, itf);
        break;
      case Opcodes.INVOKEVIRTUAL:
        registry.registerInvokeVirtual(method);
        break;
      default:
        throw new Unreachable("Unknown CfInvoke opcode " + opcode);
    }
  }

  public boolean isInvokeConstructor(DexItemFactory dexItemFactory) {
    return getMethod().isInstanceInitializer(dexItemFactory);
  }

  // We should avoid interpreting a CF invoke using DEX semantics.
  @Deprecated
  @SuppressWarnings("ReferenceEquality")
  public boolean isInvokeSuper(DexType clazz) {
    return opcode == Opcodes.INVOKESPECIAL
        && method.holder != clazz
        && !method.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME);
  }

  @Override
  public boolean isInvokeSpecial() {
    return opcode == Opcodes.INVOKESPECIAL;
  }

  @Override
  public boolean isInvokeStatic() {
    return opcode == Opcodes.INVOKESTATIC;
  }

  @Override
  public boolean isInvokeVirtual() {
    return opcode == Opcodes.INVOKEVIRTUAL;
  }

  @Override
  public boolean isInvokeInterface() {
    return opcode == Opcodes.INVOKEINTERFACE;
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    InvokeType type;
    DexMethod canonicalMethod;
    DexProto callSiteProto = null;
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        {
          canonicalMethod = method;
          type = InvokeType.INTERFACE;
          break;
        }
      case Opcodes.INVOKEVIRTUAL:
        {
          canonicalMethod = builder.dexItemFactory().polymorphicMethods.canonicalize(method);
          if (canonicalMethod == null) {
            type = InvokeType.VIRTUAL;
            canonicalMethod = method;
          } else {
            if (builder.appView.options().shouldDesugarVarHandle()) {
              type = InvokeType.VIRTUAL;
              canonicalMethod = method;
            } else {
              type = InvokeType.POLYMORPHIC;
              callSiteProto = method.proto;
            }
          }
          break;
        }
      case Opcodes.INVOKESPECIAL:
        {
          // Per https://source.android.com/devices/tech/dalvik/dalvik-bytecode, for Dex files
          // version >= 037, if the method refers to an interface method, invoke-super is used to
          // invoke the most specific, non-overridden version of that method.
          // In https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.3, it is
          // a compile-time error in the case that "If TypeName denotes an interface, let T be the
          // type declaration immediately enclosing the method invocation. A compile-time error
          // occurs if there exists a method, distinct from the compile-time declaration, that
          // overrides (§9.4.1) the compile-time declaration from a direct superclass or
          // direct superinterface of T."
          // Using invoke-super should therefore observe the correct semantics since we cannot
          // target less specific targets (up in the hierarchy).
          AppView<?> appView = builder.appView;
          ProgramMethod context = builder.getProgramMethod();
          canonicalMethod = method;
          type = InvokeType.fromInvokeSpecial(method, context, appView, builder.getCodeLens());
          break;
        }
      case Opcodes.INVOKESTATIC:
        {
          canonicalMethod = method;
          type = InvokeType.STATIC;
          break;
        }
      default:
        throw new Unreachable("unknown CfInvoke opcode " + opcode);
    }

    int parameterCount = method.getParameters().size();
    if (type != InvokeType.STATIC) {
      parameterCount += 1;
    }
    ValueType[] types = new ValueType[parameterCount];
    Integer[] registers = new Integer[parameterCount];
    for (int i = parameterCount - 1; i >= 0; i--) {
      Slot slot = state.pop();
      types[i] = slot.type;
      registers[i] = slot.register;
    }
    builder.addInvoke(
        type, canonicalMethod, callSiteProto, Arrays.asList(types), Arrays.asList(registers), itf);
    if (!method.getReturnType().isVoidType()) {
      builder.addMoveResult(state.push(method.getReturnType()).register);
    }
    assert type
        == InvokeType.fromCfOpcode(
            opcode, method, builder.getProgramMethod(), builder.appView, builder.getCodeLens());
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    GraphLens graphLens = inliningConstraints.getGraphLens();
    AppView<?> appView = inliningConstraints.getAppView();
    DexMethod target = method;
    // Find the DEX invocation type.
    InvokeType type;
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        // Could have changed to an invoke-virtual instruction due to vertical class merging
        // (if an interface is merged into a class).
        type =
            graphLens.lookupMethod(target, context.getReference(), InvokeType.INTERFACE).getType();
        assert type == InvokeType.INTERFACE || type == InvokeType.VIRTUAL;
        break;

      case Opcodes.INVOKESPECIAL:
        {
          InvokeType actualInvokeType =
              computeInvokeTypeForInvokeSpecial(appView, method, context, code.getOriginalHolder());
          type = graphLens.lookupMethod(target, context.getReference(), actualInvokeType).getType();
        }
        break;

      case Opcodes.INVOKESTATIC:
        {
          // Static invokes may have changed as a result of horizontal class merging.
          MethodLookupResult lookup =
              graphLens.lookupMethod(target, context.getReference(), InvokeType.STATIC);
          target = lookup.getReference();
          type = lookup.getType();
        }
        break;

      case Opcodes.INVOKEVIRTUAL:
        {
          type = InvokeType.VIRTUAL;
          // Instructions that target a private method in the same class translates to
          // invoke-direct.
          if (target.holder == context.getHolderType()) {
            DexClass clazz = appView.definitionFor(target.holder);
            if (clazz != null && clazz.lookupDirectMethod(target) != null) {
              type = InvokeType.DIRECT;
            }
          }

          // Virtual invokes may have changed to interface invokes as a result of member rebinding.
          MethodLookupResult lookup = graphLens.lookupMethod(target, context.getReference(), type);
          target = lookup.getReference();
          type = lookup.getType();
        }
        break;

      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }

    return inliningConstraints.forInvoke(target, type, context);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., objectref, [arg1, [arg2 ...]] →
    // ... [ returnType ]
    // OR, for static method calls:
    // ..., [arg1, [arg2 ...]] →
    // ...
    frame = frame.popInitialized(appView, config, method.getParameters().getBacking());
    if (opcode != Opcodes.INVOKESTATIC) {
      if (method.getHolderType().isArrayType()) {
        frame = frame.popArray(appView);
      } else {
        DexItemFactory dexItemFactory = appView.dexItemFactory();
        frame =
            opcode == Opcodes.INVOKESPECIAL && method.isInstanceInitializer(dexItemFactory)
                ? frame.popAndInitialize(appView, method, config)
                : frame.popInitialized(appView, config, method.getHolderType());
      }
    }
    if (method.getReturnType().isVoidType()) {
      return frame;
    }
    return frame.push(config, method.getReturnType());
  }

  @SuppressWarnings("ReferenceEquality")
  private InvokeType computeInvokeTypeForInvokeSpecial(
      AppView<?> appView, DexMethod method, ProgramMethod context, DexType originalHolder) {
    if (appView.dexItemFactory().isConstructor(method)) {
      return InvokeType.DIRECT;
    }
    if (originalHolder != method.getHolderType()) {
      return InvokeType.SUPER;
    }
    return invokeTypeForInvokeSpecialToNonInitMethodOnHolder(context, appView.graphLens());
  }

  private InvokeType invokeTypeForInvokeSpecialToNonInitMethodOnHolder(
      ProgramMethod context, GraphLens graphLens) {
    MethodLookupResult lookupResult =
        graphLens.lookupMethod(method, context.getReference(), InvokeType.DIRECT);
    DexEncodedMethod definition = context.getHolder().lookupMethod(lookupResult.getReference());
    if (definition == null) {
      return InvokeType.SUPER;
    }

    if (context.getHolder().isInterface()) {
      // On interfaces invoke-special should be mapped to invoke-super if the invoke-special
      // instruction is used to target a default interface method.
      if (definition.belongsToVirtualPool()) {
        return InvokeType.SUPER;
      }
    } else {
      // Due to desugaring of invoke-special instructions that target virtual methods, this invoke
      // should only target a virtual method if the method has been publicized in R8 (in which case
      // the invoke instruction has a pending rewrite to invoke-virtual).
      assert definition.isPrivate() || lookupResult.getType().isVirtual();
    }

    return InvokeType.DIRECT;
  }
}
