// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.structural.CompareToVisitor;
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
    MethodLookupResult lookup =
        graphLens.lookupMethod(method, context.getReference(), getInvokeType(context));
    DexMethod rewrittenMethod = lookup.getReference();
    String owner = namingLens.lookupInternalName(rewrittenMethod.holder);
    String name = namingLens.lookupName(rewrittenMethod).toString();
    String desc = rewrittenMethod.proto.toDescriptorString(namingLens);
    visitor.visitMethodInsn(lookup.getType().getCfOpcode(), owner, name, desc, itf);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    Type invokeType = getInvokeType(context);
    switch (invokeType) {
      case DIRECT:
        registry.registerInvokeDirect(method);
        break;
      case INTERFACE:
        registry.registerInvokeInterface(method);
        break;
      case STATIC:
        registry.registerInvokeStatic(method, itf);
        break;
      case SUPER:
        registry.registerInvokeSuper(method);
        break;
      case VIRTUAL:
        registry.registerInvokeVirtual(method);
        break;
      default:
        throw new Unreachable("Unexpected invoke type " + invokeType);
    }
  }

  // We should avoid interpreting a CF invoke using DEX semantics.
  @Deprecated
  public Invoke.Type getInvokeType(DexClassAndMethod context) {
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        return Type.INTERFACE;

      case Opcodes.INVOKEVIRTUAL:
        return Type.VIRTUAL;

      case Opcodes.INVOKESPECIAL:
        if (method.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)
            || method.holder == context.getHolderType()) {
          return Type.DIRECT;
        }
        return Type.SUPER;

      case Opcodes.INVOKESTATIC:
        return Type.STATIC;

      default:
        throw new Unreachable("unknown CfInvoke opcode " + opcode);
    }
  }

  public boolean isInvokeConstructor(DexItemFactory dexItemFactory) {
    return getMethod().isInstanceInitializer(dexItemFactory);
  }

  // We should avoid interpreting a CF invoke using DEX semantics.
  @Deprecated
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
    Invoke.Type type;
    DexMethod canonicalMethod;
    DexProto callSiteProto = null;
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        {
          canonicalMethod = method;
          type = Type.INTERFACE;
          break;
        }
      case Opcodes.INVOKEVIRTUAL:
        {
          canonicalMethod =
              builder.appView.dexItemFactory().polymorphicMethods.canonicalize(method);
          if (canonicalMethod == null) {
            type = Type.VIRTUAL;
            canonicalMethod = method;
          } else {
            type = Type.POLYMORPHIC;
            callSiteProto = method.proto;
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
          canonicalMethod = method;
          type =
              computeInvokeTypeForInvokeSpecial(
                  builder.appView, method, code.getOriginalHolder(), code.getOrigin());
          break;
        }
      case Opcodes.INVOKESTATIC:
        {
          canonicalMethod = method;
          type = Type.STATIC;
          break;
        }
      default:
        throw new Unreachable("unknown CfInvoke opcode " + opcode);
    }
    int parameterCount = method.proto.parameters.size();
    if (type != Type.STATIC) {
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
    if (!method.proto.returnType.isVoidType()) {
      builder.addMoveResult(state.push(method.proto.returnType).register);
    }
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    GraphLens graphLens = inliningConstraints.getGraphLens();
    AppView<?> appView = inliningConstraints.getAppView();
    DexMethod target = method;
    // Find the DEX invocation type.
    Type type;
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        // Could have changed to an invoke-virtual instruction due to vertical class merging
        // (if an interface is merged into a class).
        type = graphLens.lookupMethod(target, context.getReference(), Type.INTERFACE).getType();
        assert type == Type.INTERFACE || type == Type.VIRTUAL;
        break;

      case Opcodes.INVOKESPECIAL:
        {
          Type actualInvokeType =
              computeInvokeTypeForInvokeSpecial(
                  appView, method, code.getOriginalHolder(), context.getOrigin());
          type = graphLens.lookupMethod(target, context.getReference(), actualInvokeType).getType();
        }
        break;

      case Opcodes.INVOKESTATIC:
        {
          // Static invokes may have changed as a result of horizontal class merging.
          MethodLookupResult lookup =
              graphLens.lookupMethod(target, context.getReference(), Type.STATIC);
          target = lookup.getReference();
          type = lookup.getType();
        }
        break;

      case Opcodes.INVOKEVIRTUAL:
        {
          type = Type.VIRTUAL;
          // Instructions that target a private method in the same class translates to
          // invoke-direct.
          if (target.holder == context.getHolderType()) {
            DexClass clazz = appView.definitionFor(target.holder);
            if (clazz != null && clazz.lookupDirectMethod(target) != null) {
              type = Type.DIRECT;
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
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // ..., objectref, [arg1, [arg2 ...]] →
    // ... [ returnType ]
    // OR, for static method calls:
    // ..., [arg1, [arg2 ...]] →
    // ...
    frameBuilder.popAndDiscardInitialized(this.method.proto.parameters.values);
    if (opcode == Opcodes.INVOKESPECIAL && method.isInstanceInitializer(factory)) {
      frameBuilder.popAndInitialize(context, method.holder);
    } else if (opcode != Opcodes.INVOKESTATIC) {
      frameBuilder.popInitialized(method.holder);
    }
    if (this.method.proto.returnType != factory.voidType) {
      frameBuilder.push(this.method.proto.returnType);
    }
  }

  private Type computeInvokeTypeForInvokeSpecial(
      AppView<?> appView, DexMethod method, DexType originalHolder, Origin origin) {
    if (appView.dexItemFactory().isConstructor(method)) {
      return Type.DIRECT;
    }
    if (originalHolder == method.holder) {
      return invokeTypeForInvokeSpecialToNonInitMethodOnHolder(appView, origin);
    }
    return Type.SUPER;
  }

  private Type invokeTypeForInvokeSpecialToNonInitMethodOnHolder(
      AppView<?> appView, Origin origin) {
    boolean desugaringEnabled = appView.options().isInterfaceMethodDesugaringEnabled();
    MethodLookupResult lookupResult = appView.graphLens().lookupMethod(method, method, Type.DIRECT);
    if (lookupResult.getType() == Type.VIRTUAL) {
      // The method has been publicized. We can't always expect private methods that have been
      // publicized to be final. For example, if a private method A.m() is publicized, and A is
      // subsequently merged with a class B, with declares a public non-final method B.m(), then the
      // horizontal class merger will merge A.m() and B.m() into a new non-final public method.
      return Type.VIRTUAL;
    }
    DexMethod rewrittenMethod = lookupResult.getReference();
    DexEncodedMethod definition = lookupMethodOnHolder(appView, rewrittenMethod);
    if (definition == null) {
      // The method is not defined on the class, we can use super to target. When desugaring
      // default interface methods, it is expected they are targeted with invoke-direct.
      return this.itf && desugaringEnabled ? Type.DIRECT : Type.SUPER;
    }
    if (definition.isPrivateMethod() || !definition.isVirtualMethod()) {
      return Type.DIRECT;
    }
    if (definition.isFinal()) {
      // This method is final which indicates no subtype will overwrite it, we can use
      // invoke-virtual.
      return Type.VIRTUAL;
    }
    if (itf && definition.isDefaultMethod()) {
      return desugaringEnabled ? Type.DIRECT : Type.SUPER;
    }
    // We cannot emulate the semantics of invoke-special in this case and should throw a compilation
    // error.
    throw new CompilationError("Failed to compile unsupported use of invokespecial", origin);
  }

  private DexEncodedMethod lookupMethodOnHolder(AppView<?> appView, DexMethod method) {
    // Directly lookup the program type for holder. This bypasses lookup order as well as looks
    // directly on the application data, which bypasses and indirection or validation.
    DexProgramClass clazz = appView.appInfo().unsafeDirectProgramTypeLookup(method.getHolderType());
    assert clazz != null;
    return clazz.lookupMethod(method);
  }
}
