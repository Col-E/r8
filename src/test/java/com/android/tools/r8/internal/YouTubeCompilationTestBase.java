// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.analysis.ProtoApplicationStats;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public abstract class YouTubeCompilationTestBase extends CompilationTestBase {

  static final String DEPLOY_JAR = "YouTubeRelease_deploy.jar";
  static final String PG_MAP = "YouTubeRelease_proguard.map";
  static final String PG_CONF = "YouTubeRelease_proguard.config";
  static final String PG_CONF_EXTRA = "YouTubeRelease_proguard_extra.config";
  static final String PG_PROTO_CONF = "YouTubeRelease_proto_safety.pgconf";
  static final String PG_MISSING_CLASSES_CONF = "YouTubeRelease_proguard_missing_classes.config";

  final String base;
  final AndroidApiLevel apiLevel;

  public YouTubeCompilationTestBase(int majorVersion, int minorVersion, AndroidApiLevel apiLevel) {
    this.base =
        "third_party/youtube/youtube.android_"
            + majorVersion
            + "."
            + String.format("%02d", minorVersion)
            + "/";
    this.apiLevel = apiLevel;
  }

  protected AndroidApiLevel getApiLevel() {
    return apiLevel;
  }

  protected Path getDesugaredLibraryConfiguration() {
    Path path = Paths.get(base, "desugar_jdk_libs/full_desugar_jdk_libs.json");
    assertTrue(path.toFile().exists());
    return path;
  }

  protected Path getDesugaredLibraryJDKLibs() {
    Path path = Paths.get(base, "desugar_jdk_libs/jdk_libs_to_desugar.jar");
    assertTrue(path.toFile().exists());
    return path;
  }

  protected Path getD8DesugaredLibraryJDKLibs() {
    Path path = Paths.get(base, "desugar_jdk_libs/d8_desugared_jdk_libs.jar");
    assertTrue(path.toFile().exists());
    return path;
  }

  protected Path getDesugaredLibraryJDKLibsConfiguration() {
    Path path = Paths.get(base, "desugar_jdk_libs/desugar_jdk_libs_configuration.jar");
    assertTrue(path.toFile().exists());
    return path;
  }

  protected List<Path> getDesugaredLibraryKeepRuleFiles(Path generatedKeepRules) {
    ImmutableList<Path> keepRuleFiles =
        ImmutableList.of(
            Paths.get(base, "desugar_jdk_libs/base.pgcfg"),
            Paths.get(base, "desugar_jdk_libs/minify_desugar_jdk_libs.pgcfg"),
            generatedKeepRules);
    assertTrue(keepRuleFiles.stream().allMatch(keepRuleFile -> keepRuleFile.toFile().exists()));
    return keepRuleFiles;
  }

  protected List<Path> getKeepRuleFiles() {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.add(Paths.get(base).resolve(PG_CONF));
    builder.add(Paths.get(ToolHelper.PROGUARD_SETTINGS_FOR_INTERNAL_APPS).resolve(PG_CONF));
    for (String name : new String[] {PG_CONF_EXTRA, PG_PROTO_CONF, PG_MISSING_CLASSES_CONF}) {
      Path config = Paths.get(base).resolve(name);
      if (config.toFile().exists()) {
        builder.add(config);
      }
    }
    return builder.build();
  }

  protected Path getLibraryFile() {
    Path filtered =
        Paths.get(base).resolve("legacy_YouTubeRelease_combined_library_jars_filtered.jar");
    if (filtered.toFile().exists()) {
      return filtered;
    }
    Path unfiltered = Paths.get(base, "legacy_YouTubeRelease_combined_library_jars.jar");
    assertTrue(unfiltered.toFile().exists());
    return unfiltered;
  }

  Path getLibraryFileWithoutDesugaredLibrary() throws IOException {
    Path libraryFile = getLibraryFile();
    Path filteredLibraryFile =
        Paths.get(libraryFile.toString().replace(".jar", "desugared_lib_filtered.jar"));
    ArchiveConsumer consumer = new ArchiveConsumer(filteredLibraryFile);
    ZipUtils.iter(
        libraryFile,
        (entry, inputStream) -> {
          String entryString = entry.toString();
          if (entryString.endsWith(".class") && !entryString.startsWith("j$")) {
            byte[] bytes = ByteStreams.toByteArray(inputStream);
            consumer.accept(ByteDataView.of(bytes), TestBase.extractClassDescriptor(bytes), null);
          }
        });
    consumer.finished(null);
    return filteredLibraryFile;
  }

  protected List<Path> getMainDexRuleFiles() {
    return ImmutableList.of(
        Paths.get(base).resolve("mainDexClasses.rules"),
        Paths.get(base).resolve("main-dex-classes-release-optimized.pgcfg"),
        Paths.get(base).resolve("main_dex_YouTubeRelease_proguard.cfg"));
  }

  protected List<Path> getProgramFiles() throws IOException {
    List<Path> result = new ArrayList<>();
    for (Path keepRuleFile : getKeepRuleFiles()) {
      for (String line : FileUtils.readAllLines(keepRuleFile)) {
        if (line.startsWith("-injars")) {
          String fileName = line.substring("-injars ".length());
          result.add(Paths.get(base).resolve(fileName));
        }
      }
    }
    if (result.isEmpty()) {
      Path path = Paths.get(base).resolve(DEPLOY_JAR);
      assert path.toFile().exists();
      result.add(path);
    }
    return result;
  }

  Path getReleaseApk() {
    return Paths.get(base).resolve("YouTubeRelease.apk");
  }

  Path getReleaseProguardMap() {
    return Paths.get(base).resolve(PG_MAP);
  }

  void printProtoStats(R8TestCompileResult compileResult) throws Exception {
    if (ToolHelper.isLocalDevelopment()) {
      DexItemFactory dexItemFactory = new DexItemFactory();
      ProtoApplicationStats original =
          new ProtoApplicationStats(dexItemFactory, new CodeInspector(getProgramFiles()));
      ProtoApplicationStats actual =
          new ProtoApplicationStats(dexItemFactory, compileResult.inspector(), original);
      ProtoApplicationStats baseline =
          new ProtoApplicationStats(
              dexItemFactory, new CodeInspector(getReleaseApk(), getReleaseProguardMap()));
      System.out.println(actual.getStats(baseline));
    }
  }
}
