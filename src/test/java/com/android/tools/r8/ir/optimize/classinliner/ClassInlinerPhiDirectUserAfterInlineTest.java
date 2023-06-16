// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/178608910.
@RunWith(Parameterized.class)
public class ClassInlinerPhiDirectUserAfterInlineTest extends TestBase {

  private final TestParameters parameters;
  private final List<String> EXPECTED = ImmutableList.of("0", "A::baz");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ClassInlinerPhiDirectUserAfterInlineTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoClassInlining() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
              // Here we modify the IR when it is processed normally.
              options.testing.irModifier = this::modifyIr;
              options.enableClassInlining = false;
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.testing.validInliningReasons = ImmutableSet.of(Reason.ALWAYS);
              options.testing.inlineeIrModifier = this::modifyIr;
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              // Assert that the A has been class-inlined into the caller.
              ClassSubject aSubject = inspector.clazz(A.class);
              assertThat(aSubject, not(isPresent()));
            });
  }

  private void modifyIr(IRCode irCode, AppView<?> appView) {
    modifyIr(irCode);
  }

  private void modifyIr(IRCode irCode) {
    if (irCode.context().getReference().qualifiedName().equals(A.class.getTypeName() + ".foo")) {
      assertEquals(7, irCode.blocks.size());
      // This is the code that we expect
      // <pre>
      // blocks:
      // block 0, pred-counts: 0, succ-count: 1, filled: true, sealed: true
      // predecessors: -
      // successors: 1  (no try/catch successors)
      // no phis
      //  #0:foo;0:main: -1: Argument             v3 <-
      //               : -1: ConstNumber          v4(0) <-  0 (INT)
      //               : -1: Goto                 block 1
      //
      // block 1, pred-counts: 2, succ-count: 2, filled: true, sealed: true
      // predecessors: 0 5
      // successors: 2 3  (no try/catch successors)
      // v7 <- phi(v4(0), v13) : INT
      //  #0:foo;0:main: -1: InstanceGet          v6 <- v3; field: int
      // com.android.tools.r8.ir.optimize.classinliner
      //    .ClassInlinerPhiDirectUserAfterInlineTest$A.number
      //               : -1: If                   v6 EQZ block 2 (fallthrough 3)
      //
      // block 2, pred-counts: 1, succ-count: 0, filled: true, sealed: true
      // predecessors: 1
      // successors: -
      // no phis
      //  #0:foo;0:main: -1: Invoke-Virtual       v3, v7; method: void
      // com.android.tools.r8.ir.optimize.classinliner
      //  .ClassInlinerPhiDirectUserAfterInlineTest$A.bar(int)
      //               : -1: Return
      //
      // block 3, pred-counts: 1, succ-count: 1, filled: true, sealed: true
      // predecessors: 1
      // successors: 4  (no try/catch successors)
      // no phis
      //  #0:foo;0:main: -1: ConstNumber          v8(2) <-  2 (INT)
      //               : -1: Add                  v9 <- v7, v8(2)
      //               : -1: Goto                 block 4
      //
      // block 4, pred-counts: 2, succ-count: 2, filled: true, sealed: true
      // predecessors: 3 6
      // successors: 5 6  (no try/catch successors)
      // v13 <- phi(v9, v15) : INT
      //  #0:foo;0:main: -1: InstanceGet          v11 <- v3; field: int
      // com.android.tools.r8.ir.optimize.classinliner
      //  .ClassInlinerPhiDirectUserAfterInlineTest$A.number
      //               : -1: ConstNumber          v12(10) <-  10 (INT)
      //               : -1: If                   v11, v12(10) LE  block 5
      // (fallthrough 6)
      //
      // block 5, pred-counts: 1, succ-count: 1, filled: true, sealed: true
      // predecessors: 4
      // successors: 1  (no try/catch successors)
      // no phis
      //  #0:foo;0:main: -1: Goto                 block 1
      //
      // block 6, pred-counts: 1, succ-count: 1, filled: true, sealed: true
      // predecessors: 4
      // successors: 4  (no try/catch successors)
      // no phis
      //  #0:foo;0:main: -1: ConstNumber          v14(3) <-  3 (INT)
      //               : -1: Add                  v15 <- v13, v14(3)
      //               : -1: ConstNumber          v16(-1) <-  -1 (INT)
      //               : -1: Add                  v17 <- v11, v16(-1)
      //               : -1: InstancePut          v3, v17; field: int
      // com.android.tools.r8.ir.optimize.classinliner
      //  .ClassInlinerPhiDirectUserAfterInlineTest$A.number
      //               : -1: Goto                 block 4
      // </pre>
      // We modify block 1 to have:
      // vX : phi(v3, vY)
      // .. InstanceGet       vX ...
      //
      // and block 4 to have:
      // vY : phi(v3, vX)
      BasicBlock basicBlock = irCode.blocks.get(0);
      Argument argument = basicBlock.getInstructions().get(0).asArgument();
      assertNotNull(argument);
      Value argumentValue = argument.outValue();

      BasicBlock block1 = irCode.blocks.stream().filter(b -> b.getNumber() == 1).findFirst().get();
      assertTrue(block1.exit().isIf());

      BasicBlock block3 = irCode.blocks.stream().filter(b -> b.getNumber() == 3).findFirst().get();
      assertTrue(block1.getSuccessors().contains(block3));
      assertTrue(block3.exit().isGoto());

      BasicBlock block4 = irCode.blocks.stream().filter(b -> b.getNumber() == 4).findFirst().get();
      assertSame(block3.getUniqueNormalSuccessor(), block4);

      Phi firstPhi =
          new Phi(
              irCode.valueNumberGenerator.next(),
              block1,
              argumentValue.getType(),
              null,
              RegisterReadType.NORMAL);

      Phi secondPhi =
          new Phi(
              irCode.valueNumberGenerator.next(),
              block4,
              argumentValue.getType(),
              null,
              RegisterReadType.NORMAL);

      firstPhi.addOperands(ImmutableList.of(argumentValue, secondPhi));
      secondPhi.addOperands(ImmutableList.of(argumentValue, firstPhi));

      // Replace the invoke to use the phi
      InstanceGet instanceGet = block1.getInstructions().get(0).asInstanceGet();
      assertNotNull(instanceGet);
      assertEquals(A.class.getTypeName(), instanceGet.getField().holder.toSourceString());
      instanceGet.replaceValue(0, firstPhi);
    }
  }

  public static class A {

    int number = 0;

    public void foo() {
      int otherNumber = 0;
      while (number != 0) {
        otherNumber += 2;
        while (number > 10) {
          otherNumber += 3;
          number--;
        }
      }
      bar(otherNumber);
    }

    public void bar(int number) {
      System.out.println(number + "");
    }

    public void baz() {
      System.out.println("A::baz");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A a = new A();
      a.number = args.length;
      a.foo();
      a.baz();
    }
  }
}
