// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.legacy;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class L8TestWithLegacySpecification extends TestBase {

  @Parameter(0)
  public AndroidApiLevel apiLevel;

  @Parameter(1)
  public CompilationMode mode;

  @Parameter(2)
  public L8KeepRules l8KeepRules;

  @Parameter(3)
  public TestParameters none;

  @Parameters(name = "{0}, {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        Arrays.stream(AndroidApiLevel.values())
            .sorted()
            .filter(apiLevel -> apiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.L))
            .filter(apiLevel -> apiLevel.isLessThan(AndroidApiLevel.MASTER))
            .collect(Collectors.toList()),
        CompilationMode.values(),
        ImmutableList.of(
            new L8KeepRules("AGP", agp73KeepRules),
            new L8KeepRules("j$", ImmutableList.of("-keep class j$.** { *; }")),
            new L8KeepRules("java", ImmutableList.of("-keep class java.** { *; }")),
            new L8KeepRules(
                "both",
                ImmutableList.of("-keep class j$.** { *; }", "-keep class java.** { *; }"))),
        getTestParameters().withNoneRuntime().build());
  }

  private static class L8KeepRules {

    private String name;
    private List<String> keepRules;

    L8KeepRules(String name, List<String> keepRules) {
      this.name = name;
      this.keepRules = keepRules;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  // Keep rules generated by D8 for an empty app with AGP 7.3.0-aplha09.
  private static List<String> agp73KeepRules =
      StringUtils.splitLines(
          "-keep class j$.util.DesugarTimeZone {\n"
              + "    java.util.TimeZone getTimeZone(java.lang.String);\n"
              + "}\n"
              + "-keep class java.util.function.IntFunction\n"
              + "-keep class j$.util.concurrent.ConcurrentHashMap {\n"
              + "    void <init>();\n"
              + "    java.lang.Object get(java.lang.Object);\n"
              + "    java.lang.Object put(java.lang.Object, java.lang.Object);\n"
              + "}\n"
              + "-keep class j$.util.function.IntFunction$Wrapper {\n"
              + "    java.util.function.IntFunction convert(j$.util.function.IntFunction);\n"
              + "}\n"
              + "-keep class j$.util.function.IntFunction { *; }\n"
              + "-keep class j$.util.DesugarCollections {\n"
              + "    java.util.Map synchronizedMap(java.util.Map);\n"
              + "}\n"
              + "-keep class java.util.function.Supplier\n"
              + "-keep class j$.util.function.Supplier$Wrapper {\n"
              + "    java.util.function.Supplier convert(j$.util.function.Supplier);\n"
              + "}\n"
              + "-keep class j$.util.function.Supplier { *; }\n"
              + "-keep class java.util.function.Consumer\n"
              + "-keep class j$.util.Collection$-EL {\n"
              + "    boolean removeIf(java.util.Collection, j$.util.function.Predicate);\n"
              + "}\n"
              + "-keep class j$.util.concurrent.ConcurrentHashMap {\n"
              + "    void <init>();\n"
              + "    java.lang.Object get(java.lang.Object);\n"
              + "    java.lang.Object put(java.lang.Object, java.lang.Object);\n"
              + "}\n"
              + "-keep class j$.util.function.Consumer$-CC {\n"
              + "    j$.util.function.Consumer $default$andThen(j$.util.function.Consumer,"
              + " j$.util.function.Consumer);\n"
              + "}\n"
              + "-keep class j$.util.function.Consumer$Wrapper {\n"
              + "    java.util.function.Consumer convert(j$.util.function.Consumer);\n"
              + "}\n"
              + "-keep class j$.util.function.Consumer { *; }\n"
              + "-keep class j$.util.function.Predicate$-CC {\n"
              + "    j$.util.function.Predicate $default$and(j$.util.function.Predicate,"
              + " j$.util.function.Predicate);\n"
              + "    j$.util.function.Predicate $default$negate(j$.util.function.Predicate);\n"
              + "    j$.util.function.Predicate $default$or(j$.util.function.Predicate,"
              + " j$.util.function.Predicate);\n"
              + "}\n"
              + "-keep class j$.util.function.Predicate { *; }\n"
              + "-keep class java.util.function.BiConsumer\n"
              + "-keep class java.util.function.BiFunction\n"
              + "-keep class java.util.function.Consumer\n"
              + "-keep class java.util.function.Function\n"
              + "-keep class j$.util.Iterator { *; }\n"
              + "-keep class j$.util.Map$Entry { *; }\n"
              + "-keep class j$.util.Map { *; }\n"
              + "-keep class j$.util.Iterator$-CC {\n"
              + "    void $default$forEachRemaining(java.util.Iterator,"
              + " j$.util.function.Consumer);\n"
              + "}\n"
              + "-keep class j$.util.Map$-CC {\n"
              + "    java.lang.Object $default$compute(java.util.Map, java.lang.Object,"
              + " j$.util.function.BiFunction);\n"
              + "    java.lang.Object $default$computeIfAbsent(java.util.Map, java.lang.Object,"
              + " j$.util.function.Function);\n"
              + "    java.lang.Object $default$computeIfPresent(java.util.Map, java.lang.Object,"
              + " j$.util.function.BiFunction);\n"
              + "    void $default$forEach(java.util.Map, j$.util.function.BiConsumer);\n"
              + "    java.lang.Object $default$merge(java.util.Map, java.lang.Object,"
              + " java.lang.Object, j$.util.function.BiFunction);\n"
              + "    void $default$replaceAll(java.util.Map, j$.util.function.BiFunction);\n"
              + "}\n"
              + "-keep class j$.util.function.BiConsumer$VivifiedWrapper {\n"
              + "    j$.util.function.BiConsumer convert(java.util.function.BiConsumer);\n"
              + "}\n"
              + "-keep class j$.util.function.BiConsumer\n"
              + "-keep class j$.util.function.BiFunction$VivifiedWrapper {\n"
              + "    j$.util.function.BiFunction convert(java.util.function.BiFunction);\n"
              + "}\n"
              + "-keep class j$.util.function.BiFunction\n"
              + "-keep class j$.util.function.Consumer$VivifiedWrapper {\n"
              + "    j$.util.function.Consumer convert(java.util.function.Consumer);\n"
              + "}\n"
              + "-keep class j$.util.function.Consumer\n"
              + "-keep class j$.util.function.Function$VivifiedWrapper {\n"
              + "    j$.util.function.Function convert(java.util.function.Function);\n"
              + "}\n"
              + "-keep class j$.util.function.Function\n");

  @Test
  public void testL8WithLegacyConfigurationAndStudioKeepRules() throws Exception {
    L8Command.Builder builder = L8Command.builder();
    L8Command command =
        builder
            .addLibraryFiles(LibraryDesugaringSpecification.JDK11_LEGACY.getLibraryFiles())
            .addProgramFiles(LibraryDesugaringSpecification.JDK11_LEGACY.getDesugarJdkLibs())
            .addDesugaredLibraryConfiguration(
                FileUtils.readTextFile(
                    LibraryDesugaringSpecification.JDK11_LEGACY.getSpecification(),
                    StandardCharsets.UTF_8))
            .addProguardConfiguration(l8KeepRules.keepRules, Origin.unknown())
            .setMode(mode)
            .setOutput(temp.newFolder().toPath().resolve("out.jar"), OutputMode.DexIndexed)
            .setMinApiLevel(apiLevel.getLevel())
            .build();
    // TODO(b/231925782): This should succeed for all API levels.
    L8.run(command);
  }
}
