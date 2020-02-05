// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Keep
public class BackportedMethodListCommand {

  private final boolean printHelp;
  private final boolean printVersion;
  private final Reporter reporter;
  private final int minApiLevel;
  private final StringConsumer backportedMethodListConsumer;

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  Reporter getReporter() {
    return reporter;
  }

  public int getMinApiLevel() {
    return minApiLevel;
  }

  public StringConsumer getBackportedMethodListConsumer() {
    return backportedMethodListConsumer;
  }

  private BackportedMethodListCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    this.reporter = new Reporter();
    this.minApiLevel = -1;
    this.backportedMethodListConsumer = null;
  }

  private BackportedMethodListCommand(
      Reporter reporter,
      int minApiLevel,
      StringConsumer backportedMethodListConsumer) {
    this.printHelp = false;
    this.printVersion = false;
    this.reporter = reporter;
    this.minApiLevel = minApiLevel;
    this.backportedMethodListConsumer = backportedMethodListConsumer;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  public static Builder parse(String[] args) {
    final Set<String> OPTIONS_WITH_PARAMETER = ImmutableSet.of("--output", "--min-api");

    boolean hasDefinedApiLevel = false;
    Builder builder = builder();
    if (args.length == 0) {
      builder.setPrintHelp(true);
      return builder;
    }
    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_PARAMETER.contains(arg)) {
        if (++i < args.length) {
          nextArg = args[i];
        } else {
          builder.error(new StringDiagnostic("Missing parameter for " + args[i - 1] + "."));
          break;
        }
      }
      if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals(" --min-api <number>")) {
        if (hasDefinedApiLevel) {
          builder.error(new StringDiagnostic("Cannot set multiple --min-api options"));
        } else {
          parseMinApi(builder, nextArg);
          hasDefinedApiLevel = true;
        }
      } else if (arg.equals("--output")) {
        builder.setOutputPath(Paths.get(nextArg));
      } else {
        builder.error(new StringDiagnostic("Unknown option: " + arg));
      }
    }
    return builder;
  }

  private static void parseMinApi(Builder builder, String minApiString) {
    int minApi;
    try {
      minApi = Integer.parseInt(minApiString);
    } catch (NumberFormatException e) {
      builder.error(new StringDiagnostic("Invalid argument to --min-api: " + minApiString));
      return;
    }
    if (minApi < 1) {
      builder.error(new StringDiagnostic("Invalid argument to --min-api: " + minApiString));
      return;
    }
    builder.setMinApiLevel(minApi);
  }

  @Keep
  public static class Builder {

    private final Reporter reporter;
    private int minApiLevel;
    private StringConsumer backportedMethodListConsumer;
    private boolean printHelp = false;
    private boolean printVersion = false;

    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    public Builder setMinApiLevel(int minApiLevel) {
      if (minApiLevel <= 0) {
        reporter.error(new StringDiagnostic("Invalid minApiLevel: " + minApiLevel));
      } else {
        this.minApiLevel = minApiLevel;
      }
      return this;
    }

    public Builder setOutputPath(Path outputPath) {
      backportedMethodListConsumer =
          new StringConsumer.FileConsumer(outputPath) {
            @Override
            public void accept(String string, DiagnosticsHandler handler) {
              super.accept(string, handler);
              super.accept(System.lineSeparator(), handler);
            }
          };
      return this;
    }

    public Builder setConsumer(StringConsumer consumer) {
      this.backportedMethodListConsumer = consumer;
      return this;
    }

    public boolean isPrintHelp() {
      return printHelp;
    }

    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    public boolean isPrintVersion() {
      return printVersion;
    }

    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    private void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }

    public BackportedMethodListCommand build() {
      if (isPrintHelp() || isPrintVersion()) {
        return new BackportedMethodListCommand(isPrintHelp(), isPrintVersion());
      }
      if (backportedMethodListConsumer == null) {
        backportedMethodListConsumer =
            new StringConsumer() {
              @Override
              public void accept(String string, DiagnosticsHandler handler) {
                System.out.println(string);
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            };
      }
      return new BackportedMethodListCommand(
          reporter, minApiLevel, backportedMethodListConsumer);
    }
  }
}
