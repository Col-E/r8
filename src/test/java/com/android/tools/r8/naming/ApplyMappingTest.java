// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.InstructionSubject;
import com.android.tools.r8.utils.DexInspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApplyMappingTest {

  private static final String MAPPING = "test-mapping.txt";

  private static final Path MINIFICATION_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "minification" + FileUtils.JAR_EXTENSION);

  private static final Path NAMING001_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming001" + FileUtils.JAR_EXTENSION);

  private static final Path NAMING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming044" + FileUtils.JAR_EXTENSION);

  private static final Path APPLYMAPPING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "applymapping044" + FileUtils.JAR_EXTENSION);

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private Path out;

  @Before
  public void setup() throws IOException {
    out = temp.newFolder("outdex").toPath();
  }

  @Test
  @Ignore("b/71621001")
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

    DexInspector inspector = new DexInspector(instrApp);
    MethodSubject main = inspector.clazz("applymapping044.Main").method(DexInspector.MAIN);
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
    assertNotEquals("f", f.invokedMethod().name.toSourceString());
    // Skip AsubB#<init>
    iterator.next();
    // AsubB#f(A)
    InvokeInstructionSubject overloaded_f = iterator.next();
    DexMethod aSubB_f = overloaded_f.invokedMethod();
    DexType a2 = aSubB_f.proto.parameters.values[0];
    assertNotEquals("naming044.A", a2.toString());
    assertEquals("naming044.A", mapperFromApp.deobfuscateClassName(a2.toString()));
    assertNotEquals("f", overloaded_f.invokedMethod().name.toSourceString());
    // B#f == AsubB#f
    assertEquals(f.invokedMethod().name.toString(), aSubB_f.name.toString());
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
    DexInspector inspector = new DexInspector(outputApp);
    MethodSubject main = inspector.clazz("applymapping044.Main").method(DexInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    // B#m() -> y#n()
    InvokeInstructionSubject m = iterator.next();
    assertEquals("naming044.y", m.holder().toString());
    assertEquals("n", m.invokedMethod().name.toSourceString());
    // sub.SubB#n() -> z.y#m()
    InvokeInstructionSubject n = iterator.next();
    assertEquals("naming044.z.y", n.holder().toString());
    assertEquals("m", n.invokedMethod().name.toSourceString());
    // Skip A#<init>
    iterator.next();
    // Skip B#<init>
    iterator.next();
    // B#f(A) -> y#p(x)
    InvokeInstructionSubject f = iterator.next();
    DexType a1 = f.invokedMethod().proto.parameters.values[0];
    assertEquals("naming044.x", a1.toString());
    assertEquals("p", f.invokedMethod().name.toSourceString());
    // Skip AsubB#<init>
    iterator.next();
    // AsubB#f(A) -> AsubB#p(x)
    InvokeInstructionSubject overloaded_f = iterator.next();
    DexMethod aSubB_f = overloaded_f.invokedMethod();
    DexType a2 = aSubB_f.proto.parameters.values[0];
    assertEquals("naming044.x", a2.toString());
    assertEquals("p", aSubB_f.name.toSourceString());
    // B#f == AsubB#f
    assertEquals(f.invokedMethod().name.toString(), aSubB_f.name.toString());
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
    DexInspector inspector = new DexInspector(outputApp);
    MethodSubject main = inspector.clazz("naming001.D").method(DexInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    // mapping-105 simply includes: naming001.D#keep -> peek
    // naming001.E extends D, hence its keep() should be renamed to peek as well.
    // Skip E#<init>
    iterator.next();
    // E#keep() should be replaced with peek by applying the map.
    InvokeInstructionSubject m = iterator.next();
    assertEquals("peek", m.invokedMethod().name.toSourceString());
    // E could be renamed randomly, though.
    assertNotEquals("naming001.E", m.holder().toString());
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
      Path out, Path flag, Path mainApp, Path instrApp)
      throws CompilationException, IOException {
    return R8Command.builder()
        .addLibraryFiles(Paths.get(ToolHelper.getDefaultAndroidJar()), mainApp)
        .addProgramFiles(instrApp)
        .setOutput(out, OutputMode.DexIndexed)
        .addProguardConfigurationFiles(flag);
  }

  private R8Command.Builder getCommandForApps(
      Path out, Path flag, Path... jars)
      throws CompilationException, IOException {
    return R8Command.builder()
        .addLibraryFiles(Paths.get(ToolHelper.getDefaultAndroidJar()))
        .addProgramFiles(jars)
        .setOutput(out, OutputMode.DexIndexed)
        .addProguardConfigurationFiles(flag);
  }

  private static AndroidApp runR8(R8Command command)
      throws ProguardRuleParserException, ExecutionException, CompilationException, IOException {
    return ToolHelper.runR8(command, options -> {
      // Disable inlining to make this test not depend on inlining decisions.
      options.inlineAccessors = false;
    });
  }
}
