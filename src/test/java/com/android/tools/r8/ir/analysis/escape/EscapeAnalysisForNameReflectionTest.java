// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.escape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.string.StringOptimizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EscapeAnalysisForNameReflectionTest extends AnalysisTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public EscapeAnalysisForNameReflectionTest(TestParameters parameters) throws Exception {
    super(
        parameters,
        TestClass.class.getTypeName(), TestClass.class, Helper.class, NamingInterface.class);
  }

  private static Predicate<Instruction> invokesMethodWithName(String name) {
    return instruction ->
        instruction.isInvokeMethod()
            && instruction.asInvokeMethod().getInvokedMethod().name.toString().equals(name);
  }

  @Test
  public void testEscapeViaReturn() throws Exception {
    buildAndCheckIR("escapeViaReturn", checkEscapingName(true, Instruction::isReturn));
  }

  @Test
  public void testEscapeViaThrow() throws Exception {
    buildAndCheckIR(
        "escapeViaThrow",
        code -> {
          NewInstance e = getMatchingInstruction(code, Instruction::isNewInstance);
          assertNotNull(e);
          Value v = e.outValue();
          assertNotNull(v);
          EscapeAnalysis escapeAnalysis =
              new EscapeAnalysis(
                  appView,
                  StringOptimizer.StringOptimizerEscapeAnalysisConfiguration.getInstance());
          Set<Instruction> escapeRoutes = escapeAnalysis.computeEscapeRoutes(code, v);
          assertEquals(1, escapeRoutes.size());
          assertTrue(
              escapeRoutes.stream()
                  .allMatch(
                      instr ->
                          instr.isThrow()
                              || (instr.isInvokeDirect()
                                  && instr
                                      .asInvokeDirect()
                                      .getInvokedMethod()
                                      .name
                                      .toString()
                                      .equals("<init>"))));
        });
  }

  @Test
  public void testEscapeViaStaticPut() throws Exception {
    buildAndCheckIR("escapeViaStaticPut", checkEscapingName(true, Instruction::isStaticPut));
  }

  @Test
  public void testEscapeViaInstancePut() throws Exception {
    buildAndCheckIR(
        "escapeViaInstancePut",
        checkEscapingName(true, invokesMethodWithName("namingInterfaceConsumer")));
  }

  @Test
  public void testEscapeViaArrayPut() throws Exception {
    buildAndCheckIR(
        "escapeViaArrayPut",
        checkEscapingName(true, invokesMethodWithName("namingInterfacesConsumer")));
  }

  @Test
  public void testEscapeViaArrayArgumentPut() throws Exception {
    buildAndCheckIR("escapeViaArrayArgumentPut", checkEscapingName(true, Instruction::isArrayPut));
  }

  @Test
  public void testEscapeViaListPut() throws Exception {
    buildAndCheckIR(
        "escapeViaListPut",
        checkEscapingName(
            false,
            instr -> {
              throw new Unreachable();
            }));
  }

  @Test
  public void testEscapeViaListArgumentPut() throws Exception {
    buildAndCheckIR(
        "escapeViaListArgumentPut",
        checkEscapingName(
            false,
            instr -> {
              throw new Unreachable();
            }));
  }

  @Test
  public void testEscapeViaArrayGet() throws Exception {
    buildAndCheckIR(
        "escapeViaArrayGet",
        checkEscapingName(true, invokesMethodWithName("namingInterfaceConsumer")));
  }

  @Test
  public void testHandlePhiAndAlias() throws Exception {
    buildAndCheckIR(
        "handlePhiAndAlias", checkEscapingName(true, invokesMethodWithName("stringConsumer")));
  }

  @Test
  public void testToString() throws Exception {
    buildAndCheckIR("toString", checkEscapingName(true, Instruction::isReturn));
  }

  @Test
  public void testEscapeViaRecursion() throws Exception {
    buildAndCheckIR("escapeViaRecursion", checkEscapingName(true, Instruction::isReturn));
  }

  @Test
  public void testEscapeViaLoopAndBoxing() throws Exception {
    buildAndCheckIR(
        "escapeViaLoopAndBoxing",
        checkEscapingName(
            true, instr -> instr.isReturn() || invokesMethodWithName("toString").test(instr)));
  }

  private Consumer<IRCode> checkEscapingName(
      boolean expectedHeuristicResult, Predicate<Instruction> instructionTester) {
    assert instructionTester != null;
    return code -> {
      InvokeVirtual simpleNameCall = getSimpleNameCall(code);
      assertNotNull(simpleNameCall);
      Value v = simpleNameCall.outValue();
      assertNotNull(v);
      EscapeAnalysis escapeAnalysis =
          new EscapeAnalysis(
              appView, StringOptimizer.StringOptimizerEscapeAnalysisConfiguration.getInstance());
      Set<Instruction> escapeRoutes = escapeAnalysis.computeEscapeRoutes(code, v);
      assertNotEquals(expectedHeuristicResult, escapeRoutes.isEmpty());
      assertTrue(escapeRoutes.stream().allMatch(instructionTester));
    };
  }

  private static InvokeVirtual getSimpleNameCall(IRCode code) {
    return getMatchingInstruction(code, instruction -> {
      if (instruction.isInvokeVirtual()) {
        DexMethod invokedMethod = instruction.asInvokeVirtual().getInvokedMethod();
        return invokedMethod.holder.toDescriptorString().equals("Ljava/lang/Class;")
            && invokedMethod.name.toString().equals("getSimpleName");
      }
      return false;
    }).asInvokeVirtual();
  }

  static class Helper {
    static String toString(String x, String y) {
      return x + y;
    }

    static void stringConsumer(String str) {
      System.out.println(str);
    }

    static void namingInterfaceConsumer(NamingInterface itf) {
      System.out.println(itf.getName());
    }

    static void namingInterfacesConsumer(NamingInterface[] interfaces) {
      for (NamingInterface itf : interfaces) {
        System.out.println(itf.getName());
      }
    }
  }

  interface NamingInterface {
    default String getName() {
      return getClass().getName();
    }
  }

  static class TestClass implements NamingInterface {
    static String tag;
    String id;

    TestClass() {
      this(new Random().nextInt());
    }

    TestClass(int id) {
      this(String.valueOf(id));
    }

    TestClass(String id) {
      this.id = id;
    }

    @Override
    public String getName() {
      return getClass().getCanonicalName();
    }

    @Override
    public String toString() {
      String name = getClass().getSimpleName();
      return Helper.toString(name, id);
    }

    public static String escapeViaReturn() {
      return TestClass.class.getSimpleName();
    }

    public static void escapeViaThrow() {
      RuntimeException e = new RuntimeException();
      throw e;
    }

    public static void escapeViaStaticPut() {
      tag = TestClass.class.getSimpleName();
    }

    public static void escapeViaInstancePut() {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      Helper.namingInterfaceConsumer(instance);
    }

    public static void escapeViaArrayPut() {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      NamingInterface[] array = new NamingInterface[1];
      array[0] = instance;
      Helper.namingInterfacesConsumer(array);
    }

    public static void escapeViaArrayArgumentPut(NamingInterface[] array) {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      array[0] = instance;
    }

    public static void escapeViaListPut() {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      List<NamingInterface> list = new ArrayList<>();
      list.add(instance);
      Helper.namingInterfacesConsumer(list.toArray(new NamingInterface[0]));
    }

    public static void escapeViaListArgumentPut(List<NamingInterface> list) {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      list.add(instance);
    }

    public static void escapeViaArrayGet(boolean flag) {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      NamingInterface[] array = new NamingInterface[2];
      array[0] = instance;
      array[1] = null;
      // At compile-time, we don't know which one will be taken.
      // So, conservatively, we should consider that name can be propagated.
      NamingInterface couldBeNull = flag ? array[0] : array[1];
      Helper.namingInterfaceConsumer(couldBeNull);
    }

    public static void handlePhiAndAlias(String input) {
      TestClass instance = new TestClass();
      String name = System.currentTimeMillis() > 0 ? instance.getClass().getSimpleName() : input;
      String alias = name;
      Helper.stringConsumer(alias);
    }

    public static String escapeViaRecursion(String arg, boolean escape) {
      if (escape) {
        return arg;
      } else {
        String name = TestClass.class.getSimpleName();
        return escapeViaRecursion(name, true);
      }
    }

    public static TestClass escapeViaLoopAndBoxing(String arg) {
      TestClass box = null;
      while (arg != null) {
        box = new TestClass(arg);
        String name = TestClass.class.getSimpleName();
        box.id = name;
        arg = box.toString();
      }
      return box;
    }
  }
}
