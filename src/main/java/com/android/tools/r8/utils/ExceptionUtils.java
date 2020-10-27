// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.Version;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.Position;
import com.google.common.collect.ObjectArrays;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class ExceptionUtils {

  public static final int STATUS_ERROR = 1;

  public static void withConsumeResourceHandler(
      Reporter reporter, StringConsumer consumer, String data) {
    withConsumeResourceHandler(reporter, handler -> consumer.accept(data, handler));
  }

  public static void withFinishedResourceHandler(Reporter reporter, StringConsumer consumer) {
    withConsumeResourceHandler(reporter, consumer::finished);
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
    void run() throws IOException, CompilationError, ResourceException;
  }

  public static void withD8CompilationHandler(Reporter reporter, CompileAction action)
      throws CompilationFailedException {
    withCompilationHandler(reporter, action);
  }

  public static void withR8CompilationHandler(Reporter reporter, CompileAction action)
      throws CompilationFailedException {
    withCompilationHandler(reporter, action);
  }

  public static void withMainDexListHandler(
      Reporter reporter, CompileAction action) throws CompilationFailedException {
    withCompilationHandler(reporter, action);
  }

  public static void withCompilationHandler(Reporter reporter, CompileAction action)
      throws CompilationFailedException {
    try {
      action.run();
      reporter.failIfPendingErrors();
    } catch (Throwable e) {
      throw failCompilation(reporter, e);
    }
  }

  private static CompilationFailedException failCompilation(
      Reporter reporter, Throwable topMostException) {
    return failWithFakeEntry(
        reporter, topMostException, CompilationFailedException::new, AbortException.class);
  }

  public static <T extends Exception, A extends Exception> T failWithFakeEntry(
      DiagnosticsHandler diagnosticsHandler,
      Throwable topMostException,
      BiFunction<String, Throwable, T> newException,
      Class<A> abortException) {
    // Find inner-most cause of the failure and compute origin, position and reported for the path.
    boolean hasBeenReported = false;
    Origin origin = Origin.unknown();
    Position position = Position.UNKNOWN;
    List<Throwable> suppressed = new ArrayList<>();
    Throwable innerMostCause = topMostException;
    while (true) {
      hasBeenReported |= abortException.isAssignableFrom(innerMostCause.getClass());
      Origin nextOrigin = getOrigin(innerMostCause);
      if (nextOrigin != Origin.unknown()) {
        origin = nextOrigin;
      }
      Position nextPosition = getPosition(innerMostCause);
      if (nextPosition != Position.UNKNOWN) {
        position = nextPosition;
      }
      if (innerMostCause.getCause() == null || suppressed.contains(innerMostCause)) {
        break;
      }
      suppressed.add(innerMostCause);
      innerMostCause = innerMostCause.getCause();
    }
    // Add the full stack as a suppressed stack on the inner cause.
    if (topMostException != innerMostCause) {
      innerMostCause.addSuppressed(topMostException);
    }

    // If no abort is seen, the exception is not reported, so report it now.
    if (!hasBeenReported) {
      diagnosticsHandler.error(new ExceptionDiagnostic(innerMostCause, origin, position));
    }

    // Build the top-level compiler exception and version stack.
    StringBuilder message = new StringBuilder("Compilation failed to complete");
    if (position != Position.UNKNOWN) {
      message.append(", position: ").append(position);
    }
    if (origin != Origin.unknown()) {
      message.append(", origin: ").append(origin);
    }
    // Create the final exception object.
    T rethrow = newException.apply(message.toString(), innerMostCause);
    // Replace its stack by the cause stack and insert version info at the top.
    String filename = "Version_" + Version.LABEL + ".java";
    StackTraceElement versionElement =
        new StackTraceElement(Version.class.getSimpleName(), "fakeStackEntry", filename, 0);
    rethrow.setStackTrace(ObjectArrays.concat(versionElement, rethrow.getStackTrace()));
    return rethrow;
  }

  private static Origin getOrigin(Throwable e) {
    if (e instanceof IOException) {
      return extractIOExceptionOrigin((IOException) e);
    }
    if (e instanceof CompilationError) {
      return ((CompilationError) e).getOrigin();
    }
    if (e instanceof ResourceException) {
      return ((ResourceException) e).getOrigin();
    }
    if (e instanceof OriginAttachmentException) {
      return ((OriginAttachmentException) e).origin;
    }
    if (e instanceof AbortException) {
      return ((AbortException) e).getOrigin();
    }
    return Origin.unknown();
  }

  private static Position getPosition(Throwable e) {
    if (e instanceof CompilationError) {
      return ((CompilationError) e).getPosition();
    }
    if (e instanceof OriginAttachmentException) {
      return ((OriginAttachmentException) e).position;
    }
    if (e instanceof AbortException) {
      return ((AbortException) e).getPosition();
    }
    return Position.UNKNOWN;
  }

  public interface MainAction {
    void run() throws CompilationFailedException;
  }

  public static void withMainProgramHandler(MainAction action) {
    try {
      action.run();
    } catch (CompilationFailedException e) {
      throw exitWithError(e, e.getCause());
    } catch (RuntimeException e) {
      throw exitWithError(e, e);
    }
  }

  private static RuntimeException exitWithError(Throwable e, Throwable cause) {
    if (isExpectedException(cause)) {
      // Detail of the errors were already reported
      System.err.println("Compilation failed");
      System.exit(STATUS_ERROR);
      throw null;
    }
    System.err.println("Compilation failed with an internal error.");
    e.printStackTrace();
    System.exit(STATUS_ERROR);
    throw null;
  }

  private static boolean isExpectedException(Throwable e) {
    return e instanceof CompilationError || e instanceof AbortException;
  }

  // We should try to avoid the use of this extraction as it signifies a point where we don't have
  // enough context to associate a specific origin with an IOException. Concretely, we should move
  // towards always catching IOException and rethrowing CompilationError with proper origins.
  private static Origin extractIOExceptionOrigin(IOException e) {
    if (e instanceof FileSystemException) {
      FileSystemException fse = (FileSystemException) e;
      if (fse.getFile() != null && !fse.getFile().isEmpty()) {
        return new PathOrigin(Paths.get(fse.getFile()));
      }
    }
    return Origin.unknown();
  }

  public static RuntimeException unwrapExecutionException(ExecutionException executionException) {
    return new RuntimeException(executionException);
  }

  public static void withOriginAttachmentHandler(Origin origin, Runnable action) {
    withOriginAndPositionAttachmentHandler(origin, Position.UNKNOWN, action);
  }

  public static <T> T withOriginAttachmentHandler(Origin origin, Supplier<T> action) {
    return withOriginAndPositionAttachmentHandler(origin, Position.UNKNOWN, action);
  }

  public static void withOriginAndPositionAttachmentHandler(
      Origin origin, Position position, Runnable action) {
    withOriginAndPositionAttachmentHandler(
        origin,
        position,
        () -> {
          action.run();
          return null;
        });
  }

  public static <T> T withOriginAndPositionAttachmentHandler(
      Origin origin, Position position, Supplier<T> action) {
    try {
      return action.get();
    } catch (RuntimeException e) {
      throw OriginAttachmentException.wrap(e, origin, position);
    }
  }

  private static class OriginAttachmentException extends RuntimeException {
    final Origin origin;
    final Position position;

    public static RuntimeException wrap(RuntimeException e, Origin origin, Position position) {
      return needsAttachment(e, origin, position)
          ? new OriginAttachmentException(e, origin, position)
          : e;
    }

    private OriginAttachmentException(RuntimeException e, Origin origin, Position position) {
      super(e);
      this.origin = origin;
      this.position = position;
    }

    private static boolean needsAttachment(RuntimeException e, Origin origin, Position position) {
      if (origin == Origin.unknown() && position == Position.UNKNOWN) {
        return false;
      }
      Origin existingOrigin = getOrigin(e);
      Position existingPosition = getPosition(e);
      return origin != existingOrigin || position != existingPosition;
    }
  }
}
