// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import java.util.List;
import org.apache.harmony.jpda.tests.framework.jdwp.Frame.Variable;
import org.apache.harmony.jpda.tests.framework.jdwp.VmMirror;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LocalVariableTableForParameterWithSignatureTest extends DebugTestBase {

  private final TestParameters parameters;
  private final boolean startBeforeDefault;

  @Parameterized.Parameters(name = "{0}, startBeforeDefault:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultCfRuntime()
            // VMs 4.0 and 4.4 hang on exit so skip testing on those. Their locals are the same as
            // VMs 5.1 and 6.0.
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withMinimumApiLevel()
            .build(),
        BooleanUtils.values());
  }

  public LocalVariableTableForParameterWithSignatureTest(
      TestParameters parameters, boolean startBeforeDefault) {
    this.parameters = parameters;
    this.startBeforeDefault = startBeforeDefault;
  }

  @Test
  public void test() throws Exception {
    // Only run one configuration in CF.
    assumeTrue(parameters.isDexRuntime() || startBeforeDefault);
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .applyIf(
            parameters.isDexRuntime(),
            b ->
                b.setMinApi(parameters)
                    .addOptionsModification(
                        o -> o.testing.emitDebugLocalStartBeforeDefaultEvent = startBeforeDefault))
        .compile()
        .inspect(this::checkDebugInfo)
        .apply(b -> runDebugger(b.debugConfig(parameters.getRuntime())));
  }

  private void checkDebugInfo(CodeInspector inspector) throws NoSuchMethodException {
    if (parameters.isCfRuntime()) {
      return;
    }
    MethodSubject method =
        inspector.method(TestClass.class.getMethod("fun", int.class, List.class));
    EventBasedDebugInfo debugInfo =
        method.getMethod().getCode().asDexCode().getDebugInfo().asEventBasedInfo();
    assertEquals("value", debugInfo.parameters[0].toString());
    assertNull(debugInfo.parameters[1]);
    assertEquals(2, debugInfo.parameters.length);
    Default defaultEvent = (Default) debugInfo.events[startBeforeDefault ? 1 : 0];
    StartLocal startEvent = (StartLocal) debugInfo.events[startBeforeDefault ? 0 : 1];
    assertEquals(inspector.getFactory().zeroChangeDefaultEvent, defaultEvent);
  }

  private void runDebugger(DebugTestConfig debugConfig) throws Throwable {
    runDebugTest(
        debugConfig,
        TestClass.class,
        breakpoint(Reference.methodFromMethod(TestClass.class.getMethod("main", String[].class))),
        run(),
        inspect(
            inspector -> {
              VmMirror mirror = inspector.getMirror();
              long classID = mirror.getClassID(inspector.getClassSignature());
              long methodID = mirror.getMethodID(classID, "fun");
              List<Variable> variableTable = mirror.getVariableTable(classID, methodID);
              boolean hasEmptyRange = false;
              boolean hasParamValue = false;
              boolean hasParamStringsWithSignature = false;
              for (Variable variable : variableTable) {
                if (variable.getLength() == 0) {
                  hasEmptyRange = true;
                } else if ("value".equals(variable.getName())) {
                  hasParamValue = true;
                } else if ("strings".equals(variable.getName())) {
                  assertEquals(
                      "Ljava/util/List<Ljava/lang/String;>;", variable.getGenericSignature());
                  hasParamStringsWithSignature = true;
                } else {
                  fail("Unexpected variable");
                }
              }
              assertTrue(hasParamValue);
              assertTrue(hasParamStringsWithSignature);
              if (parameters.isCfRuntime()
                  || parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V6_0_1)) {
                // CF runtimes and the old DEX runtimes report the correct local variable table.
                // The variable table should be just the two parameters.
                assertEquals(2, variableTable.size());
                assertFalse(hasEmptyRange);
              } else {
                // Newer ART runtimes report a variable with an empty range. That variable is the
                // parameter without a signature, e.g., List, and is ended immediately as a variable
                // is started that also includes the signature, e.g., List<String>.
                assertEquals(variableTable.toString(), 3, variableTable.size());
                assertTrue(hasEmptyRange);
              }
            }),
        run());
  }

  static class TestClass {

    public static void fun(int value, List<String> strings) {
      System.out.println("" + value + strings);
    }

    public static void main(String[] args) {
      fun(42, Arrays.asList(args));
    }
  }
}
