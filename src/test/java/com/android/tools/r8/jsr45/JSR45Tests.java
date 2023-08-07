// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jsr45;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

public class JSR45Tests {

  private static final String DEFAULT_MAP_FILENAME = "proguard.map";
  private static final Path INPUT_PATH =
      Paths.get(ToolHelper.TESTS_SOURCE_DIR + "com/android/tools/r8/jsr45/HelloKt.class");
  private static final Path DONT_SHRINK_DONT_OBFUSCATE_CONFIG =
      Paths.get(ToolHelper.TESTS_SOURCE_DIR + "com/android/tools/r8/jsr45/keep-rules-1.txt");
  private static final Path DONT_SHRINK_CONFIG =
      Paths.get(ToolHelper.TESTS_SOURCE_DIR + "com/android/tools/r8/jsr45/keep-rules-2.txt");
  private static final Path SHRINK_KEEP_CONFIG =
      Paths.get(ToolHelper.TESTS_SOURCE_DIR + "com/android/tools/r8/jsr45/keep-rules-3.txt");
  private static final Path SHRINK_NO_KEEP_CONFIG =
      Paths.get(ToolHelper.TESTS_SOURCE_DIR + "com/android/tools/r8/jsr45/keep-rules-4.txt");

  @Rule
  public TemporaryFolder tmpOutputDir = ToolHelper.getTemporaryFolderForTest();

  private AndroidApp compileWithD8(Path intputPath, Path outputPath)
      throws CompilationFailedException {
    D8Command.Builder builder =
        D8Command.builder()
            .setMinApiLevel(AndroidApiLevel.O.getLevel())
            .addProgramFiles(intputPath)
            .setOutput(outputPath, OutputMode.DexIndexed);
    AndroidAppConsumers appSink = new AndroidAppConsumers(builder);
    D8.run(builder.build());
    return appSink.build();
  }

  private AndroidApp compileWithR8(Path inputPath, Path outputPath, Path keepRulesPath)
      throws CompilationFailedException {
    KotlinCompiler kotlinc = KotlinCompiler.latest();
    return ToolHelper.runR8(
        R8Command.builder()
            .addProgramFiles(inputPath)
            .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
            .setOutput(outputPath, OutputMode.DexIndexed)
            .addProguardConfigurationFiles(keepRulesPath)
            .build());
  }

  static class ReadSourceDebugExtensionAttribute extends ClassVisitor {

    private ReadSourceDebugExtensionAttribute(int api, ClassVisitor cv) {
      super(api, cv);
    }

    private String debugSourceExtension = null;

    @Override
    public void visitSource(String source, String debug) {
      debugSourceExtension = debug;
      super.visitSource(source, debug);
    }
  }

  @Test
  public void testSourceDebugExtensionWithD8() throws Exception {
    Path outputPath = tmpOutputDir.newFolder().toPath();

    AndroidApp result = compileWithD8(INPUT_PATH, outputPath);
    checkAnnotationContent(INPUT_PATH, result);
  }

  /** Check that when dontshrink and dontobfuscate is used the annotation is transmitted. */
  @Test
  public void testSourceDebugExtensionWithShrinking1() throws Exception {
    Path outputPath = tmpOutputDir.newFolder().toPath();
    AndroidApp result = compileWithR8(INPUT_PATH, outputPath, DONT_SHRINK_DONT_OBFUSCATE_CONFIG);
    checkAnnotationContent(INPUT_PATH, result);
  }

  /**
   * Check that when dontshrink is used the annotation is not removed due to obfuscation.
   */
  @Test
  public void testSourceDebugExtensionWithShrinking2() throws Exception {
    Path outputPath = tmpOutputDir.newFolder().toPath();
    AndroidApp result = compileWithR8(INPUT_PATH, outputPath, DONT_SHRINK_CONFIG);
    checkAnnotationContent(INPUT_PATH, result);
  }

  /**
   * Check that the annotation is transmitted when shrinking is enabled with a keepattribute option.
   */
  @Test
  public void testSourceDebugExtensionWithShrinking3() throws Exception {
    Path outputPath = tmpOutputDir.newFolder().toPath();
    AndroidApp result = compileWithR8(INPUT_PATH, outputPath, SHRINK_KEEP_CONFIG);
    checkAnnotationContent(INPUT_PATH, result);
  }

  /**
   * Check that the annotation is removed when shrinking is enabled and that there is not
   * keepattributes option.
   */
  @Test
  public void testSourceDebugExtensionWithShrinking4() throws Exception {
    Path outputPath = tmpOutputDir.newFolder().toPath();

    compileWithR8(INPUT_PATH, outputPath, SHRINK_NO_KEEP_CONFIG);

    CodeInspector codeInspector =
        new CodeInspector(outputPath.resolve("classes.dex"), getGeneratedProguardMap());
    ClassSubject classSubject = codeInspector.clazz("HelloKt");
    AnnotationSubject annotationSubject =
        classSubject.annotation("dalvik.annotation.SourceDebugExtension");
    Assert.assertFalse(annotationSubject.isPresent());
  }

  private void checkAnnotationContent(Path inputPath, AndroidApp androidApp) throws IOException {
    ClassReader classReader = new ClassReader(new FileInputStream(inputPath.toFile()));
    ReadSourceDebugExtensionAttribute sourceDebugExtensionReader =
        new ReadSourceDebugExtensionAttribute(InternalOptions.ASM_VERSION, null);
    classReader.accept(sourceDebugExtensionReader, 0);

    CodeInspector codeInspector = new CodeInspector(androidApp);
    ClassSubject classSubject = codeInspector.clazz("HelloKt");

    AnnotationSubject annotationSubject =
        classSubject.annotation("dalvik.annotation.SourceDebugExtension");
    Assert.assertTrue(annotationSubject.isPresent());
    DexAnnotationElement[] annotationElement = annotationSubject.getAnnotation().elements;
    Assert.assertNotNull(annotationElement);
    Assert.assertTrue(annotationElement.length == 1);
    Assert.assertEquals("value", annotationElement[0].name.toString());
    Assert.assertTrue(annotationElement[0].value.isDexValueString());
    Assert.assertEquals(
        sourceDebugExtensionReader.debugSourceExtension,
        annotationElement[0].value.asDexValueString().value.toSourceString());
  }

  private Path getGeneratedProguardMap() throws IOException {
    Path mapFile = Paths.get(tmpOutputDir.getRoot().getCanonicalPath(), DEFAULT_MAP_FILENAME);
    if (Files.exists(mapFile)) {
      return mapFile.toAbsolutePath();
    }
    return null;
  }
}
