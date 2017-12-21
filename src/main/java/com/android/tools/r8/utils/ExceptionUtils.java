// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.errors.CompilationError;
import java.io.IOException;
import java.util.function.Consumer;

public abstract class ExceptionUtils {

  public static void withConsumeResourceHandler(
      Reporter reporter, StringConsumer consumer, String data) {
    withConsumeResourceHandler(reporter, handler -> consumer.accept(data, handler));
  }

  public static void withConsumeResourceHandler(
      Reporter reporter, Consumer<DiagnosticsHandler> consumer) {
    // Unchecked exceptions simply propagate out, aborting the compilation forcefully.
    consumer.accept(reporter);
    // Fail fast for now. We might consider delaying failure since consumer failure does not affect
    // the compilation. We might need to be careful to correctly identify errors so as to exit
    // compilation with an error code.
    reporter.failIfPendingErrors();
  }

  public interface CompileAction {
    void run() throws IOException, CompilationException, CompilationError, ResourceException;
  }

  private enum Compiler {
    D8,
    R8;
  }

  public static void withD8CompilationHandler(Reporter reporter, CompileAction action)
      throws CompilationFailedException {
    withCompilationHandler(reporter, action, Compiler.D8);
  }

  public static void withR8CompilationHandler(Reporter reporter, CompileAction action)
      throws CompilationFailedException {
    withCompilationHandler(reporter, action, Compiler.R8);
  }

  public static void withCompilationHandler(
      Reporter reporter, CompileAction action, Compiler compiler)
      throws CompilationFailedException {
    try {
      try {
        action.run();
      } catch (IOException e) {
        throw reporter.fatalError(new IOExceptionDiagnostic(e));
      } catch (CompilationException e) {
        throw reporter.fatalError(
            new StringDiagnostic(
                compiler == Compiler.D8 ? e.getMessageForD8() : e.getMessageForR8()),
            e);
      } catch (CompilationError e) {
        throw reporter.fatalError(e);
      } catch (ResourceException e) {
        throw reporter.fatalError(
            e.getCause() instanceof IOException
                ? new IOExceptionDiagnostic((IOException) e.getCause(), e.getOrigin())
                : new StringDiagnostic(e.getMessage(), e.getOrigin()));
      }
      reporter.failIfPendingErrors();
    } catch (AbortException e) {
      throw new CompilationFailedException(e);
    }
  }
}
