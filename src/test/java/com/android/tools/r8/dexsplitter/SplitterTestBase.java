package com.android.tools.r8.dexsplitter;

import static junit.framework.TestCase.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.io.ByteStreams;
import dalvik.system.PathClassLoader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
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
        .setProgramConsumer(new ArchiveConsumer(outputPath, true));
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

  public ProcessResult testR8Splitter(
      TestParameters parameters,
      Set<Class<?>> baseClasses,
      Set<Class<?>> featureClasses,
      Class<?> toRun,
      ThrowableConsumer<R8TestCompileResult> compileResultConsumer,
      ThrowableConsumer<R8FullTestBuilder> r8TestConfigurator)
      throws IOException, CompilationFailedException {
    R8FullTestBuilder r8FullTestBuilder = testForR8(parameters.getBackend());
    if (parameters.isCfRuntime()) {
      // Compiling to jar we need to support the same way of loading code at runtime as
      // android supports.
      r8FullTestBuilder
          .addProgramClasses(PathClassLoader.class)
          .addKeepClassAndMembersRules(PathClassLoader.class);
    }

    R8TestCompileResult r8TestCompileResult =
        r8FullTestBuilder
            .addProgramClasses(SplitRunner.class, RunInterface.class)
            .addProgramClasses(baseClasses)
            .addFeatureSplit(featureClasses.toArray(new Class[0]))
            .addInliningAnnotations()
            .setMinApi(parameters)
            .addKeepMainRule(SplitRunner.class)
            .addKeepClassRules(toRun)
            .apply(r8TestConfigurator)
            .compile()
            .apply(compileResultConsumer);

    Path baseOutput = r8TestCompileResult.writeToZip();
    return runFeatureOnArt(
        toRun, baseOutput, r8TestCompileResult.getFeature(0), parameters.getRuntime());
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
