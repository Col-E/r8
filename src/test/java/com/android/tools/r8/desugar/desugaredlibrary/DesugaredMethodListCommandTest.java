// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.DesugaredMethodsListCommand;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredMethodListCommandTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().build(),
        ImmutableList.of(JDK8, JDK11_MINIMAL, JDK11, JDK11_PATH, JDK11_LEGACY));
  }

  public DesugaredMethodListCommandTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testErrorPlatformDesugaredLibrary() throws IOException {
    TestDiagnosticMessagesImpl diagnosticMessages = new TestDiagnosticMessagesImpl();
    DesugaredMethodsListCommand.parse(
        new String[] {
          "--android-platform-build",
          "--desugared-lib",
          libraryDesugaringSpecification.getSpecification().toString(),
          "--desugared-lib-jar",
          libraryDesugaringSpecification.getDesugarJdkLibs().iterator().next().toString(),
          "--lib",
          ToolHelper.getAndroidJar(34).toString()
        },
        new Reporter(diagnosticMessages));
    diagnosticMessages.assertErrorMessageThatMatches(
        containsString("With platform build desugared library is not allowed."));
    diagnosticMessages.assertOnlyErrors();
  }

  @Test
  public void testErrorDesugaredLibraryNoLib() throws IOException {
    TestDiagnosticMessagesImpl diagnosticMessages = new TestDiagnosticMessagesImpl();
    DesugaredMethodsListCommand.parse(
        new String[] {
          "--desugared-lib", libraryDesugaringSpecification.getSpecification().toString()
        },
        new Reporter(diagnosticMessages));
    diagnosticMessages.assertOnlyErrors();
    diagnosticMessages.assertErrorMessageThatMatches(
        containsString("With desugared library specification a library is required."));
  }

  @Test
  public void testErrorDesugaredLibraryImplementationNoSpec() throws IOException {
    TestDiagnosticMessagesImpl diagnosticMessages = new TestDiagnosticMessagesImpl();
    DesugaredMethodsListCommand.parse(
        new String[] {
          "--desugared-lib-jar",
          libraryDesugaringSpecification.getDesugarJdkLibs().iterator().next().toString()
        },
        new Reporter(diagnosticMessages));
    diagnosticMessages.assertOnlyErrors();
    diagnosticMessages.assertErrorMessageThatMatches(
        containsString(
            "The desugar library specification is required when desugared library "
                + "implementation is present."));
  }

  @Test
  public void testMissingArg() throws IOException {
    TestDiagnosticMessagesImpl diagnosticMessages = new TestDiagnosticMessagesImpl();
    DesugaredMethodsListCommand.parse(
        new String[] {"--desugared-lib"}, new Reporter(diagnosticMessages));
    diagnosticMessages.assertOnlyErrors();
    diagnosticMessages.assertErrorMessageThatMatches(
        containsString("Missing value for arg --desugared-lib"));
  }

  @Test
  public void testFullCommand() throws Exception {
    List<String> commandList = new ArrayList<>();
    commandList.add("--min-api");
    commandList.add(String.valueOf(21));
    commandList.add("--desugared-lib");
    commandList.add(libraryDesugaringSpecification.getSpecification().toString());
    for (Path desugarJdkLib : libraryDesugaringSpecification.getDesugarJdkLibs()) {
      commandList.add("--desugared-lib-jar");
      commandList.add(desugarJdkLib.toString());
    }
    commandList.add("--lib");
    commandList.add(ToolHelper.getAndroidJar(34).toString());
    String[] commandArray = commandList.stream().toArray(String[]::new);
    DesugaredMethodsListCommand command = DesugaredMethodsListCommand.parse(commandArray);
    assertFalse(command.getDesugarLibraryImplementation().isEmpty());
    assertNotNull(command.getDesugarLibrarySpecification().getString());
    assertFalse(command.getLibrary().isEmpty());
    assertFalse(command.isAndroidPlatformBuild());
    assertFalse(command.isVersion());
    assertFalse(command.isHelp());
  }
}
