// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.utils.FileUtils.isArchive;
import static com.android.tools.r8.utils.FileUtils.isClassFile;
import static com.android.tools.r8.utils.FileUtils.isDexFile;
import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

@KeepForApi
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

  public static Builder parse(Collection<String> args, Origin origin) {
    return TraceReferencesCommandParser.parse(args.toArray(new String[args.size()]), origin);
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  @KeepForApi
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

    private static String extractClassDescriptor(byte[] data) {
      class ClassNameExtractor extends ClassVisitor {
        private String className;

        private ClassNameExtractor() {
          super(ASM_VERSION);
        }

        @Override
        public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
          className = name;
        }

        String getClassInternalType() {
          return className;
        }
      }

      ClassReader reader = new ClassReader(data);
      ClassNameExtractor extractor = new ClassNameExtractor();
      reader.accept(
          extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return "L" + extractor.getClassInternalType() + ";";
    }

    private static class SingleClassClassFileResourceProvider implements ClassFileResourceProvider {
      private final String descriptor;
      private final ProgramResource programResource;

      SingleClassClassFileResourceProvider(Origin origin, byte[] data) {
        this.descriptor = extractClassDescriptor(data);
        this.programResource =
            ProgramResource.fromBytes(origin, Kind.CF, data, ImmutableSet.of(descriptor));
      }

      @Override
      public Set<String> getClassDescriptors() {
        return ImmutableSet.of(descriptor);
      }

      @Override
      public ProgramResource getProgramResource(String descriptor) {
        return descriptor.equals(this.descriptor) ? programResource : null;
      }
    }

    private ClassFileResourceProvider singleClassFileClassFileResourceProvider(Path file)
        throws IOException {
      return new SingleClassClassFileResourceProvider(
          new PathOrigin(file), Files.readAllBytes(file));
    }

    private ProgramResourceProvider singleClassFileProgramResourceProvider(Path file)
        throws IOException {
      byte[] bytes = Files.readAllBytes(file);
      String descriptor = extractClassDescriptor(bytes);
      return new ProgramResourceProvider() {

        @Override
        public Collection<ProgramResource> getProgramResources() {
          return ImmutableList.of(
              ProgramResource.fromBytes(
                  new PathOrigin(file), Kind.CF, bytes, ImmutableSet.of(descriptor)));
        }
      };
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
      } else if (isClassFile(file)) {
        try {
          builder.add(singleClassFileClassFileResourceProvider(file));
        } catch (IOException e) {
          error(new ExceptionDiagnostic(e));
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
      } else if (isClassFile(file)) {
        try {
          traceSourceBuilder.add(singleClassFileProgramResourceProvider(file));
        } catch (IOException e) {
          error(new ExceptionDiagnostic(e));
        }
      } else if (isDexFile(file)) {
        traceSourceBuilder.add(
            new ProgramResourceProvider() {
              ProgramResource dexResource = ProgramResource.fromFile(Kind.DEX, file);

              @Override
              public Collection<ProgramResource> getProgramResources() {
                return Collections.singletonList(dexResource);
              }
            });
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

    public Builder setConsumer(TraceReferencesConsumer consumer) {
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

  InternalOptions getInternalOptions() {
    InternalOptions options = new InternalOptions();
    options.loadAllClassDefinitions = true;
    TraceReferencesConsumer consumer = getConsumer();
    DumpOptions.Builder builder =
        DumpOptions.builder(Tool.TraceReferences)
            .readCurrentSystemProperties()
            // The behavior of TraceReferences greatly differs depending if we have a CheckConsumer
            // or a KeepRules consumer. We log the consumer type and obfuscation if relevant.
            .setTraceReferencesConsumer(consumer.getClass().getName());
    if (consumer instanceof TraceReferencesKeepRules) {
      builder.setMinification(((TraceReferencesKeepRules) consumer).allowObfuscation());
    }
    options.dumpOptions = builder.build();
    return options;
  }
}
