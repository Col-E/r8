// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.objectweb.asm.Opcodes.ASM7;

import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;

@RunWith(Parameterized.class)
public class MetadataVersionNumberBumpTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters()
            .withOldCompilersIfSet()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .build());
  }

  public MetadataVersionNumberBumpTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testLessThan1_4() throws Exception {
    final R8FullTestBuilder testBuilder = testForR8(parameters.getBackend());
    rewriteMetadataVersion(testBuilder::addProgramClassFileData, new int[] {1, 1, 16});
    testBuilder
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.testing.keepMetadataInR8IfNotRewritten = false)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(inspector -> inspectMetadataVersion(inspector, "1.4.0"));
  }

  @Test
  public void testEqualTo1_4() throws Exception {
    final R8FullTestBuilder testBuilder = testForR8(parameters.getBackend());
    rewriteMetadataVersion(testBuilder::addProgramClassFileData, new int[] {1, 4, 0});
    testBuilder
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(inspector -> inspectMetadataVersion(inspector, "1.4.0"));
  }

  @Test
  public void testGreaterThan1_4() throws Exception {
    final R8FullTestBuilder testBuilder = testForR8(parameters.getBackend());
    rewriteMetadataVersion(testBuilder::addProgramClassFileData, new int[] {1, 4, 2});
    testBuilder
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(inspector -> inspectMetadataVersion(inspector, "1.4.2"));
  }

  private void rewriteMetadataVersion(Consumer<byte[]> rewrittenBytesConsumer, int[] newVersion)
      throws IOException {
    ZipUtils.iter(
        kotlinc.getKotlinStdlibJar().toString(),
        ((entry, input) -> {
          if (!entry.getName().endsWith(".class") || entry.getName().contains("module-info")) {
            return;
          }
          final byte[] bytes = StreamUtils.streamToByteArrayClose(input);
          final byte[] rewrittenBytes =
              transformMetadataVersion(
                  entry.getName().substring(0, entry.getName().length() - 6), bytes, newVersion);
          rewrittenBytesConsumer.accept(rewrittenBytes);
        }));
  }

  private byte[] transformMetadataVersion(String descriptor, byte[] bytes, int[] newVersion) {
    return transformer(bytes, Reference.classFromDescriptor(descriptor))
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals("Lkotlin/Metadata;")) {
                  return super.visitAnnotation(descriptor, visible);
                } else {
                  return new AnnotationVisitor(ASM7, super.visitAnnotation(descriptor, visible)) {
                    @Override
                    public void visit(String name, Object value) {
                      if (name.equals("mv")) {
                        super.visit(name, newVersion);
                      } else {
                        super.visit(name, value);
                      }
                    }
                  };
                }
              }
            })
        .transform();
  }

  private void inspectMetadataVersion(CodeInspector inspector, String expectedVersion) {
    for (FoundClassSubject clazz : inspector.allClasses()) {
      verifyExpectedVersionForClass(clazz, expectedVersion);
    }
  }

  private void verifyExpectedVersionForClass(FoundClassSubject clazz, String expectedVersion) {
    final AnnotationSubject annotationSubject = clazz.annotation("kotlin.Metadata");
    // TODO(b/164418977): All classes should have an annotation?
    if (!annotationSubject.isPresent()) {
      return;
    }
    final DexAnnotationElement[] elements = annotationSubject.getAnnotation().elements;
    for (DexAnnotationElement element : elements) {
      if (!element.name.toString().equals("mv")) {
        continue;
      }
      final String version =
          Arrays.stream(element.value.asDexValueArray().getValues())
              .map(val -> val.asDexValueInt().value + "")
              .collect(Collectors.joining("."));
      assertEquals(expectedVersion, version);
      return;
    }
    fail("Could not find the mv (metadataVersion) element");
  }
}
