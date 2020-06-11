// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.GraphLenseLookupResult;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import java.util.Arrays;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfInvoke extends CfInstruction {

  private final DexMethod method;
  private final int opcode;
  private final boolean itf;

  public CfInvoke(int opcode, DexMethod method, boolean itf) {
    assert Opcodes.INVOKEVIRTUAL <= opcode && opcode <= Opcodes.INVOKEINTERFACE;
    assert !(opcode == Opcodes.INVOKEVIRTUAL && itf) : "InvokeVirtual on interface type";
    assert !(opcode == Opcodes.INVOKEINTERFACE && !itf) : "InvokeInterface on class type";
    this.opcode = opcode;
    this.method = method;
    this.itf = itf;
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
  public void write(MethodVisitor visitor, InitClassLens initClassLens, NamingLens lens) {
    String owner = lens.lookupInternalName(method.holder);
    String name = lens.lookupName(method).toString();
    String desc = method.proto.toDescriptorString(lens);
    visitor.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(UseRegistry registry, DexClassAndMethod context) {
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        registry.registerInvokeInterface(method);
        break;
      case Opcodes.INVOKEVIRTUAL:
        registry.registerInvokeVirtual(method);
        break;
      case Opcodes.INVOKESPECIAL:
        if (method.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)) {
          registry.registerInvokeDirect(method);
        } else if (method.holder == context.getHolderType()) {
          registry.registerInvokeDirect(method);
        } else {
          registry.registerInvokeSuper(method);
        }
        break;
      case Opcodes.INVOKESTATIC:
        registry.registerInvokeStatic(method);
        break;
      default:
        throw new Unreachable("unknown CfInvoke opcode " + opcode);
    }
  }

  public boolean isInvokeSuper(DexType clazz) {
    return opcode == Opcodes.INVOKESPECIAL &&
        method.holder != clazz &&
        !method.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME);
  }

  public boolean isInvokeVirtual() {
    return opcode == Opcodes.INVOKEVIRTUAL;
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
          if (method.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)) {
            type = Type.DIRECT;
          } else if (code.getOriginalHolder() == method.holder) {
            type = invokeTypeForInvokeSpecialToNonInitMethodOnHolder(builder.appView, code);
          } else {
            type = Type.SUPER;
          }
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
      InliningConstraints inliningConstraints, DexProgramClass context) {
    GraphLense graphLense = inliningConstraints.getGraphLense();
    AppView<?> appView = inliningConstraints.getAppView();
    DexMethod target = method;
    // Find the DEX invocation type.
    Type type;
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        // Could have changed to an invoke-virtual instruction due to vertical class merging
        // (if an interface is merged into a class).
        type = graphLense.lookupMethod(target, null, Type.INTERFACE).getType();
        assert type == Type.INTERFACE || type == Type.VIRTUAL;
        break;

      case Opcodes.INVOKESPECIAL:
        if (appView.dexItemFactory().isConstructor(target)) {
          type = Type.DIRECT;
          assert noNeedToUseGraphLense(target, type, graphLense);
        } else if (target.holder == context.type) {
          // The method could have been publicized.
          type = graphLense.lookupMethod(target, null, Type.DIRECT).getType();
          assert type == Type.DIRECT || type == Type.VIRTUAL;
        } else {
          // This is a super call. Note that the vertical class merger translates some invoke-super
          // instructions to invoke-direct. However, when that happens, the invoke instruction and
          // the target method end up being in the same class, and therefore, we will allow inlining
          // it. The result of using type=SUPER below will be the same, since it leads to the
          // inlining constraint SAMECLASS.
          // TODO(christofferqa): Consider using graphLense.lookupMethod (to do this, we need the
          // context for the graph lense, though).
          type = Type.SUPER;
          assert noNeedToUseGraphLense(target, type, graphLense);
        }
        break;

      case Opcodes.INVOKESTATIC:
        {
          // Static invokes may have changed as a result of horizontal class merging.
          GraphLenseLookupResult lookup = graphLense.lookupMethod(target, null, Type.STATIC);
          target = lookup.getMethod();
          type = lookup.getType();
        }
        break;

      case Opcodes.INVOKEVIRTUAL:
        {
          type = Type.VIRTUAL;
          // Instructions that target a private method in the same class translates to
          // invoke-direct.
          if (target.holder == context.type) {
            DexClass clazz = appView.definitionFor(target.holder);
            if (clazz != null && clazz.lookupDirectMethod(target) != null) {
              type = Type.DIRECT;
            }
          }

          // Virtual invokes may have changed to interface invokes as a result of member rebinding.
          GraphLenseLookupResult lookup = graphLense.lookupMethod(target, null, type);
          target = lookup.getMethod();
          type = lookup.getType();
        }
        break;

      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }

    return inliningConstraints.forInvoke(target, type, context);
  }

  private static boolean noNeedToUseGraphLense(
      DexMethod method, Invoke.Type type, GraphLense graphLense) {
    assert graphLense.lookupMethod(method, null, type).getType() == type;
    return true;
  }

  private Type invokeTypeForInvokeSpecialToNonInitMethodOnHolder(
      AppView<?> appView, CfSourceCode code) {
    boolean desugaringEnabled = appView.options().isInterfaceMethodDesugaringEnabled();
    DexEncodedMethod encodedMethod = lookupMethod(appView, method);
    if (encodedMethod == null) {
      // The method is not defined on the class, we can use super to target. When desugaring
      // default interface methods, it is expected they are targeted with invoke-direct.
      return this.itf && desugaringEnabled ? Type.DIRECT : Type.SUPER;
    }
    if (encodedMethod.isPrivateMethod() || !encodedMethod.isVirtualMethod()) {
      return Type.DIRECT;
    }
    if (encodedMethod.accessFlags.isFinal()) {
      // This method is final which indicates no subtype will overwrite it, we can use
      // invoke-virtual.
      return Type.VIRTUAL;
    }
    if (this.itf && encodedMethod.isDefaultMethod()) {
      return desugaringEnabled ? Type.DIRECT : Type.SUPER;
    }
    // We cannot emulate the semantics of invoke-special in this case and should throw a compilation
    // error.
    throw new CompilationError(
        "Failed to compile unsupported use of invokespecial", code.getOrigin());
  }

  private DexEncodedMethod lookupMethod(AppView<?> appView, DexMethod method) {
    GraphLenseLookupResult lookupResult =
        appView.graphLense().lookupMethod(method, method, Type.DIRECT);
    DexMethod rewrittenMethod = lookupResult.getMethod();
    DexProgramClass clazz = appView.definitionForProgramType(rewrittenMethod.holder);
    assert clazz != null;
    return clazz.lookupMethod(rewrittenMethod);
  }
}
