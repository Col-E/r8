// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JavaScriptScriptEngineTest extends ScriptEngineTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public JavaScriptScriptEngineTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static String EXPECTED_RHINO_OUTPUT = StringUtils.lines("Mozilla Rhino", "42");
  private static String EXPECTED_NASHORN_OUTPUT = StringUtils.lines("Oracle Nashorn", "42");

  @Test
  public void testD8() throws IOException, CompilationFailedException, ExecutionException {
    assumeTrue("Only run D8 for dex backend", parameters.isDexRuntime());
    testForD8()
        .addInnerClasses(JavaScriptScriptEngineTest.class)
        .setMinApi(parameters)
        .apply(this::addRhinoForAndroid)
        .compile()
        .run(parameters.getRuntime(), TestClassWithExplicitRhinoScriptEngineRegistration.class)
        .assertSuccessWithOutput(EXPECTED_RHINO_OUTPUT);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(JavaScriptScriptEngineTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .applyIf(
            parameters.isDexRuntime(),
            testBuilder -> {
              testBuilder.addOptionsModification(
                  options ->
                      options
                          .getOpenClosedInterfacesOptions()
                          .suppressAllOpenInterfacesDueToMissingClasses());
              addRhinoForAndroid(testBuilder);
              addKeepRulesForAndroidRhino(testBuilder);
              testBuilder.allowDiagnosticWarningMessages();
            })
        .compile()
        .applyIf(
            parameters.isDexRuntime(),
            result ->
                result.assertAllWarningMessagesMatch(
                    anyOf(
                        containsString("Missing class "),
                        containsString(
                            "required for default or static interface methods desugaring"),
                        allOf(
                            containsString("Unverifiable code in `"),
                            containsString("org.mozilla.javascript.tools.")),
                        equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))))
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            // TODO(b/227162584): Fails to find any engine on JDK17.
            parameters.isCfRuntime(CfVm.JDK17),
            r -> r.assertFailureWithErrorThatThrows(NullPointerException.class),
            r ->
                r.assertSuccessWithOutput(
                    parameters.isCfRuntime() ? EXPECTED_NASHORN_OUTPUT : EXPECTED_RHINO_OUTPUT));
  }

  static class TestClass {
    public final ScriptEngineManager manager = new ScriptEngineManager();

    public void run() throws Exception {
      ScriptEngine engine = manager.getEngineByMimeType("text/javascript");
      System.out.println(engine.getFactory().getEngineName());
      runJavaScript(engine);
    }

    public void runJavaScript(ScriptEngine engine) throws Exception {
      // Add a simple JavaScript function and call it.
      engine.eval("function add(x, y) { return x + y }");
      Object fortyTwo = engine.eval("add(40, 2)");
      // Rhino and Nashorn in JDK8 returns a Double. Nashorn in JDK9 and JDK11 returns an Integer.
      if (fortyTwo instanceof Double) {
        System.out.println(((Double) fortyTwo).intValue());
      } else if (fortyTwo instanceof Integer) {
        System.out.println(fortyTwo);
      } else {
        System.out.println("Unexpected");
      }
    }

    public static void main(String[] args) throws Exception {
      new TestClass().run();
    }
  }

  static class TestClassWithExplicitRhinoScriptEngineRegistration extends TestClass {

    public TestClassWithExplicitRhinoScriptEngineRegistration() throws Exception {
      // D8 does not handle META-INF/services, it just produces dex files, so register
      // the Rhino engine directly.
      manager.registerEngineMimeType(
          "text/javascript",
          (ScriptEngineFactory)
              (Class.forName("com.sun.script.javascript.RhinoScriptEngineFactory")
                  .getConstructor()
                  .newInstance()));
    }

    public static void main(String[] args) throws Exception {
      new TestClassWithExplicitRhinoScriptEngineRegistration().run();
    }
  }
}
