// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
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
import java.util.Arrays;
import java.util.ListIterator;
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

  @Override
  public int getCompareToId() {
    return getOpcode();
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    CfInvoke otherInvoke = other.asInvoke();
    int itfDiff = Boolean.compare(itf, otherInvoke.itf);
    return itfDiff != 0 ? itfDiff : method.slowCompareTo(otherInvoke.method);
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

  private Invoke.Type getInvokeType(DexClassAndMethod context) {
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
      InliningConstraints inliningConstraints, ProgramMethod context) {
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
        if (appView.dexItemFactory().isConstructor(target)) {
          type = Type.DIRECT;
          assert noNeedToUseGraphLens(target, context.getReference(), type, graphLens);
        } else if (target.holder == context.getHolderType()) {
          // The method could have been publicized.
          type = graphLens.lookupMethod(target, context.getReference(), Type.DIRECT).getType();
          assert type == Type.DIRECT || type == Type.VIRTUAL;
        } else {
          // This is a super call. Note that the vertical class merger translates some invoke-super
          // instructions to invoke-direct. However, when that happens, the invoke instruction and
          // the target method end up being in the same class, and therefore, we will allow inlining
          // it. The result of using type=SUPER below will be the same, since it leads to the
          // inlining constraint SAMECLASS.
          // TODO(christofferqa): Consider using graphLens.lookupMethod (to do this, we need the
          // context for the graph lens, though).
          type = Type.SUPER;
          assert noNeedToUseGraphLens(target, context.getReference(), type, graphLens);
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
    frameBuilder.popAndDiscard(this.method.proto.parameters.values);
    if (opcode == Opcodes.INVOKESPECIAL && method.isInstanceInitializer(factory)) {
      frameBuilder.popAndInitialize(context, method.holder);
    } else if (opcode != Opcodes.INVOKESTATIC) {
      frameBuilder.pop(method.holder);
    }
    if (this.method.proto.returnType != factory.voidType) {
      frameBuilder.push(this.method.proto.returnType);
    }
  }

  private static boolean noNeedToUseGraphLens(
      DexMethod method, DexMethod context, Invoke.Type type, GraphLens graphLens) {
    assert graphLens.lookupMethod(method, context, type).getType() == type;
    return true;
  }

  private Type invokeTypeForInvokeSpecialToNonInitMethodOnHolder(
      AppView<?> appView, CfSourceCode code) {
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
    DexEncodedMethod encodedMethod = lookupMethodOnHolder(appView, rewrittenMethod);
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

  private DexEncodedMethod lookupMethodOnHolder(AppView<?> appView, DexMethod method) {
    // Directly lookup the program type for holder. This bypasses lookup order as well as looks
    // directly on the application data, which bypasses and indirection or validation.
    DexProgramClass clazz = appView.appInfo().unsafeDirectProgramTypeLookup(method.getHolderType());
    assert clazz != null;
    return clazz.lookupMethod(method);
  }
}
