// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.compilerapi.mockdata.MockClass;
import com.android.tools.r8.compilerapi.mockdata.MockClassWithAssertion;
import com.android.tools.r8.compilerapi.mockdata.PostStartupMockClass;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Base class for any actual API test.
 *
 * <p>Subclasses of this must only use the public API and the otherwise linked libraries (junit,
 * etc).
 */
@RunWith(Parameterized.class)
public abstract class CompilerApiTest {

  public static final Object PARAMETERS = "none";

  public static final String API_TEST_MODE_KEY = "API_TEST_MODE";
  public static final String API_TEST_MODE_EXTERNAL = "external";

  public static final String API_TEST_LIB_KEY = "API_TEST_LIB";
  public static final String API_TEST_LIB_YES = "yes";
  public static final String API_TEST_LIB_NO = "no";

  @Parameters(name = "{0}")
  public static List<Object> data() {
    // Simulate only running the API tests directly on the "none" runtime configuration.
    String runtimes = System.getProperty("runtimes");
    if (runtimes != null && !runtimes.contains("none")) {
      return Collections.emptyList();
    }
    return Collections.singletonList(PARAMETERS);
  }

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  public CompilerApiTest(Object none) {
    assertEquals(PARAMETERS, none);
  }

  /** Predicate to determine if the test is being run externally. */
  public boolean isRunningExternal() {
    return API_TEST_MODE_EXTERNAL.equals(System.getProperty(API_TEST_MODE_KEY));
  }

  /** Predicate to determine if the test is being run for an R8 lib compilation. */
  public boolean isRunningR8Lib() {
    return API_TEST_LIB_YES.equals(System.getProperty(API_TEST_LIB_KEY));
  }

  public Path getNewTempFolder() throws IOException {
    return temp.newFolder().toPath();
  }

  public Class<?> getMockClass() {
    return MockClass.class;
  }

  public Class<?> getMockClassWithAssertion() {
    return MockClassWithAssertion.class;
  }

  public Class<?> getPostStartupMockClass() {
    return PostStartupMockClass.class;
  }

  public static Path getProjectRoot() {
    String userDirProperty = System.getProperty("user.dir");
    if (userDirProperty.endsWith("d8_r8/test")) {
      return Paths.get(userDirProperty).getParent().getParent();
    }
    return Paths.get("");
  }

  public Path getJava8RuntimeJar() {
    return getProjectRoot()
        .resolve(Paths.get("third_party", "openjdk", "openjdk-rt-1.8", "rt.jar"));
  }

  public Path getAndroidJar() {
    return getProjectRoot()
        .resolve(Paths.get("third_party", "android_jar", "lib-v33", "android.jar"));
  }

  public List<String> getKeepMainRules(Class<?> clazz) {
    return Collections.singletonList(
        "-keep class " + clazz.getName() + " { public static void main(java.lang.String[]); }");
  }

  public Path getPathForClass(Class<?> clazz) {
    String file = clazz.getName().replace('.', '/') + ".class";
    return Paths.get("build", "classes", "java", "test", file);
  }

  public byte[] getBytesForClass(Class<?> clazz) throws IOException {
    return Files.readAllBytes(getPathForClass(clazz));
  }
}
