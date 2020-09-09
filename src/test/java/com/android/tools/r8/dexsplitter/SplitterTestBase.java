package com.android.tools.r8.dexsplitter;

import static junit.framework.TestCase.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dexsplitter.DexSplitter.Options;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import dalvik.system.PathClassLoader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.rules.TemporaryFolder;

public class SplitterTestBase extends TestBase {

  public static FeatureSplit simpleSplitProvider(
      FeatureSplit.Builder builder, Path outputPath, TemporaryFolder temp, Class<?>... classes) {
    return simpleSplitProvider(builder, outputPath, temp, Arrays.asList(classes));
  }

  private static FeatureSplit simpleSplitProvider(
      FeatureSplit.Builder builder,
      Path outputPath,
      TemporaryFolder temp,
      Collection<Class<?>> classes) {
    addConsumers(builder, outputPath, temp, null, classes);
    return builder.build();
  }

  private static void addConsumers(
      FeatureSplit.Builder builder,
      Path outputPath,
      TemporaryFolder temp,
      Collection<Pair<String, String>> nonJavaResources,
      Collection<Class<?>> classes) {
    Path featureJar;
    try {
      featureJar = temp.newFolder().toPath().resolve("feature.jar");
      writeClassesToJar(featureJar, classes);
      if (nonJavaResources != null && nonJavaResources.size() > 0) {
        // We can't simply append to an existing zip easily, just copy the entries and add what we
        // need.
        Path newFeatureJar = temp.newFolder().toPath().resolve("feature.jar");

        ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(newFeatureJar));
        ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(featureJar));
        ZipEntry next = inputStream.getNextEntry();
        while (next != null) {
          outputStream.putNextEntry(new ZipEntry(next.getName()));
          outputStream.write(ByteStreams.toByteArray(inputStream));
          outputStream.closeEntry();
          next = inputStream.getNextEntry();
        }

        for (Pair<String, String> nonJavaResource : nonJavaResources) {
          ZipUtils.writeToZipStream(
              outputStream,
              nonJavaResource.getFirst(),
              nonJavaResource.getSecond().getBytes(),
              ZipEntry.STORED);
        }
        outputStream.close();
        featureJar = newFeatureJar;
      }
    } catch (IOException e) {
      fail();
      return;
    }

    builder
        .addProgramResourceProvider(ArchiveResourceProvider.fromArchive(featureJar, true))
        .setProgramConsumer(
            new ArchiveConsumer(outputPath, true) {
              @Override
              public void accept(
                  int fileIndex,
                  ByteDataView data,
                  Set<String> descriptors,
                  DiagnosticsHandler handler) {
                super.accept(fileIndex, data, descriptors, handler);
              }
            });
  }

  public static FeatureSplit splitWithNonJavaFile(
      FeatureSplit.Builder builder,
      Path outputPath,
      TemporaryFolder temp,
      Collection<Pair<String, String>> nonJavaFiles,
      Class<?>... classes) {
    addConsumers(builder, outputPath, temp, nonJavaFiles, Arrays.asList(classes));
    return builder.build();
  }

  <E extends Throwable> ProcessResult testR8Splitter(
      TestParameters parameters,
      Set<Class<?>> baseClasses,
      Set<Class<?>> featureClasses,
      Class<?> toRun,
      ThrowingConsumer<R8TestCompileResult, E> compileResultConsumer,
      Consumer<R8FullTestBuilder> r8TestConfigurator)
      throws IOException, CompilationFailedException, E {
    Path featureOutput = temp.newFile("feature.zip").toPath();

    R8FullTestBuilder r8FullTestBuilder = testForR8(parameters.getBackend());
    if (parameters.isCfRuntime()) {
      // Compiling to jar we need to support the same way of loading code at runtime as
      // android supports.
      r8FullTestBuilder
          .addProgramClasses(PathClassLoader.class)
          .addKeepClassAndMembersRules(PathClassLoader.class);
    }

    r8FullTestBuilder
        .addProgramClasses(SplitRunner.class, RunInterface.class)
        .addProgramClasses(baseClasses)
        .addFeatureSplit(
            builder -> simpleSplitProvider(builder, featureOutput, temp, featureClasses))
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(SplitRunner.class)
        .addKeepClassRules(toRun);

    r8TestConfigurator.accept(r8FullTestBuilder);

    R8TestCompileResult r8TestCompileResult = r8FullTestBuilder.compile();
    compileResultConsumer.accept(r8TestCompileResult);
    Path baseOutput = r8TestCompileResult.writeToZip();

    return runFeatureOnArt(toRun, baseOutput, featureOutput, parameters.getRuntime());
  }

  // Compile the passed in classes plus RunInterface and SplitRunner using R8, then split
  // based on the base/feature sets. toRun must implement the BaseRunInterface
  <E extends Throwable> ProcessResult testDexSplitter(
      TestParameters parameters,
      Set<Class<?>> baseClasses,
      Set<Class<?>> featureClasses,
      Class<?> toRun,
      String expectedOutput,
      ThrowingConsumer<R8TestCompileResult, E> compileResultConsumer,
      Consumer<R8FullTestBuilder> r8TestConfigurator)
      throws Exception, E {
    List<Class<?>> baseClassesWithRunner =
        ImmutableList.<Class<?>>builder()
            .add(RunInterface.class, SplitRunner.class)
            .addAll(baseClasses)
            .build();

    Path baseJar = jarTestClasses(baseClassesWithRunner);
    Path featureJar = jarTestClasses(featureClasses);

    Path featureOnly =
        testForR8(parameters.getBackend())
            .addProgramClasses(featureClasses)
            .addClasspathClasses(baseClasses)
            .addClasspathClasses(RunInterface.class)
            .addKeepAllClassesRule()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    if (parameters.isDexRuntime()) {
      // With D8 this should just work. We compile all of the base classes, then run with the
      // feature loaded at runtime. Since there is no inlining/class merging we don't
      // have any issues.
      testForD8()
          .addProgramClasses(SplitRunner.class, RunInterface.class)
          .addProgramClasses(baseClasses)
          .setMinApi(parameters.getApiLevel())
          .compile()
          .run(
              parameters.getRuntime(),
              SplitRunner.class,
              toRun.getName(),
              featureOnly.toAbsolutePath().toString())
          .assertSuccessWithOutput(expectedOutput);
    }

    R8FullTestBuilder builder = testForR8(parameters.getBackend());
    if (parameters.isCfRuntime()) {
      // Compiling to jar we need to support the same way of loading code at runtime as
      // android supports.
      builder
          .addProgramClasses(PathClassLoader.class)
          .addKeepClassAndMembersRules(PathClassLoader.class);
    }

    R8FullTestBuilder r8FullTestBuilder =
        builder
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(SplitRunner.class, RunInterface.class)
            .addProgramClasses(baseClasses)
            .addProgramClasses(featureClasses)
            .addKeepMainRule(SplitRunner.class)
            .addKeepClassRules(toRun);
    r8TestConfigurator.accept(r8FullTestBuilder);
    R8TestCompileResult r8TestCompileResult = r8FullTestBuilder.compile();
    compileResultConsumer.accept(r8TestCompileResult);
    Path fullFiles = r8TestCompileResult.writeToZip();

    // Ensure that we can run the program as a unit (i.e., without splitting)
    r8TestCompileResult
        .run(parameters.getRuntime(), SplitRunner.class, toRun.getName())
        .assertSuccessWithOutput(expectedOutput);

    Path splitterOutput = temp.newFolder().toPath();
    Path splitterBaseDexFile = splitterOutput.resolve("base").resolve("classes.dex");
    Path splitterFeatureDexFile = splitterOutput.resolve("feature").resolve("classes.dex");

    Options options = new Options();
    options.setOutput(splitterOutput.toString());
    options.addBaseJar(baseJar.toString());
    options.addFeatureJar(featureJar.toString(), "feature");

    options.addInputArchive(fullFiles.toString());
    DexSplitter.run(options);

    return runFeatureOnArt(
        toRun, splitterBaseDexFile, splitterFeatureDexFile, parameters.getRuntime());
  }

  ProcessResult runFeatureOnArt(
      Class toRun, Path splitterBaseDexFile, Path splitterFeatureDexFile, TestRuntime runtime)
      throws IOException {
    assumeTrue(runtime.isDex());
    ArtCommandBuilder commandBuilder = new ArtCommandBuilder(runtime.asDex().getVm());
    commandBuilder.appendClasspath(splitterBaseDexFile.toString());
    commandBuilder.appendProgramArgument(toRun.getName());
    commandBuilder.appendProgramArgument(splitterFeatureDexFile.toString());
    commandBuilder.setMainClass(SplitRunner.class.getName());
    return ToolHelper.runArtRaw(commandBuilder);
  }

  public interface RunInterface {
    void run();
  }

  public static class SplitRunner {
    /* We support two different modes:
     *   - One argument to main:
     *     Pass in the class to be loaded, must implement RunInterface, run will be called
     *   - Two or more arguments to main:
     *     Pass in the class to be loaded, must implement RunInterface, run will be called
     *     Pass in the feature split that we class load, and an optional list of other feature
     *     splits that must be loaded before the given feature split.
     */
    public static void main(String[] args) {
      if (args.length < 1) {
        throw new RuntimeException("Unsupported number of arguments");
      }
      String classToRun = args[0];
      ClassLoader loader = SplitRunner.class.getClassLoader();
      // In the case where we simulate splits, the second argument is the feature to load, followed
      // by all the other features that it depends on.
      if (args.length >= 2) {
        for (int i = args.length - 1; i >= 1; i--) {
          try {
            loader = new PathClassLoader(args[i], loader);
          } catch (MalformedURLException e) {
            throw new RuntimeException("Failed reading input URL");
          }
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
}
