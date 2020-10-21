// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceStackTraceProxy;
import com.android.tools.r8.retrace.RetracedClass;
import com.android.tools.r8.retrace.RetracedMethod;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.StackTraceElementProxyRetracer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class StackTraceElementProxyRetracerImpl<T extends StackTraceElementProxy<?>>
    implements StackTraceElementProxyRetracer<T> {

  private final RetracerImpl retracer;

  public StackTraceElementProxyRetracerImpl(RetracerImpl retracer) {
    this.retracer = retracer;
  }

  @Override
  public Stream<RetraceStackTraceProxyImpl<T>> retrace(T element) {
    if (!element.hasClassName()) {
      return Stream.of(RetraceStackTraceProxyImpl.builder(element).build());
    }
    RetraceClassResultImpl classResult =
        retracer.retraceClass(Reference.classFromTypeName(element.className()));
    List<RetraceStackTraceProxyImpl<T>> retracedProxies = new ArrayList<>();
    if (!element.hasMethodName()) {
      classResult.forEach(
          classElement -> {
            RetraceStackTraceProxyImpl.Builder<T> proxy =
                RetraceStackTraceProxyImpl.builder(element)
                    .setRetracedClassElement(classElement.getRetracedClass())
                    .setAmbiguous(classResult.isAmbiguous());
            if (element.hasFileName()) {
              proxy.setSourceFile(classElement.retraceSourceFile(element.fileName()).getFilename());
            }
            retracedProxies.add(proxy.build());
          });
    } else {
      RetraceFrameResultImpl frameResult =
          element.hasLineNumber()
              ? classResult.lookupFrame(element.methodName(), element.lineNumber())
              : classResult.lookupFrame(element.methodName());
      frameResult.forEach(
          frameElement -> {
            frameElement.visitFrames(
                (frame, index) -> {
                  RetraceStackTraceProxyImpl.Builder<T> proxy =
                      RetraceStackTraceProxyImpl.builder(element)
                          .setRetracedClassElement(frame.getHolderClass())
                          .setRetracedMethodElement(frame)
                          .setAmbiguous(frameResult.isAmbiguous() && index == 0);
                  if (element.hasLineNumber()) {
                    proxy.setLineNumber(frame.getOriginalPositionOrDefault(element.lineNumber()));
                  }
                  if (element.hasFileName()) {
                    proxy.setSourceFile(
                        frameElement.retraceSourceFile(frame, element.fileName()).getFilename());
                  }
                  retracedProxies.add(proxy.build());
                });
          });
    }
    return retracedProxies.stream();
  }

  public static class RetraceStackTraceProxyImpl<T extends StackTraceElementProxy<?>>
      implements RetraceStackTraceProxy<T> {

    private final T originalItem;
    private final RetracedClass retracedClass;
    private final RetracedMethod retracedMethod;
    private final String sourceFile;
    private final int lineNumber;
    private final boolean isAmbiguous;

    private RetraceStackTraceProxyImpl(
        T originalItem,
        RetracedClass retracedClass,
        RetracedMethod retracedMethod,
        String sourceFile,
        int lineNumber,
        boolean isAmbiguous) {
      assert originalItem != null;
      this.originalItem = originalItem;
      this.retracedClass = retracedClass;
      this.retracedMethod = retracedMethod;
      this.sourceFile = sourceFile;
      this.lineNumber = lineNumber;
      this.isAmbiguous = isAmbiguous;
    }

    @Override
    public boolean isAmbiguous() {
      return isAmbiguous;
    }

    @Override
    public boolean hasRetracedClass() {
      return retracedClass != null;
    }

    @Override
    public boolean hasRetracedMethod() {
      return retracedMethod != null;
    }

    @Override
    public boolean hasSourceFile() {
      return sourceFile != null;
    }

    @Override
    public boolean hasLineNumber() {
      return lineNumber != -1;
    }

    @Override
    public T getOriginalItem() {
      return originalItem;
    }

    @Override
    public RetracedClass getRetracedClass() {
      return retracedClass;
    }

    @Override
    public RetracedMethod getRetracedMethod() {
      return retracedMethod;
    }

    @Override
    public String getSourceFile() {
      return sourceFile;
    }

    private static <T extends StackTraceElementProxy<?>> Builder<T> builder(T originalElement) {
      return new Builder<>(originalElement);
    }

    @Override
    public int getLineNumber() {
      return lineNumber;
    }

    private static class Builder<T extends StackTraceElementProxy<?>> {

      private final T originalElement;
      private RetracedClass classContext;
      private RetracedMethod methodContext;
      private String sourceFile;
      private int lineNumber = -1;
      private boolean isAmbiguous;

      private Builder(T originalElement) {
        this.originalElement = originalElement;
      }

      private Builder<T> setRetracedClassElement(RetracedClass retracedClass) {
        this.classContext = retracedClass;
        return this;
      }

      private Builder<T> setRetracedMethodElement(RetracedMethod methodElement) {
        this.methodContext = methodElement;
        return this;
      }

      private Builder<T> setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
        return this;
      }

      private Builder<T> setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
        return this;
      }

      private Builder<T> setAmbiguous(boolean ambiguous) {
        this.isAmbiguous = ambiguous;
        return this;
      }

      private RetraceStackTraceProxyImpl<T> build() {
        RetracedClass retracedClass = classContext;
        if (methodContext != null) {
          retracedClass = methodContext.getHolderClass();
        }
        return new RetraceStackTraceProxyImpl<>(
            originalElement,
            retracedClass,
            methodContext,
            sourceFile != null ? sourceFile : null,
            lineNumber,
            isAmbiguous);
      }
    }
  }
}
