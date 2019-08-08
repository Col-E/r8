// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CoreLibDesugarTestBase extends TestBase {

  private static Map<CacheEntry, TestCompileResult> computedLibraryCache =
      new ConcurrentHashMap<>();

  @Deprecated
  protected boolean requiresCoreLibDesugaring(TestParameters parameters) {
    // TODO(b/134732760): Use the two other APIS instead.
    return requiresEmulatedInterfaceCoreLibDesugaring(parameters)
        && requiresRetargetCoreLibMemberDesugaring(parameters);
  }

  protected boolean requiresEmulatedInterfaceCoreLibDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel();
  }

  protected boolean requiresRetargetCoreLibMemberDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.P.getLevel();
  }

  protected boolean requiresAnyCoreLibDesugaring(TestParameters parameters) {
    return requiresRetargetCoreLibMemberDesugaring(parameters);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel) throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, "", false);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules)
      throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, keepRules, true);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules, boolean shrink)
      throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, keepRules, shrink, ImmutableList.of());
  }

  protected Path buildDesugaredLibrary(
      AndroidApiLevel apiLevel, String keepRules, boolean shrink, List<Path> additionalProgramFiles)
      throws RuntimeException {
    // We wrap exceptions in a RuntimeException to call this from a lambda.
    try {
      Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs_dex.zip");
      CacheEntry cacheEntry = new CacheEntry(apiLevel, keepRules, shrink, additionalProgramFiles);
      TestCompileResult testCompileResult =
          computedLibraryCache.computeIfAbsent(
              cacheEntry,
              key -> compileDesugaredLibrary(apiLevel, keepRules, shrink, additionalProgramFiles));
      testCompileResult.writeToZip(output);
      return output;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private TestCompileResult compileDesugaredLibrary(
      AndroidApiLevel apiLevel, String keepRules, boolean shrink, List<Path> additionalProgramFiles)
      throws RuntimeException {
    // We wrap exceptions in a RuntimeException to call this from a lambda.
    try {
      // TODO(b/138922694): Known performance issue here.
      Path cfDesugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs_cf.zip");
      L8.run(
          L8Command.builder()
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addProgramFiles(ToolHelper.getDesugarJDKLibs())
              .addProgramFiles(additionalProgramFiles)
              .addSpecialLibraryConfiguration("default")
              .setMinApiLevel(apiLevel.getLevel())
              .setOutput(cfDesugaredLib, OutputMode.ClassFile)
              .build());
      if (shrink) {
        return testForR8(Backend.DEX)
            .addProgramFiles(cfDesugaredLib)
            .noMinification()
            .addKeepRules(keepRules)
            // We still need P+ library files to resolve classes.
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .setMinApi(apiLevel)
            .compile();
      }
      return testForD8()
          .addProgramFiles(cfDesugaredLib)
          .setMinApi(apiLevel)
          // We still need P+ library files to resolve classes.
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .compile();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static class CacheEntry {

    private int apiLevel;
    private String keepRules;
    private boolean shrink;
    private List<Path> additionalProgramFiles;

    private CacheEntry(
        AndroidApiLevel apiLevel,
        String keepRules,
        boolean shrink,
        List<Path> additionalProgramFiles) {
      this.apiLevel = apiLevel.getLevel();
      this.keepRules = keepRules;
      this.shrink = shrink;
      this.additionalProgramFiles = additionalProgramFiles;
    }

    @Override
    public int hashCode() {
      // In practice there are only 2 sets of additionalProgramFiles with different sizes.
      return Objects.hash(apiLevel, keepRules, shrink, additionalProgramFiles.size());
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof CacheEntry)) {
        return false;
      }
      CacheEntry other = (CacheEntry) o;
      return apiLevel == other.apiLevel
          && keepRules.equals(other.keepRules)
          && shrink == other.shrink
          && additionalProgramFiles.equals(other.additionalProgramFiles);
    }
  }

  protected void assertLines2By2Correct(String stdOut) {
    String[] lines = stdOut.split("\n");
    assert lines.length % 2 == 0;
    for (int i = 0; i < lines.length; i += 2) {
      assertEquals(lines[i], lines[i + 1]);
    }
  }
}
