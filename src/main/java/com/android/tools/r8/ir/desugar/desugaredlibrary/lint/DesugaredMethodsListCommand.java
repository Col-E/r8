// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Keep
public class DesugaredMethodsListCommand {

  private final boolean help;
  private final boolean version;
  private final int minApi;
  private final StringResource desugarLibrarySpecification;
  private final Collection<ProgramResourceProvider> desugarLibraryImplementation;
  private final StringConsumer outputConsumer;
  private final Collection<ClassFileResourceProvider> library;

  DesugaredMethodsListCommand(
      int minApi,
      StringResource desugarLibrarySpecification,
      Collection<ProgramResourceProvider> desugarLibraryImplementation,
      StringConsumer outputConsumer,
      Collection<ClassFileResourceProvider> library) {
    this.help = false;
    this.version = false;
    this.minApi = minApi;
    this.desugarLibrarySpecification = desugarLibrarySpecification;
    this.desugarLibraryImplementation = desugarLibraryImplementation;
    this.outputConsumer = outputConsumer;
    this.library = library;
  }

  DesugaredMethodsListCommand(boolean help, boolean version) {
    this.help = help;
    this.version = version;
    this.minApi = -1;
    this.desugarLibrarySpecification = null;
    this.desugarLibraryImplementation = null;
    this.outputConsumer = null;
    this.library = null;
  }

  public int getMinApi() {
    return minApi;
  }

  public StringResource getDesugarLibrarySpecification() {
    return desugarLibrarySpecification;
  }

  public Collection<ProgramResourceProvider> getDesugarLibraryImplementation() {
    return desugarLibraryImplementation;
  }

  public StringConsumer getOutputConsumer() {
    return outputConsumer;
  }

  public Collection<ClassFileResourceProvider> getLibrary() {
    return library;
  }

  public boolean isHelp() {
    return help;
  }

  public boolean isVersion() {
    return version;
  }

  public static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(builder, "Usage: desugaredmethods [options] where  options are:");
    new ParseFlagPrinter()
        .addFlags(ImmutableList.copyOf(DesugaredMethodsListCommandParser.getFlags()))
        .appendLinesToBuilder(builder);
    return builder.toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  @Keep
  public static class Builder {

    private int minApi = AndroidApiLevel.B.getLevel();
    private StringResource desugarLibrarySpecification = null;
    private Collection<ProgramResourceProvider> desugarLibraryImplementation = new ArrayList<>();
    private StringConsumer outputConsumer;
    private Collection<ClassFileResourceProvider> library = new ArrayList<>();

    private boolean help = false;
    private boolean version = false;

    public Builder setMinApi(int minApi) {
      this.minApi = minApi;
      return this;
    }

    public Builder setDesugarLibrarySpecification(StringResource desugarLibrarySpecification) {
      this.desugarLibrarySpecification = desugarLibrarySpecification;
      return this;
    }

    public Builder setOutputConsumer(StringConsumer outputConsumer) {
      this.outputConsumer = outputConsumer;
      return this;
    }

    public Builder setOutputPath(Path outputPath) {
      this.outputConsumer =
          new StringConsumer.FileConsumer(outputPath) {
            @Override
            public void accept(String string, DiagnosticsHandler handler) {
              super.accept(string, handler);
              super.accept(System.lineSeparator(), handler);
            }
          };
      return this;
    }

    public Builder addDesugarLibraryImplementation(
        ProgramResourceProvider programResourceProvider) {
      desugarLibraryImplementation.add(programResourceProvider);
      return this;
    }

    public Builder addLibrary(ClassFileResourceProvider classFileResourceProvider) {
      library.add(classFileResourceProvider);
      return this;
    }

    public Builder setHelp() {
      this.help = true;
      return this;
    }

    public Builder setVersion() {
      this.version = true;
      return this;
    }

    public DesugaredMethodsListCommand build() {
      // The min-api level defaults to 1, it's always present.
      // If desugarLibraryImplementation is empty, this generates only the backported method list.

      if (help || version) {
        return new DesugaredMethodsListCommand(help, version);
      }

      if (desugarLibrarySpecification != null && library.isEmpty()) {
        throw new CompilationError("With desugared library configuration a library is required");
      }

      if (!desugarLibraryImplementation.isEmpty() && desugarLibrarySpecification == null) {
        throw new CompilationError(
            "desugarLibrarySpecification is required when desugared library implementation is"
                + " present.");
      }

      if (outputConsumer == null) {
        outputConsumer =
            new StringConsumer() {
              @Override
              public void accept(String string, DiagnosticsHandler handler) {
                System.out.println(string);
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            };
      }
      return new DesugaredMethodsListCommand(
          minApi,
          desugarLibrarySpecification,
          desugarLibraryImplementation,
          outputConsumer,
          library);
    }
  }

  public static class DesugaredMethodsListCommandParser {

    static List<ParseFlagInfo> getFlags() {
      return ImmutableList.<ParseFlagInfo>builder()
          .add(ParseFlagInfoImpl.getOutput())
          .add(ParseFlagInfoImpl.getLib())
          .add(ParseFlagInfoImpl.getMinApi())
          .add(ParseFlagInfoImpl.getVersion("DesugaredMethods"))
          .add(ParseFlagInfoImpl.getHelp())
          .add(ParseFlagInfoImpl.getDesugaredLib())
          .add(
              ParseFlagInfoImpl.flag1(
                  "--desugared-lib-jar", "<file>", "Specify desugared library jar."))
          .build();
    }

    public DesugaredMethodsListCommand parse(String[] args, Origin origin) throws IOException {
      DesugaredMethodsListCommand.Builder builder = DesugaredMethodsListCommand.builder();
      for (int i = 0; i < args.length; i += 2) {
        String arg = args[i].trim();
        if (arg.length() == 0) {
          continue;
        } else if (arg.equals("--help")) {
          builder.setHelp();
          continue;
        } else if (arg.equals("--version")) {
          builder.setVersion();
          continue;
        }
        String argValue = args[i + 1].trim();
        if (arg.equals("--min-api")) {
          builder.setMinApi(Integer.parseInt(argValue));
        } else if (arg.equals("--desugared-lib")) {
          builder.setDesugarLibrarySpecification(StringResource.fromFile(Paths.get(argValue)));
        } else if (arg.equals("--desugared-lib-jar")) {
          builder.addDesugarLibraryImplementation(
              ArchiveProgramResourceProvider.fromArchive(Paths.get(argValue)));
        } else if (arg.equals("--output")) {
          builder.setOutputPath(Paths.get(argValue));
        } else if (arg.equals("--lib")) {
          builder.addLibrary(new ArchiveClassFileProvider(Paths.get(argValue)));
        }
      }

      return builder.build();
    }
  }
}
