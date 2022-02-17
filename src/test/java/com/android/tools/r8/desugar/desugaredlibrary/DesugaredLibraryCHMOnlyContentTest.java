// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryCHMOnlyContentTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DesugaredLibraryCHMOnlyContentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDesugaredLibraryContentCHMOnlyD8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    Path desugaredLib =
        buildDesugaredLibrary(
            parameters.getApiLevel(),
            "",
            false,
            Collections.emptyList(),
            options ->
                setDesugaredLibrarySpecificationForTesting(
                    options, chmOnlyConfiguration(options, true, parameters)));
    CodeInspector inspector = new CodeInspector(desugaredLib);
    assert inspector.clazz("j$.util.concurrent.ConcurrentHashMap").isPresent();
  }

  @Test
  public void testDesugaredLibraryContentCHMOnlyR8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    Path desugaredLib =
        buildDesugaredLibrary(
            parameters.getApiLevel(),
            "-keep class * { *; }",
            true,
            Collections.emptyList(),
            options ->
                setDesugaredLibrarySpecificationForTesting(
                    options, chmOnlyConfiguration(options, true, parameters)));
    CodeInspector inspector = new CodeInspector(desugaredLib);
    assert inspector.clazz("j$.util.concurrent.ConcurrentHashMap").isPresent();
  }

  DesugaredLibrarySpecification chmOnlyConfiguration(
      InternalOptions options, boolean libraryCompilation, TestParameters parameters) {
    return DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
        StringResource.fromFile(ToolHelper.getCHMOnlyDesugarLibJsonForTesting()),
        options.dexItemFactory(),
        options.reporter,
        libraryCompilation,
        parameters.getApiLevel().getLevel());
  }
}
