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
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
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
    return getTestParameters().withAllRuntimesAndApiLevels().build();
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
  public void testEscapeViaReturn() {
    buildAndCheckIR("escapeViaReturn", checkEscapingName(true, Instruction::isReturn));
  }

  @Test
  public void testEscapeViaThrow() {
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
                  StringOptimizerEscapeAnalysisConfigurationForTesting.getInstance());
          Set<Instruction> escapeRoutes = escapeAnalysis.computeEscapeRoutes(code, v);
          assertEquals(2, escapeRoutes.size());
          assertTrue(
              escapeRoutes.stream().allMatch(
                  instr ->
                      instr.isThrow()
                          || (instr.isInvokeDirect()
                              && invokesMethodWithName("<init>").test(instr))));
        });
  }

  @Test
  public void testEscapeViaStaticPut() {
    buildAndCheckIR("escapeViaStaticPut", checkEscapingName(true, Instruction::isStaticPut));
  }

  @Test
  public void testEscapeViaInstancePut_local() {
    buildAndCheckIR(
        "escapeViaInstancePut_local",
        checkEscapingName(true, invokesMethodWithName("namingInterfaceConsumer")));
  }

  @Test
  public void testEscapeViaArrayPut_local() {
    buildAndCheckIR(
        "escapeViaArrayPut_local",
        checkEscapingName(true, invokesMethodWithName("namingInterfacesConsumer")));
  }

  @Test
  public void testEscapeViaArrayPut_heap() {
    buildAndCheckIR(
        "escapeViaArrayPut_heap",
        checkEscapingName(
            true,
            i -> i.isArrayPut() || invokesMethodWithName("namingInterfacesConsumer").test(i)));
  }

  @Test
  public void testEscapeViaArrayPut_argument() {
    buildAndCheckIR(
        "escapeViaArrayPut_argument",
        checkEscapingName(
            true,
            i -> i.isArrayPut() || invokesMethodWithName("namingInterfacesConsumer").test(i)));
  }

  @Test
  public void testEscapeViaListPut() {
    buildAndCheckIR(
        "escapeViaListPut",
        checkEscapingName(true, invokesMethodWithName("add")));
  }

  @Test
  public void testEscapeViaListArgumentPut() {
    buildAndCheckIR(
        "escapeViaListArgumentPut",
        checkEscapingName(true, invokesMethodWithName("add")));
  }

  @Test
  public void testEscapeViaArrayGet() {
    buildAndCheckIR(
        "escapeViaArrayGet",
        checkEscapingName(true, invokesMethodWithName("namingInterfaceConsumer")));
  }

  @Test
  public void testHandlePhiAndAlias() {
    buildAndCheckIR(
        "handlePhiAndAlias", checkEscapingName(true, invokesMethodWithName("stringConsumer")));
  }

  @Test
  public void testToString() {
    buildAndCheckIR("toString", checkEscapingName(true, Instruction::isReturn));
  }

  @Test
  public void testEscapeViaRecursion() {
    buildAndCheckIR("escapeViaRecursion", checkEscapingName(true, Instruction::isReturn));
  }

  @Test
  public void testEscapeViaLoopAndBoxing() {
    buildAndCheckIR(
        "escapeViaLoopAndBoxing",
        checkEscapingName(
            true, instr -> instr.isReturn() || invokesMethodWithName("toString").test(instr)));
  }

  static class StringOptimizerEscapeAnalysisConfigurationForTesting
      implements EscapeAnalysisConfiguration {

    private static final StringOptimizerEscapeAnalysisConfigurationForTesting INSTANCE =
        new StringOptimizerEscapeAnalysisConfigurationForTesting();

    private StringOptimizerEscapeAnalysisConfigurationForTesting() {}

    static StringOptimizerEscapeAnalysisConfigurationForTesting getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isLegitimateEscapeRoute(
        AppView<?> appView,
        EscapeAnalysis escapeAnalysis,
        Instruction escapeRoute,
        ProgramMethod context) {
      if (escapeRoute.isReturn() || escapeRoute.isThrow() || escapeRoute.isStaticPut()) {
        return false;
      }
      if (escapeRoute.isInvokeMethod()) {
        DexMethod invokedMethod = escapeRoute.asInvokeMethod().getInvokedMethod();
        // Heuristic: if the call target has the same method name, it could be still local.
        if (invokedMethod.name == context.getReference().name) {
          return true;
        }
        // It's not legitimate during testing, except for recursion calls.
        return false;
      }
      if (escapeRoute.isArrayPut()) {
        Value array = escapeRoute.asArrayPut().array().getAliasedValue();
        return !array.isPhi() && array.definition.isCreatingArray();
      }
      if (escapeRoute.isInstancePut()) {
        Value instance = escapeRoute.asInstancePut().object().getAliasedValue();
        return !instance.isPhi() && instance.definition.isNewInstance();
      }
      // All other cases are not legitimate.
      return false;
    }
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
              appView, StringOptimizerEscapeAnalysisConfigurationForTesting.getInstance());
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
    private static NamingInterface[] ARRAY = new NamingInterface[1];

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

    public static void escapeViaInstancePut_local() {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      Helper.namingInterfaceConsumer(instance);
    }

    public static void escapeViaArrayPut_local() {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      NamingInterface[] array = new NamingInterface[1];
      array[0] = instance;
      Helper.namingInterfacesConsumer(array);
    }

    public static void escapeViaArrayPut_heap() {
      TestClass instance = new TestClass();
      instance.id = instance.getClass().getSimpleName();
      ARRAY[0] = instance;
      Helper.namingInterfacesConsumer(ARRAY);
    }

    public static void escapeViaArrayPut_argument(NamingInterface[] array) {
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
