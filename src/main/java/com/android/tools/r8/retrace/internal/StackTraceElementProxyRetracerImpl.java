// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassResult;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.RetraceStackTraceProxy;
import com.android.tools.r8.retrace.RetracedClass;
import com.android.tools.r8.retrace.RetracedField;
import com.android.tools.r8.retrace.RetracedMethod;
import com.android.tools.r8.retrace.RetracedType;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.StackTraceElementProxyRetracer;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
    RetraceClassResultImpl classResult = retracer.retraceClass(element.getClassReference());
    if (element.hasMethodName()) {
      return retraceMethod(element, classResult);
    } else if (element.hasFieldName()) {
      return retraceField(element, classResult);
    } else {
      return retraceClassOrType(element, classResult);
    }
  }

  private Stream<RetraceStackTraceProxyImpl<T>> retraceClassOrType(
      T element, RetraceClassResult classResult) {
    return classResult.stream()
        .flatMap(
            classElement ->
                retraceFieldOrReturnType(element)
                    .flatMap(
                        fieldOrReturnTypeConsumer ->
                            retracedMethodArguments(element)
                                .map(
                                    argumentsConsumer -> {
                                      RetraceStackTraceProxyImpl.Builder<T> proxy =
                                          RetraceStackTraceProxyImpl.builder(element)
                                              .setRetracedClass(classElement.getRetracedClass())
                                              .setAmbiguous(classResult.isAmbiguous())
                                              .setTopFrame(true);
                                      argumentsConsumer.accept(proxy);
                                      fieldOrReturnTypeConsumer.accept(proxy);
                                      if (element.hasFileName()) {
                                        proxy.setSourceFile(
                                            classElement
                                                .retraceSourceFile(element.getFileName())
                                                .getFilename());
                                      }
                                      return proxy.build();
                                    })));
  }

  private Stream<RetraceStackTraceProxyImpl<T>> retraceMethod(
      T element, RetraceClassResultImpl classResult) {
    return retraceFieldOrReturnType(element)
        .flatMap(
            fieldOrReturnTypeConsumer ->
                retracedMethodArguments(element)
                    .flatMap(
                        argumentsConsumer -> {
                          RetraceFrameResultImpl frameResult =
                              element.hasLineNumber()
                                  ? classResult.lookupFrame(
                                      element.getMethodName(), element.getLineNumber())
                                  : classResult.lookupFrame(element.getMethodName());
                          return frameResult.stream()
                              .flatMap(
                                  frameElement -> {
                                    List<RetraceStackTraceProxyImpl<T>> retracedProxies =
                                        new ArrayList<>();
                                    frameElement.visitFrames(
                                        (frame, index) -> {
                                          RetraceStackTraceProxyImpl.Builder<T> proxy =
                                              RetraceStackTraceProxyImpl.builder(element)
                                                  .setRetracedClass(frame.getHolderClass())
                                                  .setRetracedMethod(frame)
                                                  .setAmbiguous(
                                                      frameResult.isAmbiguous() && index == 0)
                                                  .setTopFrame(index == 0);
                                          if (element.hasLineNumber()) {
                                            proxy.setLineNumber(
                                                frame.getOriginalPositionOrDefault(
                                                    element.getLineNumber()));
                                          }
                                          if (element.hasFileName()) {
                                            proxy.setSourceFile(
                                                frameElement
                                                    .retraceSourceFile(frame, element.getFileName())
                                                    .getFilename());
                                          }
                                          fieldOrReturnTypeConsumer.accept(proxy);
                                          argumentsConsumer.accept(proxy);
                                          retracedProxies.add(proxy.build());
                                        });
                                    return retracedProxies.stream();
                                  });
                        }));
  }

  private Stream<RetraceStackTraceProxyImpl<T>> retraceField(
      T element, RetraceClassResult classResult) {
    return retraceFieldOrReturnType(element)
        .flatMap(
            fieldOrReturnTypeConsumer ->
                retracedMethodArguments(element)
                    .flatMap(
                        argumentsConsumer -> {
                          RetraceFieldResult retraceFieldResult =
                              classResult.lookupField(element.getFieldName());
                          return retraceFieldResult.stream()
                              .map(
                                  fieldElement -> {
                                    RetraceStackTraceProxyImpl.Builder<T> proxy =
                                        RetraceStackTraceProxyImpl.builder(element)
                                            .setRetracedClass(
                                                fieldElement.getField().getHolderClass())
                                            .setRetracedField(fieldElement.getField())
                                            .setAmbiguous(retraceFieldResult.isAmbiguous())
                                            .setTopFrame(true);
                                    if (element.hasFileName()) {
                                      proxy.setSourceFile(
                                          fieldElement
                                              .retraceSourceFile(element.getFileName())
                                              .getFilename());
                                    }
                                    fieldOrReturnTypeConsumer.accept(proxy);
                                    argumentsConsumer.accept(proxy);
                                    return proxy.build();
                                  });
                        }));
  }

  private Stream<Consumer<RetraceStackTraceProxyImpl.Builder<T>>> retraceFieldOrReturnType(
      T element) {
    if (!element.hasFieldOrReturnType()) {
      return Stream.of(noEffect -> {});
    }
    String elementOrReturnType = element.getFieldOrReturnType();
    if (elementOrReturnType.equals("void")) {
      return Stream.of(proxy -> proxy.setRetracedFieldOrReturnType(RetracedTypeImpl.createVoid()));
    } else {
      TypeReference typeReference = Reference.typeFromTypeName(elementOrReturnType);
      RetraceTypeResultImpl retraceTypeResult = retracer.retraceType(typeReference);
      return retraceTypeResult.stream()
          .map(
              type ->
                  (proxy -> {
                    proxy.setRetracedFieldOrReturnType(type.getType());
                    if (retraceTypeResult.isAmbiguous()) {
                      proxy.setAmbiguous(true);
                    }
                  }));
    }
  }

  private Stream<Consumer<RetraceStackTraceProxyImpl.Builder<T>>> retracedMethodArguments(
      T element) {
    if (!element.hasMethodArguments()) {
      return Stream.of(noEffect -> {});
    }
    List<RetraceTypeResultImpl> retracedResults =
        Arrays.stream(element.getMethodArguments().split(","))
            .map(typeName -> retracer.retraceType(Reference.typeFromTypeName(typeName)))
            .collect(Collectors.toList());
    List<List<RetracedType>> initial = new ArrayList<>();
    initial.add(new ArrayList<>());
    Box<Boolean> isAmbiguous = new Box<>(false);
    List<List<RetracedType>> retracedArguments =
        ListUtils.fold(
            retracedResults,
            initial,
            (acc, retracedTypeResult) -> {
              if (retracedTypeResult.isAmbiguous()) {
                isAmbiguous.set(true);
              }
              List<List<RetracedType>> newResult = new ArrayList<>();
              retracedTypeResult.forEach(
                  retracedElement -> {
                    acc.forEach(
                        oldResult -> {
                          List<RetracedType> newList = new ArrayList<>(oldResult);
                          newList.add(retracedElement.getType());
                          newResult.add(newList);
                        });
                  });
              return newResult;
            });
    return retracedArguments.stream()
        .map(
            arguments ->
                proxy -> {
                  proxy.setRetracedMethodArguments(arguments);
                  if (isAmbiguous.get()) {
                    proxy.setAmbiguous(true);
                  }
                });
  }

  public static class RetraceStackTraceProxyImpl<T extends StackTraceElementProxy<?>>
      implements RetraceStackTraceProxy<T> {

    private final T originalItem;
    private final RetracedClass retracedClass;
    private final RetracedMethod retracedMethod;
    private final RetracedField retracedField;
    private final RetracedType fieldOrReturnType;
    private final List<RetracedType> methodArguments;
    private final String sourceFile;
    private final int lineNumber;
    private final boolean isAmbiguous;
    private final boolean isTopFrame;

    private RetraceStackTraceProxyImpl(
        T originalItem,
        RetracedClass retracedClass,
        RetracedMethod retracedMethod,
        RetracedField retracedField,
        RetracedType fieldOrReturnType,
        List<RetracedType> methodArguments,
        String sourceFile,
        int lineNumber,
        boolean isAmbiguous,
        boolean isTopFrame) {
      assert originalItem != null;
      this.originalItem = originalItem;
      this.retracedClass = retracedClass;
      this.retracedMethod = retracedMethod;
      this.retracedField = retracedField;
      this.fieldOrReturnType = fieldOrReturnType;
      this.methodArguments = methodArguments;
      this.sourceFile = sourceFile;
      this.lineNumber = lineNumber;
      this.isAmbiguous = isAmbiguous;
      this.isTopFrame = isTopFrame;
    }

    @Override
    public boolean isAmbiguous() {
      return isAmbiguous;
    }

    @Override
    public boolean isTopFrame() {
      return isTopFrame;
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
    public boolean hasRetracedField() {
      return retracedField != null;
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
    public boolean hasFieldOrReturnType() {
      return fieldOrReturnType != null;
    }

    @Override
    public boolean hasMethodArguments() {
      return methodArguments != null;
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
    public RetracedField getRetracedField() {
      return retracedField;
    }

    @Override
    public RetracedType getRetracedFieldOrReturnType() {
      return fieldOrReturnType;
    }

    @Override
    public List<RetracedType> getMethodArguments() {
      return methodArguments;
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

    @Override
    public int compareTo(RetraceStackTraceProxy<T> other) {
      int classCompare = Boolean.compare(hasRetracedClass(), other.hasRetracedClass());
      if (classCompare != 0) {
        return classCompare;
      }
      if (hasRetracedClass()) {
        classCompare =
            getRetracedClass().getTypeName().compareTo(other.getRetracedClass().getTypeName());
        if (classCompare != 0) {
          return classCompare;
        }
      }
      int methodCompare = Boolean.compare(hasRetracedMethod(), other.hasRetracedMethod());
      if (methodCompare != 0) {
        return methodCompare;
      }
      if (hasRetracedMethod()) {
        methodCompare = getRetracedMethod().compareTo(other.getRetracedMethod());
        if (methodCompare != 0) {
          return methodCompare;
        }
      }
      int sourceFileCompare = Boolean.compare(hasSourceFile(), other.hasSourceFile());
      if (sourceFileCompare != 0) {
        return sourceFileCompare;
      }
      if (hasSourceFile()) {
        sourceFileCompare = getSourceFile().compareTo(other.getSourceFile());
        if (sourceFileCompare != 0) {
          return sourceFileCompare;
        }
      }
      int lineNumberCompare = Boolean.compare(hasLineNumber(), other.hasLineNumber());
      if (lineNumberCompare != 0) {
        return lineNumberCompare;
      }
      if (hasLineNumber()) {
        return Integer.compare(lineNumber, other.getLineNumber());
      }
      return 0;
    }

    private static class Builder<T extends StackTraceElementProxy<?>> {

      private final T originalElement;
      private RetracedClass classContext;
      private RetracedMethod methodContext;
      private RetracedField retracedField;
      private RetracedType fieldOrReturnType;
      private List<RetracedType> methodArguments;
      private String sourceFile;
      private int lineNumber = -1;
      private boolean isAmbiguous;
      private boolean isTopFrame;

      private Builder(T originalElement) {
        this.originalElement = originalElement;
      }

      private Builder<T> setRetracedClass(RetracedClass retracedClass) {
        this.classContext = retracedClass;
        return this;
      }

      private Builder<T> setRetracedMethod(RetracedMethod methodElement) {
        this.methodContext = methodElement;
        return this;
      }

      private Builder<T> setRetracedField(RetracedField retracedField) {
        this.retracedField = retracedField;
        return this;
      }

      private Builder<T> setRetracedFieldOrReturnType(RetracedType retracedType) {
        this.fieldOrReturnType = retracedType;
        return this;
      }

      private Builder<T> setRetracedMethodArguments(List<RetracedType> arguments) {
        this.methodArguments = arguments;
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

      private Builder<T> setTopFrame(boolean topFrame) {
        isTopFrame = topFrame;
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
            retracedField,
            fieldOrReturnType,
            methodArguments,
            sourceFile,
            lineNumber,
            isAmbiguous,
            isTopFrame);
      }
    }
  }
}
