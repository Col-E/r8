// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.inputdependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.InputDependencyGraphConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.IntBox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class InputDependenciesTest extends CompilerApiTestRunner {

  public InputDependenciesTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testInputDependencies() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::run);
  }

  private void checkPathOrigin(Path expected, Origin origin, Path base) {
    assertTrue(origin instanceof PathOrigin);
    assertEquals(base.relativize(expected), base.relativize(((PathOrigin) origin).getPath()));
  }

  private interface Runner {
    void run(Path includePath, Path applymappingPath, InputDependencyGraphConsumer consumer)
        throws Exception;
  }

  private void runTest(Runner test) throws Exception {
    Path dir = temp.newFolder().toPath();
    Path included1 =
        Files.write(
            dir.resolve("included1.rules"), Collections.emptyList(), StandardOpenOption.CREATE_NEW);
    Path included2 =
        Files.write(
            dir.resolve("included2.rules"),
            Arrays.asList("-include included1.rules"),
            StandardOpenOption.CREATE_NEW);
    Path applymapping =
        Files.write(
            dir.resolve("mymapping.txt"), Collections.emptyList(), StandardOpenOption.CREATE_NEW);

    IntBox dependencyCount = new IntBox(0);
    BooleanBox isFinished = new BooleanBox(false);
    test.run(
        included2,
        applymapping,
        new InputDependencyGraphConsumer() {
          @Override
          public void accept(Origin dependent, Path dependency) {
            dependencyCount.increment();
            assertEquals(Origin.unknown(), dependent);
            assertEquals(dir.relativize(applymapping), dir.relativize(dependency));
          }

          @Override
          public void acceptProguardInclude(Origin dependent, Path dependency) {
            dependencyCount.increment();
            if (dependent == Origin.unknown()) {
              assertEquals(dir.relativize(included2), dir.relativize(dependency));
            } else {
              checkPathOrigin(included2, dependent, dir);
              assertEquals(dir.relativize(included1), dir.relativize(dependency));
            }
          }

          @Override
          public void finished() {
            isFinished.set(true);
          }
        });
    assertEquals(3, dependencyCount.get());
    assertTrue(isFinished.get());
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run(
        Path includedFile, Path applymapping, InputDependencyGraphConsumer dependencyConsumer)
        throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addProguardConfiguration(
                  Arrays.asList("-include " + includedFile, "-applymapping" + applymapping),
                  Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setInputDependencyGraphConsumer(dependencyConsumer)
              .build());
    }

    @Test
    public void testInputDependencies() throws Exception {
      Path emptyFile =
          Files.write(
              temp.newFolder().toPath().resolve("empty.txt"),
              Collections.emptyList(),
              StandardOpenOption.CREATE_NEW);
      run(
          emptyFile,
          emptyFile,
          new InputDependencyGraphConsumer() {
            @Override
            public void accept(Origin dependent, Path dependency) {
              // ignore.
            }

            @Override
            public void acceptProguardInclude(Origin dependent, Path dependency) {
              accept(dependent, dependency);
            }

            @Override
            public void acceptProguardInJars(Origin dependent, Path dependency) {
              accept(dependent, dependency);
            }

            @Override
            public void acceptProguardLibraryJars(Origin dependent, Path dependency) {
              accept(dependent, dependency);
            }

            @Override
            public void acceptProguardApplyMapping(Origin dependent, Path dependency) {
              accept(dependent, dependency);
            }

            @Override
            public void acceptProguardObfuscationDictionary(Origin dependent, Path dependency) {
              accept(dependent, dependency);
            }

            @Override
            public void acceptProguardClassObfuscationDictionary(
                Origin dependent, Path dependency) {
              accept(dependent, dependency);
            }

            @Override
            public void acceptProguardPackageObfuscationDictionary(
                Origin dependent, Path dependency) {
              accept(dependent, dependency);
            }

            @Override
            public void finished() {
              // ignore.
            }
          });
    }
  }
}
