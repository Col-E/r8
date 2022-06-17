// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicReference;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import java.util.ListIterator;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfConstDynamic extends CfInstruction implements CfTypeInstruction {

  private final ConstantDynamicReference reference;

  public CfConstDynamic(
      DexString name,
      DexType type,
      DexMethodHandle bootstrapMethod,
      Object[] bootstrapMethodArguments) {
    assert name != null;
    assert type != null;
    assert bootstrapMethod != null;
    assert bootstrapMethodArguments != null;
    assert bootstrapMethodArguments.length == 0;

    reference = new ConstantDynamicReference(name, type, bootstrapMethod, bootstrapMethodArguments);
  }

  @Override
  public CfConstDynamic asConstDynamic() {
    return this;
  }

  @Override
  public boolean isConstDynamic() {
    return true;
  }

  public ConstantDynamicReference getReference() {
    return reference;
  }

  public DexString getName() {
    return reference.getName();
  }

  public DexMethodHandle getBootstrapMethod() {
    return reference.getBootstrapMethod();
  }

  public Object[] getBootstrapMethodArguments() {
    return reference.getBootstrapMethodArguments();
  }

  public static CfConstDynamic fromAsmConstantDynamic(
      ConstantDynamic insn, JarApplicationReader application, DexType clazz) {
    String constantName = insn.getName();
    String constantDescriptor = insn.getDescriptor();
    // TODO(b/178172809): Handle bootstrap arguments.
    if (insn.getBootstrapMethodArgumentCount() > 0) {
      throw new CompilationError(
          "Unsupported dynamic constant (has arguments to bootstrap method)");
    }
    if (insn.getBootstrapMethod().getTag() != Opcodes.H_INVOKESTATIC) {
      throw new CompilationError("Unsupported dynamic constant (not invoke static)");
    }
    if (insn.getBootstrapMethod().getOwner().equals("java/lang/invoke/ConstantBootstraps")) {
      throw new CompilationError(
          "Unsupported dynamic constant (runtime provided bootstrap method)");
    }
    if (application.getTypeFromName(insn.getBootstrapMethod().getOwner()) != clazz) {
      throw new CompilationError("Unsupported dynamic constant (different owner)");
    }
    // Resolve the bootstrap method.
    DexMethodHandle bootstrapMethodHandle =
        DexMethodHandle.fromAsmHandle(insn.getBootstrapMethod(), application, clazz);
    if (!bootstrapMethodHandle.member.isDexMethod()) {
      throw new CompilationError("Unsupported dynamic constant (invalid method handle)");
    }
    DexMethod bootstrapMethod = bootstrapMethodHandle.asMethod();
    if (bootstrapMethod.getProto().returnType != application.getTypeFromDescriptor("[Z")
        && bootstrapMethod.getProto().returnType
            != application.getTypeFromDescriptor("Ljava/lang/Object;")) {
      throw new CompilationError("Unsupported dynamic constant (unsupported constant type)");
    }
    if (bootstrapMethod.getProto().getParameters().size() != 3) {
      throw new CompilationError("Unsupported dynamic constant (unsupported signature)");
    }
    if (bootstrapMethod.getProto().getParameters().get(0) != application.getFactory().lookupType) {
      throw new CompilationError(
          "Unsupported dynamic constant (unexpected type of first argument to bootstrap method");
    }
    if (bootstrapMethod.getProto().getParameters().get(1) != application.getFactory().stringType) {
      throw new CompilationError(
          "Unsupported dynamic constant (unexpected type of second argument to bootstrap method");
    }
    if (bootstrapMethod.getProto().getParameters().get(2) != application.getFactory().classType) {
      throw new CompilationError(
          "Unsupported dynamic constant (unexpected type of third argument to bootstrap method");
    }
    return new CfConstDynamic(
        application.getString(constantName),
        application.getTypeFromDescriptor(constantDescriptor),
        bootstrapMethodHandle,
        new Object[] {});
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.CONST_DYNAMIC_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    int diff = getName().acceptCompareTo(((CfConstDynamic) other).getName(), visitor);
    if (diff != 0) {
      return diff;
    }
    diff = getType().acceptCompareTo(((CfConstDynamic) other).getType(), visitor);
    if (diff != 0) {
      return diff;
    }
    return getBootstrapMethod()
        .acceptCompareTo(((CfConstDynamic) other).getBootstrapMethod(), visitor);
  }

  @Override
  public CfTypeInstruction asTypeInstruction() {
    return this;
  }

  @Override
  public boolean isTypeInstruction() {
    return true;
  }

  @Override
  public DexType getType() {
    return reference.getType();
  }

  @Override
  public CfInstruction withType(DexType newType) {
    throw new Unimplemented();
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
    // TODO(b/198142625): Support CONSTANT_Dynamic for R8 cf to cf.
    throw new CompilationError("Unsupported dynamic constant (not desugaring)");
  }

  @Override
  public int bytecodeSizeUpperBound() {
    // ldc or ldc_w
    return 3;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerTypeReference(reference.getType());
    registry.registerMethodHandle(
        reference.getBootstrapMethod(), NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
    assert reference.getBootstrapMethodArguments().length == 0;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    throw new CompilationError("Unsupported dynamic constant (not desugaring)");
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    throw new Unreachable();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ... →
    // ..., value
    return frame.push(config, appView.dexItemFactory().classType);
  }
}
