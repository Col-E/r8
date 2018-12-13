// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.LinearFlowInstructionIterator;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.ListIterator;
import org.junit.Test;

public class LinearFlowIteratorTest extends TestBase {

  IRCode branchingCode() throws Exception {

    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder clazz = jasminBuilder.addClass("foo");
    clazz.addStaticMethod(
        "bar",
        ImmutableList.of("I"),
        "V",
        ".limit stack 2",
        ".limit locals 2",
        ".var 1 is x Ljava/lang/Object; from L1 to L2",
        "  aconst_null",
        "  astore 1",
        "L1:",
        "  iload 0",
        "  ifeq L3",
        "L2:",
        "  goto L5",
        "L3:",
        "  aload 1",
        "  iconst_0",
        "  aaload",
        "  pop",
        "L5:",
        "  return");
    AndroidApp.Builder appBuilder = AndroidApp.builder();
    appBuilder.addClassProgramData(jasminBuilder.buildClasses());

    // Build the code, and split the code into three blocks.
    ValueNumberGenerator valueNumberGenerator = new ValueNumberGenerator();
    AndroidApp app = compileWithD8(appBuilder.build());
    AppInfo appInfo = getAppInfo(app);
    DexEncodedMethod method = getMethod(app, "foo", "void", "bar", ImmutableList.of("int"));
    IRCode code =
        method.buildInliningIRForTesting(new InternalOptions(), valueNumberGenerator, appInfo);
    ListIterator<BasicBlock> blocks = code.listIterator();
    blocks.next();
    InstructionListIterator iter = blocks.next().listIterator();
    iter.nextUntil(i -> !i.isConstNumber());
    iter.previous();
    iter.split(code, blocks);
    return code;
  }

  IRCode simpleCode() throws Exception {

    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder clazz = jasminBuilder.addClass("foo");
    clazz.addStaticMethod(
        "bar",
        ImmutableList.of("I"),
        "V",
        ".limit stack 2",
        ".limit locals 2",
        ".var 1 is x Ljava/lang/Object; from L1 to L2",
        "L1:",
        "  aload 1",
        "  iconst_0",
        "  aaload",
        "  pop",
        "L2:",
        "  return");
    AndroidApp.Builder appBuilder = AndroidApp.builder();
    appBuilder.addClassProgramData(jasminBuilder.buildClasses());

    // Build the code, and split the code into three blocks.
    ValueNumberGenerator valueNumberGenerator = new ValueNumberGenerator();
    AndroidApp app = compileWithD8(appBuilder.build());
    AppInfo appInfo = getAppInfo(app);
    DexEncodedMethod method = getMethod(app, "foo", "void", "bar", ImmutableList.of("int"));
    IRCode code =
        method.buildInliningIRForTesting(new InternalOptions(), valueNumberGenerator, appInfo);
    ListIterator<BasicBlock> blocks = code.listIterator();
    InstructionListIterator iter = blocks.next().listIterator();
    iter.nextUntil(i -> !i.isArgument());
    iter.split(code, 0, blocks);
    return code;
  }

  @Test
  public void hasNextWillCheckNextBlock() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(0));
    Instruction current = it.next();
    current = it.next();
    assert it.hasNext();
  }

  @Test
  public void nextWillContinueThroughGotoBlocks() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(0));
    Instruction current = it.next();
    current = it.next();
    current = it.next();
    current = it.next();
    assert current.isArrayGet();
  }

  @Test
  public void hasPreviousWillCheckPreviousBlock() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(2));
    assert it.hasPrevious();
  }

  @Test
  public void hasPreviousWillJumpOverGotos() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(2));
    assert it.previous().isConstNumber();
  }

  @Test
  public void GoToFrontAndBackIsSameAmountOfInstructions() throws Exception {
    IRCode code = simpleCode();
    int moves = 0;
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(0));
    while (it.hasNext()) {
      it.next();
      moves++;
    }
    Instruction current = null;
    for (int i = 0; i < moves; i++) {
      current = it.previous();
    }
    assert !it.hasPrevious();
    assert current.isArgument();
  }

  @Test
  public void moveFromEmptyBlock() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(1));
    Instruction current = it.previous();
    assert current.isConstNumber() && current.outValue().getTypeLattice().isReference();
    it.next();
    current = it.next();
    assert current.isConstNumber() && current.outValue().getTypeLattice().isInt();
  }

  @Test
  public void doNotChangeToNextBlockWhenNotLinearFlow() throws Exception {
    IRCode code = branchingCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(0));
    it.nextUntil((i) -> !i.isArgument());
    Instruction current = it.next();
    assert !it.hasNext();
  }

  @Test
  public void doNotChangeToPreviousBlockWhenNotLinearFlow() throws Exception {
    IRCode code = branchingCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(4));
    assert !it.hasPrevious();
  }

  @Test
  public void followLinearSubPathDown() throws Exception {
    IRCode code = branchingCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(1));
    Instruction current = null;
    while (it.hasNext()) {
      current = it.next();
    }
    assert current.isGoto();
  }

  @Test
  public void followLinearSubPathUp() throws Exception {
    IRCode code = branchingCode();
    InstructionListIterator it = new LinearFlowInstructionIterator(code.blocks.get(2));
    Instruction current = null;
    while (it.hasPrevious()) {
      current = it.previous();
    }
    assert current.isConstNumber();
  }
}
