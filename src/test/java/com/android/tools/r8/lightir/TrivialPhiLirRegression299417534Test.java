// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.lightir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.Timing;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TrivialPhiLirRegression299417534Test extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  private final TestParameters parameters;

  public TrivialPhiLirRegression299417534Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    BooleanBox foundPhi = new BooleanBox(false);

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addOptionsModification(
            o ->
                o.testing.irModifier =
                    (code, appView) -> {
                      if (!code.context().getReference().getName().toString().equals("foo")) {
                        return;
                      }
                      foundPhi.set(true);
                      // Update the return phi to be trivial and have both its cases be 'v2' from
                      // the code.
                      //   v3 <- phi(v1, v2)
                      // becomes
                      //   v3 <- phi(v2, v2)
                      List<BasicBlock> exits = code.computeNormalExitBlocks();
                      assertEquals(1, exits.size());
                      BasicBlock exit = exits.get(0);
                      assertEquals(1, exit.getPhis().size());
                      List<Value> operands = exit.getPhis().get(0).getOperands();
                      assertEquals(2, operands.size());
                      Value op1 = operands.get(0);
                      Value op2 = operands.get(1);
                      Value withNonPhiUse = op1.hasUsers() ? op1 : op2;
                      Value withOnlyPhiUse = op1.hasUsers() ? op2 : op1;
                      assertFalse(withOnlyPhiUse.hasUsers());
                      withNonPhiUse.replacePhiUsers(withOnlyPhiUse);
                      Phi trivialPhiBeforeLir = exit.getPhis().get(0);
                      assertTrue(trivialPhiBeforeLir.isTrivialPhi());

                      // Finalize the IR via LIR and rebuild it again.
                      Timing timing = Timing.empty();
                      DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
                      BytecodeMetadataProvider metadataProvider = BytecodeMetadataProvider.empty();
                      IRToLirFinalizer finalizer = new IRToLirFinalizer(appView, deadCodeRemover);
                      LirCode<Integer> lirCode =
                          finalizer.finalizeCode(code, metadataProvider, timing);
                      code.context().setCode(lirCode, appView);
                      IRCode irCode = code.context().buildIR(appView);
                      Phi trivialPhiAfterLir =
                          irCode.computeNormalExitBlocks().get(0).getPhis().get(0);
                      assertTrue(trivialPhiAfterLir.isTrivialPhi());
                    })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true");

    assertTrue(foundPhi.get());
  }

  static class TestClass {

    public static boolean foo(boolean v0) {
      boolean v1 = v0 ? true : false;
      boolean v2 = v1 ? true : false;
      boolean v3 = v0 ? v1 : v2;
      return v3;
    }

    public static void main(String[] args) {
      System.out.println(foo(args.length == 0));
    }
  }
}
