// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping.sourcelibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.NewInstanceInstructionSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingTest extends TestBase {

  private static final Path NAMING001_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming001" + FileUtils.JAR_EXTENSION);

  private static final Path NAMING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming044" + FileUtils.JAR_EXTENSION);

  private static final Path APPLYMAPPING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "applymapping044" + FileUtils.JAR_EXTENSION);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void test044_apply() throws Exception {
    // Make sure the given proguard map is indeed applied.
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(APPLYMAPPING044_JAR)
            .addClasspathFiles(NAMING044_JAR)
            .addKeepRuleFiles(
                Paths.get(
                    ToolHelper.EXAMPLES_DIR, "applymapping044", "keep-rules-apply-mapping.txt"))
            .addDontObfuscate()
            .setMinApi(parameters)
            .compile()
            .inspector();
    MethodSubject main = inspector.clazz("applymapping044.Main").method(CodeInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    // B#m() -> y#n()
    InvokeInstructionSubject m = iterator.next();
    assertEquals("naming044.y", m.holder().toString());
    assertEquals("n", m.invokedMethod().name.toString());
    // sub.SubB#n() -> z.y#m()
    InvokeInstructionSubject n = iterator.next();
    assertEquals("naming044.z.y", n.holder().toString());
    assertEquals("m", n.invokedMethod().name.toString());
    // Skip A#<init>
    iterator.next();
    // Skip B#<init>
    iterator.next();
    // B#f(A) -> y#p(x)
    InvokeInstructionSubject f = iterator.next();
    DexType a1 = f.invokedMethod().proto.parameters.values[0];
    assertEquals("naming044.x", a1.toString());
    assertEquals("p", f.invokedMethod().name.toString());
    // Skip AsubB#<init>
    iterator.next();
    // AsubB#f(A) -> AsubB#p(x)
    InvokeInstructionSubject overloaded_f = iterator.next();
    DexMethod aSubB_f = overloaded_f.invokedMethod();
    DexType a2 = aSubB_f.proto.parameters.values[0];
    assertEquals("naming044.x", a2.toString());
    assertEquals("p", aSubB_f.name.toString());
    // B#f == AsubB#f
    assertEquals(f.invokedMethod().name.toString(), aSubB_f.name.toString());
    // sub.SubB#<init>(int) -> z.y<init>(int)
    InvokeInstructionSubject subBinit = iterator.next();
    assertEquals("naming044.z.y", subBinit.holder().toString());
    // sub.SubB#f(A) -> SubB#p(x)
    InvokeInstructionSubject original_f = iterator.next();
    DexMethod subB_f = original_f.invokedMethod();
    DexType a3 = subB_f.proto.parameters.values[0];
    assertEquals(a2, a3);
    assertEquals("p", original_f.invokedMethod().name.toString());
  }

  @Test
  public void test_naming001_rule105() throws Exception {
    // keep rules to reserve D and E, along with a proguard map.
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(NAMING001_JAR)
            .addKeepRuleFiles(Paths.get(ToolHelper.EXAMPLES_DIR, "naming001", "keep-rules-105.txt"))
            .addDontObfuscate()
            .setMinApi(parameters)
            .compile()
            .inspector();

    // Make sure the given proguard map is indeed applied.
    ClassSubject classD = inspector.clazz("naming001.D");
    assertThat(classD, isPresent());
    // D must not be renamed
    assertEquals("naming001.D", classD.getFinalName());
    MethodSubject main = classD.method(CodeInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    // mapping-105 simply includes: naming001.D#keep -> peek
    // naming001.E extends D, hence its keep() should be renamed to peek as well.
    // Check E#<init> is not renamed.
    InvokeInstructionSubject init = iterator.next();
    assertEquals("Lnaming001/E;-><init>()V", init.invokedMethod().toSmaliString());
    // E#keep() should be replaced with peek by applying the map.
    InvokeInstructionSubject m = iterator.next();
    assertEquals("peek", m.invokedMethod().name.toSourceString());
  }

  @Test
  public void test_naming001_rule106() throws Exception {
    // keep rules just to rename E
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(NAMING001_JAR)
            .addKeepRuleFiles(Paths.get(ToolHelper.EXAMPLES_DIR, "naming001", "keep-rules-106.txt"))
            .noTreeShaking()
            .setMinApi(parameters)
            .compile()
            .inspector();

    // Make sure the given proguard map is indeed applied.
    MethodSubject main = inspector.clazz("naming001.D").mainMethod();
    assertThat(main, isPresent());

    Iterator<InstructionSubject> iterator = main.iterateInstructions();
    // naming001.E is renamed to a.a, so first instruction must be: new-instance La/a;
    NewInstanceInstructionSubject newInstance = null;
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (instruction.isNewInstance()) {
        newInstance = (NewInstanceInstructionSubject) instruction;
        break;
      }
    }
    assertNotNull(newInstance);
    assertEquals( "La/a;", newInstance.getType().toSmaliString());
  }
}
