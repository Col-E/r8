// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.KotlinCompilerTool;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestBase.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ContinuousSteppingTest extends DebugTestBase {

  private static final String MAIN_METHOD_NAME = "main";

  // A list of self-contained jars to process (which do not depend on other jar files).
  private static List<Pair<Path, Predicate<Version>>> listOfJars() {
    return new ConfigListBuilder()
        .addAll(
            findAllJarsIn(Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR)),
            ContinuousSteppingTest::fromAndroidN)
        .build();
  }

  private final String mainClass;
  private final Path jarPath;

  private static class ConfigListBuilder {

    private final Builder<Pair<Path, Predicate<Version>>> builder = ImmutableList.builder();

    public ConfigListBuilder add(Path path, Predicate<Version> predicate) {
      builder.add(new Pair<>(path, predicate));
      return this;
    }

    public ConfigListBuilder addAll(List<Path> paths, Predicate<Version> predicate) {
      for (Path path : paths) {
        add(path, predicate);
      }
      return this;
    }

    public ConfigListBuilder addAllKotlinDebugJars(
        TemporaryFolder temp, Predicate<Version> predicate) {
      KotlinCompileMemoizer compiledJars =
          KotlinTestBase.getCompileMemoizer(
                  KotlinTestBase.getKotlinFilesInResource("debug"),
                  CfRuntime.getCheckedInJdk9(),
                  temp)
              .configure(KotlinCompilerTool::includeRuntime);
      for (KotlinTestParameters kotlinParameter :
          TestBase.getKotlinTestParameters().withAllCompilersAndTargetVersions().build()) {
        add(compiledJars.getForConfiguration(kotlinParameter), predicate);
      }
      return this;
    }

    public List<Pair<Path, Predicate<Version>>> build() {
      return builder.build();
    }
  }

  public static boolean allVersions(Version dexVmVersion) {
    return true;
  }

  public static boolean fromAndroidN(Version dexVmVersion) {
    return dexVmVersion.isNewerThanOrEqual(Version.V7_0_0);
  }

  private static List<Path> findAllJarsIn(Path root) {
    try {
      return Files.walk(root)
          .filter(p -> p.toFile().getPath().endsWith(FileUtils.JAR_EXTENSION))
          .collect(Collectors.toList());
    } catch (IOException e) {
      return Collections.emptyList();
    }
  }

  @Parameters(name = "{0} from {1}")
  public static Collection<Object[]> getData() throws IOException {
    List<Object[]> testCases = new ArrayList<>();
    for (Pair<Path, Predicate<Version>> pair : listOfJars()) {
      if (pair.getSecond().test(ToolHelper.getDexVm().getVersion())) {
        Path jarPath = pair.getFirst();
        List<String> mainClasses = getAllMainClassesFromJar(jarPath);
        for (String className : mainClasses) {
          testCases.add(new Object[]{className, jarPath});
        }
      }
    }
    return testCases;
  }

  private static final Function<Path, DebugTestConfig> compiledJars =
      memoizeFunction(path -> new D8DebugTestConfig().compileAndAdd(getStaticTemp(), path));

  public ContinuousSteppingTest(String mainClass, Path jarPath) {
    this.mainClass = mainClass;
    this.jarPath = jarPath;
  }

  @Test
  public void testContinuousSingleStep() throws Throwable {
    DebugTestConfig config = compiledJars.apply(jarPath);
    assert config != null;
    runContinuousTest(mainClass, config, MAIN_METHOD_NAME);
  }

  // Returns a list of classes with a "public static void main(String[])" method in the given jar
  // file.
  private static List<String> getAllMainClassesFromJar(Path pathToJar) throws IOException {
    JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(pathToJar,
        StandardOpenOption.READ));
    final URL url = pathToJar.toUri().toURL();
    assert pathToJar.toFile().exists();
    assert pathToJar.toFile().isFile();
    List<String> mainClasses = new ArrayList<>();
    ClassLoader loader = new URLClassLoader(new URL[]{url},
        Thread.currentThread().getContextClassLoader());

    try {
      JarEntry entry;
      while ((entry = jarInputStream.getNextJarEntry()) != null) {
        String entryName = entry.getName();
        if (entryName.endsWith(FileUtils.CLASS_EXTENSION)) {
          String className =
              entryName.substring(0, entryName.length() - FileUtils.CLASS_EXTENSION.length());
          className = className.replace(DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR,
              DescriptorUtils.JAVA_PACKAGE_SEPARATOR);
          try {
            Class<?> cls = loader.loadClass(className);
            if (cls != null) {
              long mainMethodsCount = Arrays.stream(cls.getMethods())
                  .filter(ContinuousSteppingTest::isMainMethod)
                  .count();
              if (mainMethodsCount == 1) {
                // Add class to the list
                mainClasses.add(className);
              }
            }
          } catch (Throwable e) {
            System.out.println(
                "Could not load class " + className + " from " + pathToJar.toFile().getPath());
            return Collections.emptyList();
          }
        }
      }
    } finally {
      jarInputStream.close();
    }
    return mainClasses;
  }

  private static boolean isMainMethod(Method m) {
    return Modifier.isStatic(m.getModifiers())
        && m.getReturnType() == void.class
        && m.getName().equals(MAIN_METHOD_NAME)
        && m.getParameterCount() == 1
        && m.getParameterTypes()[0] == String[].class;
  }
}
