// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstMethodHandle;
import com.android.tools.r8.ir.code.ConstMethodType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.kotlin.Kotlin;
import java.util.ListIterator;
import java.util.function.Function;

// Performs processing of the method code (all methods) needed by lambda rewriter.
//
// Functionality can be modified by strategy (specific to lambda group) and lambda
// type visitor.
//
// In general it is used to
//   (a) track the code for illegal lambda type usages inside the code, and
//   (b) patching valid lambda type references to point to lambda group classes instead.
//
// This class is also used inside particular strategies as a context of the instruction
// being checked or patched, it provides access to code, block and instruction iterators.
public abstract class CodeProcessor {
  // Strategy (specific to lambda group) for detecting valid references to the
  // lambda classes (of this group) and patching them with group class references.
  public interface Strategy {
    LambdaGroup group();

    boolean isValidStaticFieldWrite(CodeProcessor context, DexField field);

    boolean isValidStaticFieldRead(CodeProcessor context, DexField field);

    boolean isValidInstanceFieldWrite(CodeProcessor context, DexField field);

    boolean isValidInstanceFieldRead(CodeProcessor context, DexField field);

    boolean isValidInvoke(CodeProcessor context, InvokeMethod invoke);

    boolean isValidNewInstance(CodeProcessor context, NewInstance invoke);

    void patch(CodeProcessor context, NewInstance newInstance);

    void patch(CodeProcessor context, InvokeMethod invoke);

    void patch(CodeProcessor context, InstancePut instancePut);

    void patch(CodeProcessor context, InstanceGet instanceGet);

    void patch(CodeProcessor context, StaticPut staticPut);

    void patch(CodeProcessor context, StaticGet staticGet);
  }

  // No-op strategy.
  static final Strategy NoOp = new Strategy() {
    @Override
    public LambdaGroup group() {
      return null;
    }

    @Override
    public boolean isValidInstanceFieldWrite(CodeProcessor context, DexField field) {
      return false;
    }

    @Override
    public boolean isValidInstanceFieldRead(CodeProcessor context, DexField field) {
      return false;
    }

    @Override
    public boolean isValidStaticFieldWrite(CodeProcessor context, DexField field) {
      return false;
    }

    @Override
    public boolean isValidStaticFieldRead(CodeProcessor context, DexField field) {
      return false;
    }

    @Override
    public boolean isValidInvoke(CodeProcessor context, InvokeMethod invoke) {
      return false;
    }

    @Override
    public boolean isValidNewInstance(CodeProcessor context, NewInstance invoke) {
      return false;
    }

    @Override
    public void patch(CodeProcessor context, NewInstance newInstance) {
      throw new Unreachable();
    }

    @Override
    public void patch(CodeProcessor context, InvokeMethod invoke) {
      throw new Unreachable();
    }

    @Override
    public void patch(CodeProcessor context, InstancePut instancePut) {
      throw new Unreachable();
    }

    @Override
    public void patch(CodeProcessor context, InstanceGet instanceGet) {
      throw new Unreachable();
    }

    @Override
    public void patch(CodeProcessor context, StaticPut staticPut) {
      throw new Unreachable();
    }

    @Override
    public void patch(CodeProcessor context, StaticGet staticGet) {
      throw new Unreachable();
    }
  };

  public final AppInfo appInfo;
  public final DexItemFactory factory;
  public final Kotlin kotlin;

  // Defines a factory providing a strategy for a lambda type, returns
  // NoOp strategy if the type is not a lambda.
  private final Function<DexType, Strategy> strategyProvider;

  // Visitor for lambda type references seen in unexpected places. Either
  // invalidates the lambda or asserts depending on the processing phase.
  private final LambdaTypeVisitor lambdaChecker;

  // Specify the context of the current instruction: method/code/blocks/instructions.
  public final DexEncodedMethod method;
  public final IRCode code;
  public final ListIterator<BasicBlock> blocks;
  private InstructionListIterator instructions;

  CodeProcessor(
      AppInfo appInfo,
      Function<DexType, Strategy> strategyProvider,
      LambdaTypeVisitor lambdaChecker,
      DexEncodedMethod method, IRCode code) {

    this.appInfo = appInfo;
    this.strategyProvider = strategyProvider;
    this.factory = appInfo.dexItemFactory;
    this.kotlin = factory.kotlin;
    this.lambdaChecker = lambdaChecker;
    this.method = method;
    this.code = code;
    this.blocks = code.listIterator();
  }

  public final InstructionListIterator instructions() {
    assert instructions != null;
    return instructions;
  }

  final void processCode() {
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      instructions = block.listIterator();
      while (instructions.hasNext()) {
        onInstruction(instructions.next());
      }
    }
  }

  private void onInstruction(Instruction instruction) {
    if (instruction.isInvoke()) {
      handle(instruction.asInvoke());
    } else if (instruction.isNewInstance()) {
      handle(instruction.asNewInstance());
    } else if (instruction.isCheckCast()) {
      handle(instruction.asCheckCast());
    } else if (instruction.isNewArrayEmpty()) {
      handle(instruction.asNewArrayEmpty());
    } else if (instruction.isConstClass()) {
      handle(instruction.asConstClass());
    } else if (instruction.isConstMethodType()) {
      handle(instruction.asConstMethodType());
    } else if (instruction.isConstMethodHandle()) {
      handle(instruction.asConstMethodHandle());
    } else if (instruction.isInstanceGet()) {
      handle(instruction.asInstanceGet());
    } else if (instruction.isInstancePut()) {
      handle(instruction.asInstancePut());
    } else if (instruction.isStaticGet()) {
      handle(instruction.asStaticGet());
    } else if (instruction.isStaticPut()) {
      handle(instruction.asStaticPut());
    }
  }

  private void handle(Invoke invoke) {
    if (invoke.isInvokeNewArray()) {
      lambdaChecker.accept(invoke.asInvokeNewArray().getReturnType());
      return;
    }
    if (invoke.isInvokeMultiNewArray()) {
      lambdaChecker.accept(invoke.asInvokeMultiNewArray().getReturnType());
      return;
    }
    if (invoke.isInvokeCustom()) {
      lambdaChecker.accept(invoke.asInvokeCustom().getCallSite());
      return;
    }

    InvokeMethod invokeMethod = invoke.asInvokeMethod();
    Strategy strategy = strategyProvider.apply(invokeMethod.getInvokedMethod().holder);
    if (strategy.isValidInvoke(this, invokeMethod)) {
      // Invalidate signature, there still should not be lambda references.
      lambdaChecker.accept(invokeMethod.getInvokedMethod().proto);
      // Only rewrite references to lambda classes if we are outside the class.
      if (invokeMethod.getInvokedMethod().holder != this.method.method.holder) {
        process(strategy, invokeMethod);
      }
      return;
    }

    // For the rest invalidate any references.
    if (invoke.isInvokePolymorphic()) {
      lambdaChecker.accept(invoke.asInvokePolymorphic().getProto());
    }
    lambdaChecker.accept(invokeMethod.getInvokedMethod(), null);
  }

  private void handle(NewInstance newInstance) {
    Strategy strategy = strategyProvider.apply(newInstance.clazz);
    if (strategy.isValidNewInstance(this, newInstance)) {
      // Only rewrite references to lambda classes if we are outside the class.
      if (newInstance.clazz != this.method.method.holder) {
        process(strategy, newInstance);
      }
    }
  }

  private void handle(CheckCast checkCast) {
    lambdaChecker.accept(checkCast.getType());
  }

  private void handle(NewArrayEmpty newArrayEmpty) {
    lambdaChecker.accept(newArrayEmpty.type);
  }

  private void handle(ConstClass constClass) {
    lambdaChecker.accept(constClass.getValue());
  }

  private void handle(ConstMethodType constMethodType) {
    lambdaChecker.accept(constMethodType.getValue());
  }

  private void handle(ConstMethodHandle constMethodHandle) {
    lambdaChecker.accept(constMethodHandle.getValue());
  }

  private void handle(InstanceGet instanceGet) {
    DexField field = instanceGet.getField();
    Strategy strategy = strategyProvider.apply(field.clazz);
    if (strategy.isValidInstanceFieldRead(this, field)) {
      if (field.clazz != this.method.method.holder) {
        // Only rewrite references to lambda classes if we are outside the class.
        process(strategy, instanceGet);
      }
    } else {
      lambdaChecker.accept(field.type);
    }

    // We avoid fields with type being lambda class, it is possible for
    // a lambda to capture another lambda, but we don't support it for now.
    lambdaChecker.accept(field.type);
  }

  private void handle(InstancePut instancePut) {
    DexField field = instancePut.getField();
    Strategy strategy = strategyProvider.apply(field.clazz);
    if (strategy.isValidInstanceFieldWrite(this, field)) {
      if (field.clazz != this.method.method.holder) {
        // Only rewrite references to lambda classes if we are outside the class.
        process(strategy, instancePut);
      }
    } else {
      lambdaChecker.accept(field.type);
    }

    // We avoid fields with type being lambda class, it is possible for
    // a lambda to capture another lambda, but we don't support it for now.
    lambdaChecker.accept(field.type);
  }

  private void handle(StaticGet staticGet) {
    DexField field = staticGet.getField();
    Strategy strategy = strategyProvider.apply(field.clazz);
    if (strategy.isValidStaticFieldRead(this, field)) {
      if (field.clazz != this.method.method.holder) {
        // Only rewrite references to lambda classes if we are outside the class.
        process(strategy, staticGet);
      }
    } else {
      lambdaChecker.accept(field.type);
      lambdaChecker.accept(field.clazz);
    }
  }

  private void handle(StaticPut staticPut) {
    DexField field = staticPut.getField();
    Strategy strategy = strategyProvider.apply(field.clazz);
    if (strategy.isValidStaticFieldWrite(this, field)) {
      if (field.clazz != this.method.method.holder) {
        // Only rewrite references to lambda classes if we are outside the class.
        process(strategy, staticPut);
      }
    } else {
      lambdaChecker.accept(field.type);
      lambdaChecker.accept(field.clazz);
    }
  }

  abstract void process(Strategy strategy, InvokeMethod invokeMethod);

  abstract void process(Strategy strategy, NewInstance newInstance);

  abstract void process(Strategy strategy, InstancePut instancePut);

  abstract void process(Strategy strategy, InstanceGet instanceGet);

  abstract void process(Strategy strategy, StaticPut staticPut);

  abstract void process(Strategy strategy, StaticGet staticGet);
}
