// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.NewInstanceInstructionSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingTest extends TestBase {

  private static final String MAPPING = "test-mapping.txt";

  private static final Path MINIFICATION_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "minification" + FileUtils.JAR_EXTENSION);

  private static final Path NAMING001_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming001" + FileUtils.JAR_EXTENSION);

  private static final Path NAMING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming044" + FileUtils.JAR_EXTENSION);

  private static final Path APPLYMAPPING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "applymapping044" + FileUtils.JAR_EXTENSION);

  private Path out;

  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public ApplyMappingTest(Backend backend) {
    this.backend = backend;
  }

  @Before
  public void setup() throws IOException {
    out = temp.newFolder("out").toPath();
  }

  @Test
  public void test044_obfuscate_and_apply() throws Exception {
    // keep rules that allow obfuscations while keeping everything.
    Path flagForObfuscation =
        Paths.get(ToolHelper.EXAMPLES_DIR, "naming044", "keep-rules-005.txt");
    Path proguardMap = out.resolve(MAPPING);
    class ProguardMapConsumer implements StringConsumer {
      String map;

      @Override
      public void accept(String string, DiagnosticsHandler handler) {
        map = string;
      }
    }
    ProguardMapConsumer mapConsumer = new ProguardMapConsumer();
    runR8(
        ToolHelper.addProguardConfigurationConsumer(
                getCommandForApps(out, flagForObfuscation, NAMING044_JAR)
                    .setProguardMapConsumer(mapConsumer),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(proguardMap);
                })
            .build());

    // Obviously, dumped map and resource inside the app should be *identical*.
    ClassNameMapper mapperFromFile = ClassNameMapper.mapperFromFile(proguardMap);
    ClassNameMapper mapperFromApp = ClassNameMapper.mapperFromString(mapConsumer.map);
    assertEquals(mapperFromFile, mapperFromApp);

    Path instrOut = temp.newFolder("instr").toPath();
    Path flag = Paths.get(ToolHelper.EXAMPLES_DIR, "applymapping044", "keep-rules.txt");
    AndroidApp instrApp =
        runR8(
            ToolHelper.addProguardConfigurationConsumer(
                    getCommandForInstrumentation(instrOut, flag, NAMING044_JAR, APPLYMAPPING044_JAR)
                        .setDisableMinification(true),
                    pgConfig -> pgConfig.setApplyMappingFile(proguardMap))
                .build());

    CodeInspector inspector = createDexInspector(instrApp);
    MethodSubject main = inspector.clazz("applymapping044.Main").method(CodeInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    // B#m()
    String b = iterator.next().holder().toString();
    assertEquals("naming044.B", mapperFromApp.deobfuscateClassName(b));
    // sub.SubB#n()
    String subB = iterator.next().holder().toString();
    assertEquals("naming044.sub.SubB", mapperFromApp.deobfuscateClassName(subB));
    // Skip A#<init>
    iterator.next();
    // Skip B#<init>
    iterator.next();
    // B#f(A)
    InvokeInstructionSubject f = iterator.next();
    DexType a1 = f.invokedMethod().proto.parameters.values[0];
    assertNotEquals("naming044.A", a1.toString());
    assertEquals("naming044.A", mapperFromApp.deobfuscateClassName(a1.toString()));
    assertNotEquals("f", f.invokedMethod().name.toString());
    // Skip AsubB#<init>
    iterator.next();
    // AsubB#f(A)
    InvokeInstructionSubject overloaded_f = iterator.next();
    DexMethod aSubB_f = overloaded_f.invokedMethod();
    DexType a2 = aSubB_f.proto.parameters.values[0];
    assertNotEquals("naming044.A", a2.toString());
    assertEquals("naming044.A", mapperFromApp.deobfuscateClassName(a2.toString()));
    assertNotEquals("f", aSubB_f.name.toString());
    // B#f == AsubB#f
    assertEquals(f.invokedMethod().name.toString(), aSubB_f.name.toString());
    // sub.SubB#<init>(int)
    InvokeInstructionSubject subBinit = iterator.next();
    assertNotEquals("naming044.sub.SubB", subBinit.holder().toString());
    assertEquals("naming044.sub.SubB",
        mapperFromApp.deobfuscateClassName(subBinit.holder().toString()));
    // sub.SubB#f(A)
    InvokeInstructionSubject original_f = iterator.next();
    DexMethod subB_f = original_f.invokedMethod();
    DexType a3 = subB_f.proto.parameters.values[0];
    assertEquals(a2, a3);
    assertNotEquals("f", original_f.invokedMethod().name.toString());
  }

  @Test
  public void test044_apply() throws Exception {
    Path flag =
        Paths.get(ToolHelper.EXAMPLES_DIR, "applymapping044", "keep-rules-apply-mapping.txt");
    AndroidApp outputApp =
        runR8(
            getCommandForInstrumentation(out, flag, NAMING044_JAR, APPLYMAPPING044_JAR)
                .setDisableMinification(true)
                .build());

    // Make sure the given proguard map is indeed applied.
    CodeInspector inspector = createDexInspector(outputApp);
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

  private static CodeInspector createDexInspector(AndroidApp outputApp)
      throws IOException, ExecutionException {
    return new CodeInspector(outputApp);
  }

  @Test
  public void test_naming001_rule105() throws Exception {
    // keep rules to reserve D and E, along with a proguard map.
    Path flag = Paths.get(ToolHelper.EXAMPLES_DIR, "naming001", "keep-rules-105.txt");
    Path proguardMap = out.resolve(MAPPING);
    AndroidApp outputApp =
        runR8(
            ToolHelper.addProguardConfigurationConsumer(
                    getCommandForApps(out, flag, NAMING001_JAR).setDisableMinification(true),
                    pgConfig -> {
                      pgConfig.setPrintMapping(true);
                      pgConfig.setPrintMappingFile(proguardMap);
                    })
                .build());

    // Make sure the given proguard map is indeed applied.
    CodeInspector inspector = createDexInspector(outputApp);
    MethodSubject main = inspector.clazz("naming001.D").method(CodeInspector.MAIN);
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
    // D must not be renamed
    assertEquals("naming001.D", m.holder().toString());
  }

  @Test
  public void test_naming001_rule106() throws Exception {
    // keep rules just to rename E
    Path flag = Paths.get(ToolHelper.EXAMPLES_DIR, "naming001", "keep-rules-106.txt");
    Path proguardMap = out.resolve(MAPPING);
    AndroidApp outputApp =
        runR8(
            ToolHelper.addProguardConfigurationConsumer(
                getCommandForApps(out, flag, NAMING001_JAR).setDisableMinification(true),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(proguardMap);
                })
                .build());

    // Make sure the given proguard map is indeed applied.
    CodeInspector inspector = createDexInspector(outputApp);
    MethodSubject main = inspector.clazz("naming001.D").method(CodeInspector.MAIN);

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

  @Test
  public void test_minification_conflict_mapping() throws Exception {
    Path flag = Paths.get(
        ToolHelper.EXAMPLES_DIR, "minification", "keep-rules-apply-conflict-mapping.txt");
    try {
      runR8(getCommandForApps(out, flag, MINIFICATION_JAR).build());
      fail("Expect to detect renaming conflict");
    } catch (ProguardMapError e) {
      assertTrue(e.getMessage().contains("functionFromIntToInt"));
    }
  }

  private R8Command.Builder getCommandForInstrumentation(
      Path out, Path flag, Path mainApp, Path instrApp) throws IOException {
    return R8Command.builder()
        .addLibraryFiles(runtimeJar(backend), mainApp)
        .addProgramFiles(instrApp)
        .setOutput(out, outputMode(backend))
        .addProguardConfigurationFiles(flag);
  }

  private R8Command.Builder getCommandForApps(Path out, Path flag, Path... jars)
      throws IOException {
    return R8Command.builder()
        .addLibraryFiles(runtimeJar(backend))
        .addProgramFiles(jars)
        .setOutput(out, outputMode(backend))
        .addProguardConfigurationFiles(flag);
  }

  private static AndroidApp runR8(R8Command command)
      throws ProguardRuleParserException, ExecutionException, IOException {
    return ToolHelper.runR8(
        command,
        options -> {
          // Disable inlining to make this test not depend on inlining decisions.
          options.enableInlining = false;
        });
  }
}
