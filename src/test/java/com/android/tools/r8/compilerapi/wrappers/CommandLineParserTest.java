// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.wrappers;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CommandLineParserTest extends CompilerApiTestRunner {

  public CommandLineParserTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void test() throws Exception {
    new ApiTest(ApiTest.PARAMETERS).run();
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run() throws Exception {
      runD8();
      runR8();
    }

    private void runD8() throws CompilationFailedException {
      if (!D8Command.parse(getVersionArgs(), getOrigin()).build().isPrintVersion()) {
        throw new AssertionError("parsing did not find version");
      }
      if (!D8Command.parse(getVersionArgs(), getOrigin(), getHandler()).build().isPrintVersion()) {
        throw new AssertionError("parsing did not find version");
      }

      List<ParseFlagInfo> flags = D8Command.getParseFlagsInformation();
      StringBuilder builder = new StringBuilder();
      new ParseFlagPrinter()
          .setIndent(10)
          .setHelpSeparator(" : ")
          .setHelpColumn(50)
          .addFlags(flags)
          .addFlags(getCustomFlag())
          .appendLinesToBuilder(builder);
      String helpString = builder.toString();
      if (!helpString.contains("          --version")) {
        throw new AssertionError("printing did not include --version");
      }
      if (!helpString.contains("          --my-flag")) {
        throw new AssertionError("printing did not include --my-flag!");
      }
      if (!helpString.contains(" : Some help line")) {
        throw new AssertionError("printing did not include the help info!");
      }
    }

    private void runR8() throws CompilationFailedException {
      if (!R8Command.parse(getVersionArgs(), getOrigin()).build().isPrintVersion()) {
        throw new AssertionError("parsing did not find version");
      }
      if (!R8Command.parse(getVersionArgs(), getOrigin(), getHandler()).build().isPrintVersion()) {
        throw new AssertionError("parsing did not find version");
      }

      List<ParseFlagInfo> flags = R8Command.getParseFlagsInformation();
      StringBuilder builder = new StringBuilder();
      new ParseFlagPrinter()
          .setPrefix("#### ")
          .addFlags(flags)
          .addFlags(getCustomFlag())
          .appendLinesToBuilder(builder);
      String helpString = builder.toString();
      if (!helpString.contains("#### --version")) {
        throw new AssertionError("printing did not include --version");
      }
      if (!helpString.contains("#### --my-flag")) {
        throw new AssertionError("printing did not include --my-flag!");
      }
      if (!helpString.contains("# Some help line")) {
        throw new AssertionError("printing did not include the help info!");
      }
    }

    private String[] getVersionArgs() {
      return new String[] {"--version"};
    }

    private DiagnosticsHandler getHandler() {
      return new DiagnosticsHandler() {};
    }

    private List<ParseFlagInfo> getCustomFlag() {
      return Collections.singletonList(
          new ParseFlagInfo() {
            @Override
            public String getFlagFormat() {
              return "--my-flag <my-arg>";
            }

            @Override
            public List<String> getFlagFormatAlternatives() {
              return Collections.emptyList();
            }

            @Override
            public List<String> getFlagHelp() {
              return Collections.singletonList("Some help line");
            }
          });
    }

    private Origin getOrigin() {
      return new Origin(Origin.root()) {
        @Override
        public String part() {
          return "my-cli-origin";
        }
      };
    }

    @Test
    public void test() throws Exception {
      run();
    }
  }
}
