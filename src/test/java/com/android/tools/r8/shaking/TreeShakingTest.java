// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.ToolHelper.DEFAULT_PROGUARD_MAP_FILE;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.FoundFieldSubject;
import com.android.tools.r8.utils.dexinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Base class of individual tree shaking tests in com.android.tools.r8.shaking.examples.
 * <p>
 * To add a new test, add Java files and keep rules to a new subdirectory of src/test/examples and
 * add a new subclass of TreeShakingTest. To run with multiple minification modes and
 * frontend/backend combinations, copy the Parameterized setup from one of the existing subclasses,
 * e.g. {@link com.android.tools.r8.shaking.examples.TreeShaking1Test}. Then create a test method
 * that calls {@link TreeShakingTest::runTest}, passing in the path to your keep rule file and
 * lambdas to determine if the right bits of the application are kept or discarded.
 */
public abstract class TreeShakingTest {

  private Path proguardMap;
  private Path out;

  protected enum Frontend {
    DEX, JAR
  }

  protected enum Backend {
    DEX, CF
  }

  private final String name;
  private final String mainClass;
  private final Frontend frontend;
  private final Backend backend;
  private final MinifyMode minify;

  public Frontend getFrontend() {
    return frontend;
  }

  public Backend getBackend() {
    return backend;
  }

  public MinifyMode getMinify() {
    return minify;
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  protected TreeShakingTest(
      String name, String mainClass, Frontend frontend, Backend backend, MinifyMode minify) {
    this.name = name;
    this.mainClass = mainClass;
    this.frontend = frontend;
    this.backend = backend;
    this.minify = minify;
  }

  private void generateTreeShakedVersion(
      Backend backend,
      String programFile,
      List<Path> jarLibraries,
      MinifyMode minify,
      List<String> keepRulesFiles,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    out = temp.getRoot().toPath().resolve("out.zip");
    proguardMap = temp.getRoot().toPath().resolve(DEFAULT_PROGUARD_MAP_FILE);
    // Generate R8 processed version without library option.
    boolean inline = programFile.contains("inlining");

    R8Command.Builder builder =
        ToolHelper.addProguardConfigurationConsumer(
                R8Command.builder(),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(proguardMap);
                  pgConfig.setOverloadAggressively(minify == MinifyMode.AGGRESSIVE);
                  if (!minify.isMinify()) {
                    pgConfig.disableObfuscation();
                  }
                })
            .addProguardConfigurationFiles(ListUtils.map(keepRulesFiles, Paths::get))
            .addLibraryFiles(jarLibraries);
    switch (backend) {
      case CF:
        builder.setOutput(out, OutputMode.ClassFile);
        break;
      case DEX:
        builder.setOutput(out, OutputMode.DexIndexed);
        break;
      default:
        throw new Unreachable();
    }
    ToolHelper.getAppBuilder(builder).addProgramFiles(Paths.get(programFile));
    ToolHelper.runR8(
        builder.build(),
        options -> {
          options.enableInlining = inline;
          if (optionsConsumer != null) {
            optionsConsumer.accept(options);
          }
        });
  }

  protected static void checkSameStructure(DexInspector ref, DexInspector inspector) {
    ref.forAllClasses(refClazz -> checkSameStructure(refClazz,
        inspector.clazz(refClazz.getDexClass().toSourceString())));
  }

  private static void checkSameStructure(ClassSubject refClazz, ClassSubject clazz) {
    Assert.assertTrue(clazz.isPresent());
    refClazz.forAllFields(refField -> checkSameStructure(refField, clazz));
    refClazz.forAllMethods(refMethod -> checkSameStructure(refMethod, clazz));
  }

  private static void checkSameStructure(FoundMethodSubject refMethod, ClassSubject clazz) {
    MethodSignature signature = refMethod.getOriginalSignature();
    // Don't check for existence of class initializers, as the code optimization can remove them.
    if (!refMethod.isClassInitializer()) {
      Assert.assertTrue("Missing Method: " + clazz.getDexClass().toSourceString() + "."
              + signature.toString(),
          clazz.method(signature).isPresent());
    }
  }

  private static void checkSameStructure(FoundFieldSubject refField, ClassSubject clazz) {
    FieldSignature signature = refField.getOriginalSignature();
    Assert.assertTrue(
        "Missing field: " + signature.type + " " + clazz.getOriginalDescriptor()
            + "." + signature.name,
        clazz.field(signature.type, signature.name).isPresent());
  }

  protected void runTest(
      Consumer<DexInspector> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<DexInspector, DexInspector> dexComparator,
      List<String> keepRulesFiles)
      throws Exception {
    runTest(inspection, outputComparator, dexComparator, keepRulesFiles, null);
  }

  protected void runTest(
      Consumer<DexInspector> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<DexInspector, DexInspector> dexComparator,
      List<String> keepRulesFiles,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    String originalDex = ToolHelper.TESTS_BUILD_DIR + name + "/classes.dex";
    String programFile;
    if (frontend == Frontend.DEX) {
      programFile = originalDex;
    } else {
      programFile = ToolHelper.TESTS_BUILD_DIR + name + ".jar";
    }
    List<Path> jarLibraries;
    if (backend == Backend.CF) {
      jarLibraries =
          ImmutableList.of(
              ToolHelper.getJava8RuntimeJar(),
              Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib.jar"));
    } else {
      jarLibraries =
          ImmutableList.of(
              ToolHelper.getDefaultAndroidJar(),
              Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib.jar"));
    }

    generateTreeShakedVersion(
        backend, programFile, jarLibraries, minify, keepRulesFiles, optionsConsumer);

    if (backend == Backend.CF) {
      Path shakinglib = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "shakinglib.jar");
      ProcessResult resultInput =
          ToolHelper.runJava(Arrays.asList(Paths.get(programFile), shakinglib), mainClass);
      Assert.assertEquals(0, resultInput.exitCode);
      ProcessResult resultOutput =
          ToolHelper.runJava(Arrays.asList(out, shakinglib), mainClass);
      if (outputComparator != null) {
        outputComparator.accept(resultInput.stdout, resultOutput.stdout);
      } else {
        Assert.assertEquals(resultInput.toString(), resultOutput.toString());
      }
      if (inspection != null) {
        DexInspector inspector =
            new DexInspector(
                out,
                minify.isMinify()
                    ? proguardMap.toString()
                    : null);
        inspection.accept(inspector);
      }
      return;
    }
    if (!ToolHelper.artSupported() && !ToolHelper.compareAgaintsGoldenFiles()) {
      return;
    }
    Consumer<ArtCommandBuilder> extraArtArgs = builder -> {
      builder.appendClasspath(ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib/classes.dex");
    };

    if (Files.exists(Paths.get(originalDex))) {
      if (outputComparator != null) {
        String output1 = ToolHelper.runArtNoVerificationErrors(
            Collections.singletonList(originalDex), mainClass, extraArtArgs, null);
        String output2 = ToolHelper.runArtNoVerificationErrors(
            Collections.singletonList(out.toString()), mainClass, extraArtArgs, null);
        outputComparator.accept(output1, output2);
      } else {
        ToolHelper.checkArtOutputIdentical(Collections.singletonList(originalDex),
            Collections.singletonList(out.toString()), mainClass,
            extraArtArgs, null);
      }

      if (dexComparator != null) {
        DexInspector ref = new DexInspector(Paths.get(originalDex));
        DexInspector inspector = new DexInspector(out,
            minify.isMinify() ? proguardMap.toString()
                : null);
        dexComparator.accept(ref, inspector);
      }
    } else {
      Assert.assertNull(outputComparator);
      Assert.assertNull(dexComparator);
      ToolHelper.runArtNoVerificationErrors(
          Collections.singletonList(out.toString()), mainClass, extraArtArgs, null);
    }

    if (inspection != null) {
      DexInspector inspector = new DexInspector(out,
          minify.isMinify() ? proguardMap.toString()
              : null);
      inspection.accept(inspector);
    }
  }
}
