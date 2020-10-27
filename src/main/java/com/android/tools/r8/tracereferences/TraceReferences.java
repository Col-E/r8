// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Keep;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.Version;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.Set;

@Keep
public class TraceReferences {

  public static void run(TraceReferencesCommand command) throws CompilationFailedException {
    try {
      runInternal(command);
    } catch (TraceReferencesAbortException e) {
      throw new CompilationFailedException();
    } catch (Exception e) {
      command.getDiagnosticsHandler().error(new ExceptionDiagnostic(e));
      throw new CompilationFailedException(e);
    }
  }

  private static void runInternal(TraceReferencesCommand command) throws Exception {
    AndroidApp.Builder builder = AndroidApp.builder();
    command.getLibrary().forEach(builder::addLibraryResourceProvider);
    command.getTarget().forEach(builder::addLibraryResourceProvider);
    command.getSource().forEach(builder::addProgramResourceProvider);
    Set<String> tagetDescriptors = new HashSet<>();
    command
        .getTarget()
        .forEach(provider -> tagetDescriptors.addAll(provider.getClassDescriptors()));
    for (ProgramResourceProvider provider : command.getSource()) {
      for (ProgramResource programResource : provider.getProgramResources()) {
        if (programResource.getKind() == Kind.DEX) {
          command
              .getDiagnosticsHandler()
              .warning(new StringDiagnostic("DEX files not fully supported"));
          assert programResource.getClassDescriptors() == null;
          for (DexProgramClass clazz :
              new ApplicationReader(
                      AndroidApp.builder()
                          .addDexProgramData(ImmutableList.of(programResource.getBytes()))
                          .build(),
                      new InternalOptions(),
                      Timing.empty())
                  .read()
                  .classes()) {
            tagetDescriptors.remove(clazz.getType().toDescriptorString());
          }
        } else {
          tagetDescriptors.removeAll(programResource.getClassDescriptors());
        }
      }
    }
    Tracer tracer = new Tracer(tagetDescriptors, builder.build(), command.getDiagnosticsHandler());
    tracer.run(command.getConsumer());
  }

  public static void run(String... args) throws CompilationFailedException {
    TraceReferencesCommand command =
        TraceReferencesCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      System.out.println(TraceReferencesCommandParser.USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("tracereferences " + Version.getVersionString());
      return;
    }
    run(command);
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
