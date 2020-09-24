// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.utils.ExceptionUtils.STATUS_ERROR;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Keep;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
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
      throw new CompilationFailedException();
    }
  }

  private static void runInternal(TraceReferencesCommand command) throws Exception {
    if (command.getLibrary().isEmpty()) {
      throw new TraceReferencesException("No library specified");
    }
    if (command.getTarget().isEmpty()) {
      throw new TraceReferencesException("No target specified");
    }
    if (command.getSource().isEmpty()) {
      throw new TraceReferencesException("No source specified");
    }
    if (command.getOutput() == null) {
      throw new TraceReferencesException("No output specified");
    }
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
        tagetDescriptors.removeAll(programResource.getClassDescriptors());
      }
    }
    Tracer tracer = new Tracer(tagetDescriptors, builder.build());
    Result result = tracer.run();
    ResultFormatter formatter;
    switch (command.getOutputFormat()) {
      case PRINTUSAGE:
        formatter = new PrintUsesFormatter(command.getOutput(), command.getDiagnosticsHandler());
        break;
      case KEEP_RULES:
        formatter =
            new KeepRuleFormatter(command.getOutput(), command.getDiagnosticsHandler(), false);
        break;
      case KEEP_RULES_WITH_ALLOWOBFUSCATION:
        formatter =
            new KeepRuleFormatter(command.getOutput(), command.getDiagnosticsHandler(), true);
        break;
      default:
        throw new TraceReferencesException("Unexpected format " + command.getOutputFormat().name());
    }
    formatter.format(result);
  }

  public static void main(String... args) {
    try {
      TraceReferencesCommand command = TraceReferencesCommand.parse(args, Origin.root()).build();
      if (command.isPrintHelp()) {
        System.out.println(TraceReferencesCommandParser.USAGE_MESSAGE);
        return;
      }
      if (command.isPrintVersion()) {
        System.out.println("referencetrace " + Version.getVersionString());
        return;
      }
      run(command);
    } catch (CompilationFailedException e) {
      System.exit(STATUS_ERROR);
    } catch (Throwable e) {
      System.err.println("ReferenceTrace failed with an internal error.");
      Throwable cause = e.getCause() == null ? e : e.getCause();
      cause.printStackTrace();
      System.exit(STATUS_ERROR);
    }
  }
}
