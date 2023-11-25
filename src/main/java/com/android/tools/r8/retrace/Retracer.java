// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.OptionalInt;

/** This is the main api interface for retrace. */
@KeepForApi
public interface Retracer {

  RetraceClassResult retraceClass(ClassReference classReference);

  RetraceMethodResult retraceMethod(MethodReference methodReference);

  /**
   * Retrace a stack trace frame without knowing the full method signature.
   *
   * <p>This method retraces a frame of the format:
   *
   * <pre>
   *   [callee-frame-or-exception-line]
   *   com.example.Type.method(SourceFile:line)
   * </pre>
   *
   * where the context parameter described below is obtained from retracing the preceeding line
   * `callee-frame-or-exception-line`
   *
   * @param context The context of this frame as defined by the frame preceding it, i.e., the
   *     context obtained by retracing the callee frame (or exception line), with the rest of the
   *     arguments being those that represent the caller frame.
   * @param position The optional line/pc information of the frame.
   * @param classReference The class/holder information of the frame.
   * @param methodName The method name information of the frame. If the full method signature is
   *     known, the alternative retraceFrame method should be used instead.
   * @return The possibly ambiguous result of retracing the frame.
   */
  RetraceFrameResult retraceFrame(
      RetraceStackTraceContext context,
      OptionalInt position,
      ClassReference classReference,
      String methodName);

  /**
   * Retrace a stack frame with full method signature information.
   *
   * <p>Apart from having the full method signature this is the same as retracing without it.
   *
   * @param context The context of this frame as defined by the frame preceding it, i.e., the
   *     context obtained by retracing the callee frame (or exception line), with the rest of the
   *     arguments being those that represent the caller frame.
   * @param position The optional line/pc information of the frame.
   * @param methodReference The qualified method reference information of the frame.
   * @return The possibly ambiguous result of retracing the frame.
   */
  RetraceFrameResult retraceFrame(
      RetraceStackTraceContext context, OptionalInt position, MethodReference methodReference);

  RetraceFieldResult retraceField(FieldReference fieldReference);

  RetraceTypeResult retraceType(TypeReference typeReference);

  RetraceThrownExceptionResult retraceThrownException(ClassReference exception);

  static Retracer createDefault(
      ProguardMapProducer proguardMapProducer, DiagnosticsHandler diagnosticsHandler) {
    try {
      return ProguardMappingSupplier.builder()
          .setProguardMapProducer(proguardMapProducer)
          .build()
          .createRetracer(diagnosticsHandler);
    } catch (Exception e) {
      throw new InvalidMappingFileException(e);
    }
  }
}
