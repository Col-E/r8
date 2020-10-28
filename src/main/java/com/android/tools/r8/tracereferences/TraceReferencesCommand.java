// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.utils.FileUtils.isArchive;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Keep
public class TraceReferencesCommand {
  private final boolean printHelp;
  private final boolean printVersion;
  private final Reporter reporter;
  private final ImmutableList<ClassFileResourceProvider> library;
  private final ImmutableList<ClassFileResourceProvider> traceTarget;
  private final ImmutableList<ProgramResourceProvider> traceSource;
  private final TraceReferencesConsumer consumer;

  TraceReferencesCommand(
      boolean printHelp,
      boolean printVersion,
      Reporter reporter,
      ImmutableList<ClassFileResourceProvider> library,
      ImmutableList<ClassFileResourceProvider> traceTarget,
      ImmutableList<ProgramResourceProvider> traceSource,
      TraceReferencesConsumer consumer) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    this.reporter = reporter;
    this.library = library;
    this.traceTarget = traceTarget;
    this.traceSource = traceSource;
    this.consumer = consumer;
  }

  TraceReferencesCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    this.reporter = null;
    this.library = null;
    this.traceTarget = null;
    this.traceSource = null;
    this.consumer = null;
  }

  /**
   * Utility method for obtaining a <code>ReferenceTraceCommand.Builder</code>.
   *
   * @param diagnosticsHandler The diagnostics handler for consuming messages.
   */
  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  /**
   * Utility method for obtaining a <code>ReferenceTraceCommand.Builder</code> with a default
   * diagnostics handler.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static Builder parse(String[] args, Origin origin) {
    return TraceReferencesCommandParser.parse(args, origin);
  }

  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler diagnosticsHandler) {
    return TraceReferencesCommandParser.parse(args, origin, diagnosticsHandler);
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  public static class Builder {

    private boolean printHelp = false;
    private boolean printVersion = false;
    private final Reporter reporter;
    private final ImmutableList.Builder<ClassFileResourceProvider> libraryBuilder =
        ImmutableList.builder();
    private final ImmutableList.Builder<ClassFileResourceProvider> traceTargetBuilder =
        ImmutableList.builder();
    private final ImmutableList.Builder<ProgramResourceProvider> traceSourceBuilder =
        ImmutableList.builder();
    private TraceReferencesConsumer consumer;

    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    Reporter getReporter() {
      return reporter;
    }

    /** True if the print-help flag is enabled. */
    public boolean isPrintHelp() {
      return printHelp;
    }

    /** Set the value of the print-help flag. */
    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    /** True if the print-version flag is enabled. */
    public boolean isPrintVersion() {
      return printVersion;
    }

    /** Set the value of the print-version flag. */
    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    private void addLibraryOrTargetFile(
        Path file, ImmutableList.Builder<ClassFileResourceProvider> builder) {
      if (!Files.exists(file)) {
        PathOrigin pathOrigin = new PathOrigin(file);
        NoSuchFileException noSuchFileException = new NoSuchFileException(file.toString());
        error(new ExceptionDiagnostic(noSuchFileException, pathOrigin));
      }
      if (isArchive(file)) {
        try {
          ArchiveClassFileProvider provider = new ArchiveClassFileProvider(file);
          builder.add(provider);
        } catch (IOException e) {
          error(new ExceptionDiagnostic(e, new PathOrigin(file)));
        }
      } else {
        error(new StringDiagnostic("Unsupported source file type", new PathOrigin(file)));
      }
    }

    private void addSourceFile(Path file) {
      if (!Files.exists(file)) {
        PathOrigin pathOrigin = new PathOrigin(file);
        NoSuchFileException noSuchFileException = new NoSuchFileException(file.toString());
        error(new ExceptionDiagnostic(noSuchFileException, pathOrigin));
      }
      if (isArchive(file)) {
        traceSourceBuilder.add(ArchiveResourceProvider.fromArchive(file, false));
      } else {
        error(new StringDiagnostic("Unsupported source file type", new PathOrigin(file)));
      }
    }

    public Builder addLibraryResourceProvider(ClassFileResourceProvider provider) {
      libraryBuilder.add(provider);
      return this;
    }

    public Builder addLibraryFiles(Path... files) {
      addLibraryFiles(Arrays.asList(files));
      return this;
    }

    public Builder addLibraryFiles(Collection<Path> files) {
      for (Path file : files) {
        addLibraryOrTargetFile(file, libraryBuilder);
      }
      return this;
    }

    public Builder addTargetFiles(Path... files) {
      addTargetFiles(Arrays.asList(files));
      return this;
    }

    public Builder addTargetFiles(Collection<Path> files) {
      for (Path file : files) {
        addLibraryOrTargetFile(file, traceTargetBuilder);
      }
      return this;
    }

    public Builder addSourceFiles(Path... files) {
      addSourceFiles(Arrays.asList(files));
      return this;
    }

    public Builder addSourceFiles(Collection<Path> files) {
      for (Path file : files) {
        addSourceFile(file);
      }
      return this;
    }

    Builder setConsumer(TraceReferencesConsumer consumer) {
      this.consumer = consumer;
      return this;
    }

    private TraceReferencesCommand makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new TraceReferencesCommand(isPrintHelp(), isPrintVersion());
      }

      ImmutableList<ClassFileResourceProvider> library = libraryBuilder.build();
      ImmutableList<ClassFileResourceProvider> traceTarget = traceTargetBuilder.build();
      ImmutableList<ProgramResourceProvider> traceSource = traceSourceBuilder.build();

      if (library.isEmpty()) {
        error(new StringDiagnostic("No library specified"));
      }
      if (traceTarget.isEmpty()) {
        // Target can be empty for tracing references from source outside of library.
      }
      if (traceSource.isEmpty()) {
        error(new StringDiagnostic("No source specified"));
      }
      if (consumer == null) {
        error(new StringDiagnostic("No consumer specified"));
      }
      return new TraceReferencesCommand(
          printHelp, printVersion, reporter, library, traceTarget, traceSource, consumer);
    }

    public final TraceReferencesCommand build() throws CompilationFailedException {
      Box<TraceReferencesCommand> box = new Box<>(null);
      ExceptionUtils.withCompilationHandler(
          reporter,
          () -> {
            box.set(makeCommand());
            reporter.failIfPendingErrors();
          });
      return box.get();
    }

    void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
      // For now all errors are fatal.
    }
  }

  Reporter getReporter() {
    return reporter;
  }

  List<ClassFileResourceProvider> getLibrary() {
    return library;
  }

  List<ClassFileResourceProvider> getTarget() {
    return traceTarget;
  }

  List<ProgramResourceProvider> getSource() {
    return traceSource;
  }

  TraceReferencesConsumer getConsumer() {
    return consumer;
  }
}
