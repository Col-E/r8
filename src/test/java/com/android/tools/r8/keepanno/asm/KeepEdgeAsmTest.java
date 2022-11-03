// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.testsource.KeepClassAndDefaultConstructorSource;
import com.android.tools.r8.keepanno.testsource.KeepSourceEdges;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassTransformer;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.AnnotationVisitor;

@RunWith(Parameterized.class)
public class KeepEdgeAsmTest extends TestBase {

  private static final Class<?> SOURCE = KeepClassAndDefaultConstructorSource.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepEdgeAsmTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testAsmReader() throws Exception {
    Set<KeepEdge> expectedEdges = KeepSourceEdges.getExpectedEdges(SOURCE);
    ClassReference clazz = Reference.classFromClass(SOURCE);
    // Original bytes of the test class.
    byte[] original = ToolHelper.getClassAsBytes(SOURCE);
    // Strip out all the annotations to ensure they are actually added again.
    byte[] stripped =
        transformer(SOURCE)
            .addClassTransformer(
                new ClassTransformer() {
                  @Override
                  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    // Ignore all input annotations.
                    return null;
                  }
                })
            .transform();
    // Manually add in the expected edges again.
    byte[] readded =
        transformer(stripped, clazz)
            .addClassTransformer(
                new ClassTransformer() {

                  @Override
                  public void visitEnd() {
                    for (KeepEdge edge : expectedEdges) {
                      KeepEdgeWriter.writeEdge(edge, super::visitAnnotation);
                    }
                    super.visitEnd();
                  }
                })
            .transform();

    // Read the edges from each version.
    Set<KeepEdge> originalEdges = KeepEdgeReader.readKeepEdges(original);
    Set<KeepEdge> strippedEdges = KeepEdgeReader.readKeepEdges(stripped);
    Set<KeepEdge> readdedEdges = KeepEdgeReader.readKeepEdges(readded);

    // The edges are compared to the "expected" ast to ensure we don't hide failures in reading or
    // writing.
    assertEquals(Collections.emptySet(), strippedEdges);
    assertEquals(expectedEdges, originalEdges);
    assertEquals(expectedEdges, readdedEdges);
  }
}
