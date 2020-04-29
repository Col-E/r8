package com.android.tools.r8.dexsplitter;

import static junit.framework.TestCase.assertTrue;
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
import com.android.tools.r8.utils.DescriptorUtils;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.rules.TemporaryFolder;

public class SplitterTestBase extends TestBase {

  public static FeatureSplit simpleSplitProvider(
      FeatureSplit.Builder builder, Path outputPath, TemporaryFolder temp, Class... classes) {
    return simpleSplitProvider(builder, outputPath, temp, Arrays.asList(classes));
  }

  public static FeatureSplit simpleSplitProvider(
      FeatureSplit.Builder builder,
      Path outputPath,
      TemporaryFolder temp,
      Collection<Class<?>> classes) {
    addConsumers(builder, outputPath, temp, null, true, classes);
    return builder.build();
  }

  private static void addConsumers(
      FeatureSplit.Builder builder,
      Path outputPath,
      TemporaryFolder temp,
      Collection<String> nonJavaResources,
      boolean ensureClassesInOutput,
      Collection<Class<?>> classes) {
    List<String> classNames = classes.stream().map(Class::getName).collect(Collectors.toList());
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

        for (String nonJavaResource : nonJavaResources) {
          ZipUtils.writeToZipStream(
              outputStream, nonJavaResource, nonJavaResource.getBytes(), ZipEntry.STORED);
        }
        outputStream.close();
        featureJar = newFeatureJar;
      }
    } catch (IOException e) {
      assertTrue(false);
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
                if (ensureClassesInOutput) {
                  for (String descriptor : descriptors) {
                    assertTrue(
                        classNames.contains(DescriptorUtils.descriptorToJavaType(descriptor)));
                  }
                }
                super.accept(fileIndex, data, descriptors, handler);
              }
            });
  }

  protected static FeatureSplit splitWithNonJavaFile(
      FeatureSplit.Builder builder,
      Path outputPath,
      TemporaryFolder temp,
      Collection<String> nonJavaFiles,
      boolean ensureClassesInOutput,
      Class<?>... classes) {
    addConsumers(builder, outputPath, temp, nonJavaFiles, true, Arrays.asList(classes));
    return builder.build();
  }

  protected <E extends Throwable> ProcessResult testR8Splitter(
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
        .setMinApi(parameters.getRuntime())
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
  protected <E extends Throwable> ProcessResult testDexSplitter(
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
            .setMinApi(parameters.getRuntime())
            .compile()
            .writeToZip();
    if (parameters.isDexRuntime()) {
      // With D8 this should just work. We compile all of the base classes, then run with the
      // feature loaded at runtime. Since there is no inlining/class merging we don't
      // have any issues.
      testForD8()
          .addProgramClasses(SplitRunner.class, RunInterface.class)
          .addProgramClasses(baseClasses)
          .setMinApi(parameters.getRuntime())
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
            .setMinApi(parameters.getRuntime())
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

  protected ProcessResult runFeatureOnArt(
      Class toRun, Path splitterBaseDexFile, Path splitterFeatureDexFile, TestRuntime runtime)
      throws IOException {
    assumeTrue(runtime.isDex());
    ArtCommandBuilder commandBuilder = new ArtCommandBuilder(runtime.asDex().getVm());
    commandBuilder.appendClasspath(splitterBaseDexFile.toString());
    commandBuilder.appendProgramArgument(toRun.getName());
    commandBuilder.appendProgramArgument(splitterFeatureDexFile.toString());
    commandBuilder.setMainClass(SplitRunner.class.getName());
    ProcessResult processResult = ToolHelper.runArtRaw(commandBuilder);
    return processResult;
  }

  public interface RunInterface {
    void run();
  }

  static class SplitRunner {
    /* We support two different modes:
     *   - One argument to main:
     *     Pass in the class to be loaded, must implement RunInterface, run will be called
     *   - Two arguments to main:
     *     Pass in the class to be loaded, must implement RunInterface, run will be called
     *     Pass in the feature split that we class load
     *
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
}
