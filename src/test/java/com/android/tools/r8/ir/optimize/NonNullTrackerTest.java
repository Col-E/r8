// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.optimize.nonnull.FieldAccessTest;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterArrayAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterFieldAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterInvoke;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterNullCheck;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonNullTrackerTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public NonNullTrackerTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private void buildAndTest(
      List<Class<?>> classes,
      Class<?> testClass,
      MethodSignature signature,
      int expectedNumberOfNonNull,
      Consumer<IRCode> testAugmentedIRCode)
      throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(classes).addLibraryFile(getMostRecentAndroidJar()).build());
    CodeInspector codeInspector = new CodeInspector(appView.appInfo().app());
    MethodSubject fooSubject = codeInspector.clazz(testClass.getName()).method(signature);
    IRCode code = fooSubject.buildIR();
    checkCountOfNonNull(code, 0);

    AssumeInserter assumeInserter = new AssumeInserter(appView);

    assumeInserter.insertAssumeInstructions(code, Timing.empty());
    assertTrue(code.isConsistentSSA(appView));
    checkCountOfNonNull(code, expectedNumberOfNonNull);

    if (testAugmentedIRCode != null) {
      testAugmentedIRCode.accept(code);
    }

    CodeRewriter.removeAssumeInstructions(appView, code);
    code.removeRedundantBlocks();
    assertTrue(code.isConsistentSSA(appView));
    checkCountOfNonNull(code, 0);
  }

  private static void checkCountOfNonNull(IRCode code, int expectedOccurrences) {
    int count = 0;
    Instruction prev = null, curr = null;
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      prev = curr != null && !curr.isGoto() ? curr : prev;
      curr = it.next();
      if (curr.isAssumeWithNonNullAssumption()) {
        // Make sure non-null is added to the right place.
        assertTrue(prev == null
            || prev.throwsOnNullInput()
            || (prev.isIf() && prev.asIf().isZeroTest())
            || !curr.getBlock().getPredecessors().contains(prev.getBlock()));
        // Make sure non-null is used or inserted for arguments.
        assertTrue(curr.outValue().numberOfAllUsers() > 0 || curr.asAssume().src().isArgument());
        count++;
      }
    }
    assertEquals(expectedOccurrences, count);
  }

  private void checkInvokeGetsNonNullReceiver(IRCode code) {
    checkInvokeReceiver(code, true);
  }

  private void checkInvokeGetsNullReceiver(IRCode code) {
    checkInvokeReceiver(code, false);
  }

  private void checkInvokeReceiver(IRCode code, boolean isNotNull) {
    InstructionIterator it = code.instructionIterator();
    boolean metInvokeWithReceiver = false;
    while (it.hasNext()) {
      Instruction instruction = it.nextUntil(Instruction::isInvokeMethodWithReceiver);
      if (instruction == null) {
        break;
      }
      InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
      if (invoke.isInvokeDirect()
          || !invoke.getInvokedMethod().name.toString().contains("hashCode")) {
        continue;
      }
      metInvokeWithReceiver = true;
      if (isNotNull) {
        assertTrue(invoke.getReceiver().isNeverNull()
            || invoke.getReceiver().definition.isArgument());
      } else {
        assertFalse(invoke.getReceiver().isNeverNull());
      }
    }
    assertTrue(metInvokeWithReceiver);
  }

  @Test
  public void nonNullAfterSafeInvokes() throws Exception {
    MethodSignature foo =
        new MethodSignature("foo", "int", new String[]{"java.lang.String"});
    buildAndTest(
        ImmutableList.of(NonNullAfterInvoke.class),
        NonNullAfterInvoke.class,
        foo,
        1,
        this::checkInvokeGetsNonNullReceiver);
    MethodSignature bar =
        new MethodSignature("bar", "int", new String[]{"java.lang.String"});
    buildAndTest(
        ImmutableList.of(NonNullAfterInvoke.class),
        NonNullAfterInvoke.class,
        bar,
        2,
        this::checkInvokeGetsNullReceiver);
  }

  @Test
  public void nonNullAfterSafeArrayAccess() throws Exception {
    MethodSignature foo =
        new MethodSignature("foo", "int", new String[]{"java.lang.String[]"});
    buildAndTest(
        ImmutableList.of(NonNullAfterArrayAccess.class),
        NonNullAfterArrayAccess.class,
        foo,
        1,
        null);
  }

  @Test
  public void nonNullAfterSafeArrayLength() throws Exception {
    MethodSignature signature =
        new MethodSignature("arrayLength", "int", new String[]{"java.lang.String[]"});
    buildAndTest(
        ImmutableList.of(NonNullAfterArrayAccess.class),
        NonNullAfterArrayAccess.class,
        signature,
        1,
        null);
  }

  @Test
  public void nonNullAfterSafeFieldAccess() throws Exception {
    MethodSignature foo = new MethodSignature("foo", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    buildAndTest(
        ImmutableList.of(FieldAccessTest.class, NonNullAfterFieldAccess.class),
        NonNullAfterFieldAccess.class,
        foo,
        1,
        null);
  }

  @Test
  public void avoidRedundantNonNull() throws Exception {
    MethodSignature signature = new MethodSignature("foo2", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    buildAndTest(
        ImmutableList.of(FieldAccessTest.class, NonNullAfterFieldAccess.class),
        NonNullAfterFieldAccess.class,
        signature,
        1,
        code -> {
          // There are two InstancePut instructions of interest.
          int count = 0;
          InstructionIterator it = code.instructionIterator();
          while (it.hasNext()) {
            Instruction instruction = it.nextUntil(Instruction::isInstancePut);
            if (instruction == null) {
              break;
            }
            InstancePut iput = instruction.asInstancePut();
            if (count == 0) {
              // First one in the very first line: its value should not be replaced by NonNullMarker
              // because this instruction will happen _before_ non-null.
              assertFalse(iput.value().definition.isAssumeWithNonNullAssumption());
            } else if (count == 1) {
              // Second one after a safe invocation, which should use the value added by
              // NonNullMarker.
              assertTrue(iput.object().definition.isAssumeWithNonNullAssumption());
            }
            count++;
          }
          assertEquals(2, count);
        });
  }

  @Test
  public void nonNullAfterNullCheck() throws Exception {
    MethodSignature foo =
        new MethodSignature("foo", "int", new String[]{"java.lang.String"});
    buildAndTest(
        ImmutableList.of(NonNullAfterNullCheck.class),
        NonNullAfterNullCheck.class,
        foo,
        1,
        this::checkInvokeGetsNonNullReceiver);
    MethodSignature bar =
        new MethodSignature("bar", "int", new String[]{"java.lang.String"});
    buildAndTest(
        ImmutableList.of(NonNullAfterNullCheck.class),
        NonNullAfterNullCheck.class,
        bar,
        1,
        this::checkInvokeGetsNonNullReceiver);
    MethodSignature baz =
        new MethodSignature("baz", "int", new String[]{"java.lang.String"});
    buildAndTest(
        ImmutableList.of(NonNullAfterNullCheck.class),
        NonNullAfterNullCheck.class,
        baz,
        2,
        this::checkInvokeGetsNullReceiver);
  }
}
