// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.OriginMatcher.hasParent;
import static com.android.tools.r8.utils.DescriptorUtils.JAVA_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JacocoConstantDynamicTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean useConstantDynamic;

  @Parameters(name = "{0}, useConstantDynamic: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public static JacocoClasses testClassesNoConstantDynamic;
  public static JacocoClasses testClassesConstantDynamic;

  public JacocoClasses testClasses;

  private static final String MAIN_CLASS = TestRunner.class.getTypeName();
  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @BeforeClass
  public static void setUpInput() throws IOException {
    testClassesNoConstantDynamic = testClasses(getStaticTemp(), CfVersion.V1_8);
    testClassesConstantDynamic = testClasses(getStaticTemp(), CfVersion.V11);
  }

  @Before
  public void setUp() throws IOException {
    testClasses = useConstantDynamic ? testClassesConstantDynamic : testClassesNoConstantDynamic;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(
        parameters.isCfRuntime()
            && (parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)
                || !useConstantDynamic));

    // Run non-instrumented code.
    testForRuntime(parameters)
        .addProgramFiles(testClasses.getOriginal())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);

    // Run instrumented code without an agent.
    testForRuntime(parameters)
        .addProgramFiles(testClasses.getInstrumented())
        .addProgramFiles(ToolHelper.JACOCO_AGENT)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);

    // Run non-instrumented code with an agent causing on the fly instrumentation on the JVM.
    Path output = temp.newFolder().toPath();
    Path agentOutputOnTheFly = output.resolve("on-the-fly");
    testForJvm()
        .addProgramFiles(testClasses.getOriginal())
        .enableJaCoCoAgent(ToolHelper.JACOCO_AGENT, agentOutputOnTheFly)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    List<String> onTheFlyReport = testClasses.generateReport(agentOutputOnTheFly);
    assertEquals(2, onTheFlyReport.size());

    // Run the instrumented code with offline instrumentation turned on.
    Path agentOutputOffline = output.resolve("offline");
    testForJvm()
        .addProgramFiles(testClasses.getInstrumented())
        .enableJaCoCoAgentForOfflineInstrumentedCode(ToolHelper.JACOCO_AGENT, agentOutputOffline)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    List<String> offlineReport = testClasses.generateReport(agentOutputOffline);
    assertEquals(onTheFlyReport, offlineReport);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    if (!useConstantDynamic) {
      Path output = temp.newFolder().toPath();
      Path agentOutput = output.resolve("jacoco.exec");
      testForD8(parameters.getBackend())
          .addProgramFiles(testClasses.getInstrumented())
          .addProgramFiles(ToolHelper.JACOCO_AGENT)
          .setMinApi(parameters.getApiLevel())
          .compile()
          .runWithJaCoCo(agentOutput, parameters.getRuntime(), MAIN_CLASS)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      // TODO(sgjesse): Need to figure out why there is no instrumentation output for newer VMs.
      if (parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
        List<String> report = testClasses.generateReport(agentOutput);
        assertEquals(2, report.size());
      } else {
        assertFalse(Files.exists(agentOutput));
      }
    } else {
      assertThrows(
          CompilationFailedException.class,
          () -> {
            ArchiveResourceProvider provider =
                ArchiveResourceProvider.fromArchive(testClasses.getInstrumented(), true);
            testForD8(parameters.getBackend())
                .addProgramResourceProviders(provider)
                .addProgramFiles(ToolHelper.JACOCO_AGENT)
                .setMinApi(parameters.getApiLevel())
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      // Check that the error is reported as an error to the diagnostics handler.
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorsMatch(
                          allOf(
                              diagnosticMessage(containsString("Unsupported dynamic constant")),
                              diagnosticOrigin(hasParent(provider.getOrigin()))));
                    });
          });
    }
  }

  private static JacocoClasses testClasses(TemporaryFolder temp, CfVersion version)
      throws IOException {
    return new JacocoClasses(
        transformer(TestRunner.class)
            .setVersion(version) /*.setClassDescriptor("LTestRunner;")*/
            .transform(),
        temp);
  }

  // Two sets of class files with and without JaCoCo off line instrumentation.
  private static class JacocoClasses {
    private final Path dir;

    private final Path originalJar;
    private final Path instrumentedJar;

    // Create JacocoClasses with just one class provided as bytes.
    private JacocoClasses(byte[] clazz, TemporaryFolder temp) throws IOException {
      dir = temp.newFolder().toPath();

      // Write the class to a .class file with package sub-directories.
      String typeName = extractClassName(clazz);
      int lastDotIndex = typeName.lastIndexOf('.');
      String pkg = typeName.substring(0, lastDotIndex);
      String baseFileName = typeName.substring(lastDotIndex + 1) + CLASS_EXTENSION;
      Path original = dir.resolve("original");
      Files.createDirectories(original);
      Path packageDir = original.resolve(pkg.replace(JAVA_PACKAGE_SEPARATOR, File.separatorChar));
      Files.createDirectories(packageDir);
      Path classFile = packageDir.resolve(baseFileName);
      Files.write(classFile, clazz);

      // Run offline instrumentation.
      Path instrumented = dir.resolve("instrumented");
      Files.createDirectories(instrumented);
      runJacocoInstrumentation(original, instrumented);
      originalJar = dir.resolve("original" + JAR_EXTENSION);
      ZipUtils.zip(originalJar, original);
      instrumentedJar = dir.resolve("instrumented" + JAR_EXTENSION);
      ZipUtils.zip(instrumentedJar, instrumented);
    }

    public Path getOriginal() {
      return originalJar;
    }

    public Path getInstrumented() {
      return instrumentedJar;
    }

    public List<String> generateReport(Path jacocoExec) throws IOException {
      Path report = dir.resolve("report.scv");
      ProcessResult result = ToolHelper.runJaCoCoReport(originalJar, jacocoExec, report);
      assertEquals(result.toString(), 0, result.exitCode);
      return Files.readAllLines(report);
    }

    private void runJacocoInstrumentation(Path input, Path outdir) throws IOException {
      ProcessResult result = ToolHelper.runJaCoCoInstrument(input, outdir);
      assertEquals(result.toString(), 0, result.exitCode);
    }
  }

  static class TestRunner {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
