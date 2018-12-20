// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.apiusagesample;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class R8ApiUsageSample {

  private static final Origin origin =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return "R8ApiUsageSample";
        }
      };

  private static final DiagnosticsHandler handler = new D8DiagnosticsHandler();

  /**
   * Example invocation:
   *
   * <pre>
   *   java -jar r8-api-uses.jar \
   *     --output path/to/output/dir \
   *     --min-api minApiLevel \
   *     --lib path/to/library.jar \
   *     path/to/input{1,2,3}.{jar,class}
   * </pre>
   */
  public static void main(String[] args) {
    // Parse arguments with the commandline parser to make use of its API.
    R8Command.Builder cmd = R8Command.parse(args, origin);
    CompilationMode mode = cmd.getMode();
    Path temp = cmd.getOutputPath();
    int minApiLevel = cmd.getMinApiLevel();
    // The Builder API does not provide access to the concrete paths
    // (everything is put into providers) so manually parse them here.
    List<Path> libraries = new ArrayList<>(1);
    List<Path> mainDexList = new ArrayList<>(1);
    List<Path> mainDexRules = new ArrayList<>(1);
    List<Path> pgConf = new ArrayList<>(1);
    List<Path> inputs = new ArrayList<>(args.length);
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--lib")) {
        libraries.add(Paths.get(args[++i]));
      } else if (args[i].equals("--main-dex-list")) {
        mainDexList.add(Paths.get(args[++i]));
      } else if (args[i].equals("--main-dex-rules")) {
        mainDexRules.add(Paths.get(args[++i]));
      } else if (args[i].equals("--pg-conf")) {
        pgConf.add(Paths.get(args[++i]));
      } else if (isArchive(args[i]) || isClassFile(args[i])) {
        inputs.add(Paths.get(args[i]));
      }
    }
    if (!Files.exists(temp) || !Files.isDirectory(temp)) {
      throw new RuntimeException("Must supply a temp/output directory");
    }
    if (inputs.isEmpty()) {
      throw new RuntimeException("Must supply program inputs");
    }
    if (libraries.isEmpty()) {
      throw new RuntimeException("Must supply library inputs");
    }
    if (mainDexList.isEmpty()) {
      throw new RuntimeException("Must supply main-dex-list inputs");
    }
    if (mainDexRules.isEmpty()) {
      throw new RuntimeException("Must supply main-dex-rules inputs");
    }
    if (pgConf.isEmpty()) {
      throw new RuntimeException("Must supply pg-conf inputs");
    }

    useProgramFileList(CompilationMode.DEBUG, minApiLevel, libraries, inputs);
    useProgramFileList(CompilationMode.RELEASE, minApiLevel, libraries, inputs);
    useProgramData(minApiLevel, libraries, inputs);
    useProgramResourceProvider(minApiLevel, libraries, inputs);
    useLibraryResourceProvider(minApiLevel, libraries, inputs);
    useMainDexListFiles(minApiLevel, libraries, inputs, mainDexList);
    useMainDexClasses(minApiLevel, libraries, inputs, mainDexList);
    useMainDexRulesFiles(minApiLevel, libraries, inputs, mainDexRules);
    useMainDexRules(minApiLevel, libraries, inputs, mainDexRules);
    useProguardConfigFiles(minApiLevel, libraries, inputs, mainDexList, pgConf);
    useProguardConfigLines(minApiLevel, libraries, inputs, mainDexList, pgConf);
    useVArgVariants(minApiLevel, libraries, inputs, mainDexList, mainDexRules, pgConf);
  }

  // Check API support for compiling Java class-files from the file system.
  private static void useProgramFileList(
      CompilationMode mode, int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
    try {
      R8.run(
          R8Command.builder(handler)
              .setMode(mode)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries)
              .addProgramFiles(inputs)
              .build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    }
  }

  // Check API support for compiling Java class-files from byte content.
  private static void useProgramData(
      int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
    try {
      R8Command.Builder builder =
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries);
      for (ClassFileContent classfile : readClassFiles(inputs)) {
        builder.addClassProgramData(classfile.data, classfile.origin);
      }
      R8.run(builder.build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IO exception", e);
    }
  }

  // Check API support for compiling Java class-files from a program provider abstraction.
  private static void useProgramResourceProvider(
      int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
    try {
      R8Command.Builder builder =
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries);
      for (Path input : inputs) {
        if (isArchive(input)) {
          builder.addProgramResourceProvider(
              ArchiveProgramResourceProvider.fromArchive(
                  input, ArchiveProgramResourceProvider::includeClassFileEntries));
        } else {
          builder.addProgramResourceProvider(
              new ProgramResourceProvider() {
                @Override
                public Collection<ProgramResource> getProgramResources() throws ResourceException {
                  return Collections.singleton(ProgramResource.fromFile(Kind.CF, input));
                }
              });
        }
      }
      R8.run(builder.build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    }
  }

  private static void useLibraryResourceProvider(
      int minApiLevel, Collection<Path> libraries, Collection<Path> inputs) {
    try {
      R8Command.Builder builder =
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addProgramFiles(inputs);
      for (Path library : libraries) {
        builder.addLibraryResourceProvider(new ArchiveClassFileProvider(library));
      }
      R8.run(builder.build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IO exception", e);
    }
  }

  private static void useMainDexListFiles(
      int minApiLevel,
      Collection<Path> libraries,
      Collection<Path> inputs,
      Collection<Path> mainDexList) {
    try {
      R8.run(
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries)
              .addProgramFiles(inputs)
              .addMainDexListFiles(mainDexList)
              .build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    }
  }

  private static void useMainDexClasses(
      int minApiLevel,
      Collection<Path> libraries,
      Collection<Path> inputs,
      Collection<Path> mainDexList) {
    try {
      List<String> mainDexClasses = new ArrayList<>(1);
      for (Path path : mainDexList) {
        for (String line : Files.readAllLines(path)) {
          String entry = line.trim();
          if (entry.isEmpty() || entry.startsWith("#") || !entry.endsWith(".class")) {
            continue;
          }
          mainDexClasses.add(entry.replace(".class", "").replace("/", "."));
        }
      }
      R8.run(
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries)
              .addProgramFiles(inputs)
              .addMainDexClasses(mainDexClasses)
              .build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IO exception", e);
    }
  }

  private static void useMainDexRulesFiles(
      int minApiLevel,
      Collection<Path> libraries,
      Collection<Path> inputs,
      Collection<Path> mainDexRules) {
    try {
      R8.run(
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries)
              .addProgramFiles(inputs)
              .addMainDexRulesFiles(mainDexRules)
              .build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    }
  }

  private static void useMainDexRules(
      int minApiLevel,
      Collection<Path> libraries,
      Collection<Path> inputs,
      Collection<Path> mainDexRulesFiles) {
    try {
      R8Command.Builder builder =
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries)
              .addProgramFiles(inputs);
      for (Path mainDexRulesFile : mainDexRulesFiles) {
        builder.addMainDexRules(
            Files.readAllLines(mainDexRulesFile), new PathOrigin(mainDexRulesFile));
      }
      R8.run(builder.build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IO exception", e);
    }
  }

  private static void useProguardConfigFiles(
      int minApiLevel,
      Collection<Path> libraries,
      Collection<Path> inputs,
      Collection<Path> mainDexList,
      List<Path> pgConf) {
    try {
      R8.run(
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries)
              .addProgramFiles(inputs)
              .addMainDexListFiles(mainDexList)
              .addProguardConfigurationFiles(pgConf)
              .build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    }
  }

  private static void useProguardConfigLines(
      int minApiLevel,
      Collection<Path> libraries,
      Collection<Path> inputs,
      Collection<Path> mainDexList,
      List<Path> pgConf) {
    try {
      R8Command.Builder builder =
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries)
              .addProgramFiles(inputs)
              .addMainDexListFiles(mainDexList);
      for (Path file : pgConf) {
        builder.addProguardConfiguration(Files.readAllLines(file), new PathOrigin(file));
      }
      R8.run(builder.build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IO exception", e);
    }
  }

  // Check API support for all the varg variants.
  private static void useVArgVariants(
      int minApiLevel,
      List<Path> libraries,
      List<Path> inputs,
      List<Path> mainDexList,
      List<Path> mainDexRules,
      List<Path> pgConf) {
    try {
      R8.run(
          R8Command.builder(handler)
              .setMinApiLevel(minApiLevel)
              .setProgramConsumer(new EnsureOutputConsumer())
              .addLibraryFiles(libraries.get(0))
              .addLibraryFiles(libraries.stream().skip(1).toArray(Path[]::new))
              .addProgramFiles(inputs.get(0))
              .addProgramFiles(inputs.stream().skip(1).toArray(Path[]::new))
              .addMainDexListFiles(mainDexList.get(0))
              .addMainDexListFiles(mainDexList.stream().skip(1).toArray(Path[]::new))
              .addMainDexRulesFiles(mainDexRules.get(0))
              .addMainDexRulesFiles(mainDexRules.stream().skip(1).toArray(Path[]::new))
              .addProguardConfigurationFiles(pgConf.get(0))
              .addProguardConfigurationFiles(pgConf.stream().skip(1).toArray(Path[]::new))
              .build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException("Unexpected compilation exceptions", e);
    }
  }

  // Helpers for tests.
  // Some of this reimplements stuff in R8 utils, but that is not public API and we should not
  // rely on it.

  private static List<ClassFileContent> readClassFiles(Collection<Path> files) throws IOException {
    List<ClassFileContent> classfiles = new ArrayList<>();
    for (Path file : files) {
      if (isArchive(file)) {
        Origin zipOrigin = new PathOrigin(file);
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(file), StandardCharsets.UTF_8);
        ZipEntry entry;
        while (null != (entry = zip.getNextEntry())) {
          String name = entry.getName();
          if (isClassFile(name)) {
            Origin origin = new ArchiveEntryOrigin(name, zipOrigin);
            classfiles.add(new ClassFileContent(origin, readBytes(zip)));
          }
        }
      } else if (isClassFile(file)) {
        classfiles.add(new ClassFileContent(new PathOrigin(file), Files.readAllBytes(file)));
      }
    }
    return classfiles;
  }

  private static byte[] readBytes(InputStream stream) throws IOException {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[0xffff];
      for (int length; (length = stream.read(buffer)) != -1; ) {
        bytes.write(buffer, 0, length);
      }
      return bytes.toByteArray();
    }
  }

  private static boolean isClassFile(Path file) {
    return isClassFile(file.toString());
  }

  private static boolean isClassFile(String file) {
    file = file.toLowerCase();
    return file.endsWith(".class");
  }

  private static boolean isArchive(Path file) {
    return isArchive(file.toString());
  }

  private static boolean isArchive(String file) {
    file = file.toLowerCase();
    return file.endsWith(".zip") || file.endsWith(".jar");
  }

  private static class ClassFileContent {
    final Origin origin;
    final byte[] data;

    public ClassFileContent(Origin origin, byte[] data) {
      this.origin = origin;
      this.data = data;
    }
  }

  private static class EnsureOutputConsumer implements DexIndexedConsumer {
    boolean hasOutput = false;

    @Override
    public synchronized void accept(
        int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
      hasOutput = true;
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (!hasOutput) {
        handler.error(new StringDiagnostic("Expected to produce output but had none"));
      }
    }
  }
}
