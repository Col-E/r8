// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.classconflictresolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassConflictResolver;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.BooleanBox;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import org.junit.Test;

public class ClassConflictResolverTest extends CompilerApiTestRunner {

  public ClassConflictResolverTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testD8() throws Exception {
    Origin originA = new PathOrigin(Paths.get("SourceA"));
    Origin originB = new PathOrigin(Paths.get("SourceB"));
    BooleanBox called = new BooleanBox(false);
    new ApiTest(ApiTest.PARAMETERS)
        .runD8(
            originA,
            originB,
            (reference, origins, handler) -> {
              called.set(true);
              assertEquals(ImmutableSet.of(originA, originB), ImmutableSet.copyOf(origins));
              return originA;
            });
    assertTrue(called.get());
  }

  @Test
  public void testR8() throws Exception {
    Origin originA = new PathOrigin(Paths.get("SourceA"));
    Origin originB = new PathOrigin(Paths.get("SourceB"));
    BooleanBox called = new BooleanBox(false);
    new ApiTest(ApiTest.PARAMETERS)
        .runR8(
            originA,
            originB,
            (reference, origins, handler) -> {
              called.set(true);
              assertEquals(ImmutableSet.of(originA, originB), ImmutableSet.copyOf(origins));
              return originA;
            });
    assertTrue(called.get());
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(Origin originA, Origin originB, ClassConflictResolver resolver)
        throws Exception {
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), originA)
              .addClassProgramData(getBytesForClass(getMockClass()), originB)
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setClassConflictResolver(resolver)
              .build());
    }

    public void runR8(Origin originA, Origin originB, ClassConflictResolver resolver)
        throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), originA)
              .addClassProgramData(getBytesForClass(getMockClass()), originB)
              .addLibraryFiles(getJava8RuntimeJar())
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setClassConflictResolver(resolver)
              .build());
    }

    @Test
    public void testD8() throws Exception {
      Origin originA = new PathOrigin(Paths.get("SourceA"));
      Origin originB = new PathOrigin(Paths.get("SourceB"));
      runD8(originA, originB, (reference, origins, handler) -> originA);
    }

    @Test
    public void testR8() throws Exception {
      Origin originA = new PathOrigin(Paths.get("SourceA"));
      Origin originB = new PathOrigin(Paths.get("SourceB"));
      runR8(originA, originB, (reference, origins, handler) -> originA);
    }
  }
}
