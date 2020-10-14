// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Keep
public class StackTraceElementProxyRetracer<T extends StackTraceElementProxy<?>> {

  private final RetraceApi retracer;

  public StackTraceElementProxyRetracer(RetraceApi retracer) {
    this.retracer = retracer;
  }

  public Stream<RetraceStackTraceProxy<T>> retrace(T element) {
    if (!element.hasClassName()) {
      return Stream.of(RetraceStackTraceProxy.builder(element).build());
    }
    RetraceClassResult classResult =
        retracer.retrace(Reference.classFromTypeName(element.className()));
    List<RetraceStackTraceProxy<T>> retracedProxies = new ArrayList<>();
    if (!element.hasMethodName()) {
      classResult.forEach(
          classElement -> {
            RetraceStackTraceProxy.Builder<T> proxy =
                RetraceStackTraceProxy.builder(element)
                    .setRetracedClassElement(classElement.getRetracedClass())
                    .setAmbiguous(classResult.isAmbiguous());
            if (element.hasFileName()) {
              proxy.setSourceFile(classElement.retraceSourceFile(element.fileName()).getFilename());
            }
            retracedProxies.add(proxy.build());
          });
    } else {
      RetraceMethodResult retraceMethodResult = classResult.lookupMethod(element.methodName());
      Result<? extends RetraceClassMemberElement<RetracedMethod>, ?> methodResult;
      if (element.hasLineNumber()) {
        methodResult = retraceMethodResult.narrowByPosition(element.lineNumber());
      } else {
        methodResult = retraceMethodResult;
      }
      methodResult.forEach(
          methodElement -> {
            methodElement.visitFrames(
                (frame, index) -> {
                  RetraceStackTraceProxy.Builder<T> proxy =
                      RetraceStackTraceProxy.builder(element)
                          .setRetracedClassElement(frame.getHolderClass())
                          .setRetracedMethodElement(frame)
                          .setAmbiguous(methodResult.isAmbiguous() && index == 0);
                  if (element.hasLineNumber()) {
                    proxy.setLineNumber(frame.getOriginalPositionOrDefault(element.lineNumber()));
                  }
                  if (element.hasFileName()) {
                    proxy.setSourceFile(
                        methodElement.retraceSourceFile(frame, element.fileName()).getFilename());
                  }
                  retracedProxies.add(proxy.build());
                });
          });
    }
    return retracedProxies.stream();
  }

  @Keep
  public static class RetraceStackTraceProxy<T extends StackTraceElementProxy<?>> {

    private final T originalItem;
    private final RetracedClass retracedClass;
    private final RetracedMethod retracedMethod;
    private final String sourceFile;
    private final int lineNumber;
    private final boolean isAmbiguous;

    private RetraceStackTraceProxy(
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

    public boolean isAmbiguous() {
      return isAmbiguous;
    }

    public boolean hasRetracedClass() {
      return retracedClass != null;
    }

    public boolean hasRetracedMethod() {
      return retracedMethod != null;
    }

    public boolean hasSourceFile() {
      return sourceFile != null;
    }

    public boolean hasLineNumber() {
      return lineNumber != -1;
    }

    public T getOriginalItem() {
      return originalItem;
    }

    public RetracedClass getRetracedClass() {
      return retracedClass;
    }

    public RetracedMethod getRetracedMethod() {
      return retracedMethod;
    }

    public String getSourceFile() {
      return sourceFile;
    }

    private static <T extends StackTraceElementProxy<?>> Builder<T> builder(T originalElement) {
      return new Builder<>(originalElement);
    }

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

      private RetraceStackTraceProxy<T> build() {
        RetracedClass retracedClass = classContext;
        if (methodContext != null) {
          retracedClass = methodContext.getHolderClass();
        }
        return new RetraceStackTraceProxy<>(
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
