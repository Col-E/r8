// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dexsplitter.SplitterTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import dalvik.system.PathClassLoader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FeatureSplitTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public FeatureSplitTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testTwoFeatures() throws CompilationFailedException, IOException, ExecutionException {
    CompiledWithFeature compiledWithFeature = new CompiledWithFeature().invoke(this);
    Path basePath = compiledWithFeature.getBasePath();
    Path feature1Path = compiledWithFeature.getFeature1Path();
    Path feature2Path = compiledWithFeature.getFeature2Path();
    Path desugaredLibrary = compiledWithFeature.getDesugaredLibrary();

    assertKeepThe3StreamMethods(compiledWithFeature.getKeepRules());

    assertClassPresent(basePath, BaseClass.class);
    assertClassPresent(feature1Path, FeatureClass.class);
    assertClassPresent(feature2Path, FeatureClass2.class);

    verifyRun(BaseClass.class, basePath, desugaredLibrary, null, "42");
    verifyRun(FeatureClass.class, basePath, desugaredLibrary, feature1Path, "1");
    verifyRun(FeatureClass2.class, basePath, desugaredLibrary, feature2Path, "7");
  }

  private void assertKeepThe3StreamMethods(String keepRules) {
    // Stream desugaring is not needed >= N.
    if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
      return;
    }
    // Ensure count, toArray and forEach are kept.
    assertTrue(
        keepRules.contains(
            "-keep class j$.lang.Iterable$-EL {\n"
                + "    void forEach(java.lang.Iterable, j$.util.function.Consumer);"));
    assertTrue(
        keepRules.contains(
            "-keep class j$.util.stream.Stream {\n"
                + "    long count();\n"
                + "    java.lang.Object[] toArray();"));
  }

  private void assertClassPresent(Path appPath, Class<?> present)
      throws IOException, ExecutionException {
    CodeInspector inspector = new CodeInspector(appPath);
    assertTrue(inspector.clazz(present).isPresent());
  }

  private void verifyRun(
      Class<?> toRun,
      Path basePath,
      Path desugaredLibrary,
      Path splitterFeatureDexFile,
      String expectedResult)
      throws IOException {
    ProcessResult result =
        runFeatureOnArt(
            toRun, desugaredLibrary, basePath, splitterFeatureDexFile, parameters.getRuntime());
    assertEquals(result.exitCode, 0);
    assertEquals(result.stdout, StringUtils.lines(expectedResult));
  }

  protected ProcessResult runFeatureOnArt(
      Class toRun,
      Path desugaredLibrary,
      Path splitterBaseDexFile,
      Path splitterFeatureDexFile,
      TestRuntime runtime)
      throws IOException {
    assumeTrue(runtime.isDex());
    ArtCommandBuilder commandBuilder = new ArtCommandBuilder(runtime.asDex().getVm());
    commandBuilder.appendClasspath(splitterBaseDexFile.toString());
    if (desugaredLibrary != null) {
      commandBuilder.appendClasspath(desugaredLibrary.toString());
    }
    commandBuilder.appendProgramArgument(toRun.getName());
    if (splitterFeatureDexFile != null) {
      commandBuilder.appendProgramArgument(splitterFeatureDexFile.toString());
    }
    commandBuilder.setMainClass(SplitRunner.class.getName());
    return ToolHelper.runArtRaw(commandBuilder);
  }

  public interface RunInterface {

    void run();
  }

  // Base using ForEach.
  public static class BaseClass implements RunInterface {

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      list.add(42);
      list.forEach(System.out::println);
    }
  }

  // Feature using count.
  public static class FeatureClass implements RunInterface {

    @SuppressWarnings("ReplaceInefficientStreamCount")
    @Override
    public void run() {
      ArrayList<Object> list = new ArrayList<>();
      list.add(new Object());
      System.out.println(list.stream().count());
    }
  }

  // Feature using toArray.
  public static class FeatureClass2 implements RunInterface {

    @SuppressWarnings("SimplifyStreamApiCallChains")
    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      list.add(7);
      System.out.println(list.stream().toArray()[0]);
    }
  }

  static class SplitRunner {

    /* We support two different modes:
     *   - One argument to main:
     *     Pass in the class to be loaded, must implement RunInterface, run will be called.
     *   - Two arguments to main:
     *     Pass in the class to be loaded, must implement RunInterface, run will be called.
     *     Pass in the feature split that we class load.
     */
    public static void main(String[] args) {
      if (args.length < 1 || args.length > 2) {
        throw new RuntimeException("Unsupported number of arguments");
      }
      String classToRun = args[0];
      ClassLoader loader = SplitRunner.class.getClassLoader();
      // In the case where we simulate splits, we pass in the feature as the second argument
      if (args.length == 2) {
        try {
          loader = new PathClassLoader(args[1], SplitRunner.class.getClassLoader());
        } catch (MalformedURLException e) {
          throw new RuntimeException("Failed reading input URL");
        }
      }

      try {
        Class<?> aClass = loader.loadClass(classToRun);
        RunInterface b = (RunInterface) aClass.newInstance();
        b.run();
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
        throw new RuntimeException("Failed loading class");
      }
    }
  }

  private class CompiledWithFeature {

    private Path basePath;
    private Path feature1Path;
    private Path feature2Path;
    private Path desugaredLibrary;
    private String keepRules = "";

    public Path getBasePath() {
      return basePath;
    }

    public Path getFeature1Path() {
      return feature1Path;
    }

    public Path getFeature2Path() {
      return feature2Path;
    }

    public Path getDesugaredLibrary() {
      return desugaredLibrary;
    }

    public String getKeepRules() {
      return keepRules;
    }

    public CompiledWithFeature invoke(FeatureSplitTest tester)
        throws IOException, CompilationFailedException {

      basePath = temp.newFile("base.zip").toPath();
      feature1Path = temp.newFile("feature1.zip").toPath();
      feature2Path = temp.newFile("feature2.zip").toPath();

      KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
      testForR8(parameters.getBackend())
          .addProgramClasses(BaseClass.class, RunInterface.class, SplitRunner.class)
          .setMinApi(parameters.getApiLevel())
          .addFeatureSplit(
              builder ->
                  SplitterTestBase.simpleSplitProvider(
                      builder, feature1Path, temp, FeatureClass.class))
          .addFeatureSplit(
              builder ->
                  SplitterTestBase.simpleSplitProvider(
                      builder, feature2Path, temp, FeatureClass2.class))
          .addKeepAllClassesRule()
          .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
          .compile()
          .writeToZip(basePath);
      // Stream desugaring is not needed >= N.
      if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
        return this;
      }
      desugaredLibrary =
          tester.buildDesugaredLibrary(
              parameters.getApiLevel(), keepRuleConsumer.get(), shrinkDesugaredLibrary);
      keepRules = keepRuleConsumer.get();
      return this;
    }
  }
}
