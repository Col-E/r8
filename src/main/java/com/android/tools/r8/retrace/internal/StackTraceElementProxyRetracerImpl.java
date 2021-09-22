// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassResult;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxy;
import com.android.tools.r8.retrace.RetraceTypeResult;
import com.android.tools.r8.retrace.RetraceTypeResult.Element;
import com.android.tools.r8.retrace.RetracedClassReference;
import com.android.tools.r8.retrace.RetracedFieldReference;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.RetracedTypeReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.StackTraceElementProxyRetracer;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StackTraceElementProxyRetracerImpl<T, ST extends StackTraceElementProxy<T, ST>>
    implements StackTraceElementProxyRetracer<T, ST> {

  private final Retracer retracer;

  public StackTraceElementProxyRetracerImpl(Retracer retracer) {
    this.retracer = retracer;
  }

  @Override
  public Stream<? extends RetraceStackTraceElementProxy<T, ST>> retrace(
      ST element, RetraceStackTraceContext context) {
    Stream<RetraceStackTraceElementProxyImpl<T, ST>> currentResults =
        Stream.of(RetraceStackTraceElementProxyImpl.create(element, context));
    if (!element.hasClassName()
        && !element.hasFieldOrReturnType()
        && !element.hasMethodArguments()) {
      return currentResults;
    }
    currentResults = retraceFieldOrReturnType(currentResults, element);
    currentResults = retracedMethodArguments(currentResults, element);
    if (element.hasClassName()) {
      RetraceClassResult classResult = retracer.retraceClass(element.getClassReference());
      if (element.hasMethodName()) {
        currentResults = retraceMethod(currentResults, element, classResult);
      } else if (element.hasFieldName()) {
        currentResults = retraceField(currentResults, element, classResult);
      } else {
        currentResults = retraceClassOrType(currentResults, element, classResult);
      }
    }
    return currentResults;
  }

  private Stream<RetraceStackTraceElementProxyImpl<T, ST>> retraceClassOrType(
      Stream<RetraceStackTraceElementProxyImpl<T, ST>> currentResults,
      ST element,
      RetraceClassResult classResult) {
    return currentResults.flatMap(
        proxy ->
            classResult.stream()
                .map(
                    classElement ->
                        proxy
                            .builder()
                            .setRetracedClass(classElement.getRetracedClass())
                            .joinAmbiguous(classResult.isAmbiguous())
                            .setTopFrame(true)
                            // We assume, since no method was defined for this stack trace element,
                            // that this was a thrown exception.
                            .setContext(classElement.getContextWhereClassWasThrown())
                            .applyIf(
                                element.hasSourceFile(),
                                builder -> {
                                  RetracedSourceFile sourceFile = classElement.getSourceFile();
                                  builder.setSourceFile(
                                      sourceFile.hasRetraceResult()
                                          ? sourceFile.getSourceFile()
                                          : RetraceUtils.inferSourceFile(
                                              classElement.getRetracedClass().getTypeName(),
                                              element.getSourceFile(),
                                              classResult.hasRetraceResult()));
                                })
                            .build()));
  }

  private Stream<RetraceStackTraceElementProxyImpl<T, ST>> retraceMethod(
      Stream<RetraceStackTraceElementProxyImpl<T, ST>> currentResults,
      ST element,
      RetraceClassResult classResult) {
    return currentResults.flatMap(
        proxy -> {
          RetraceFrameResult frameResult =
              classResult.lookupFrame(
                  proxy.context,
                  element.hasLineNumber() ? Optional.of(element.getLineNumber()) : Optional.empty(),
                  element.getMethodName());
          return frameResult.stream()
              .flatMap(
                  frameElement -> {
                    List<RetraceStackTraceElementProxyImpl<T, ST>> retracedProxies =
                        new ArrayList<>();
                    frameElement.visitRewrittenFrames(
                        proxy.getContext(),
                        (frame, index) -> {
                          boolean isTopFrame = index == 0;
                          retracedProxies.add(
                              proxy
                                  .builder()
                                  .setRetracedClass(frame.getHolderClass())
                                  .setRetracedMethod(frame)
                                  .joinAmbiguous(frameResult.isAmbiguous() && isTopFrame)
                                  .setTopFrame(isTopFrame)
                                  .setContext(frameElement.getContext())
                                  .applyIf(
                                      element.hasLineNumber(),
                                      builder -> {
                                        builder.setLineNumber(
                                            frame.getOriginalPositionOrDefault(
                                                element.getLineNumber()));
                                      })
                                  .applyIf(
                                      element.hasSourceFile(),
                                      builder -> {
                                        RetracedSourceFile sourceFileResult =
                                            frameElement.getSourceFile(frame);
                                        builder.setSourceFile(
                                            sourceFileResult.hasRetraceResult()
                                                ? sourceFileResult.getSourceFile()
                                                : RetraceUtils.inferSourceFile(
                                                    frame.getHolderClass().getTypeName(),
                                                    element.getSourceFile(),
                                                    classResult.hasRetraceResult()));
                                      })
                                  .build());
                        });
                    return retracedProxies.stream();
                  });
        });
  }

  private Stream<RetraceStackTraceElementProxyImpl<T, ST>> retraceField(
      Stream<RetraceStackTraceElementProxyImpl<T, ST>> currentResults,
      ST element,
      RetraceClassResult classResult) {
    return currentResults.flatMap(
        proxy -> {
          RetraceFieldResult retraceFieldResult = classResult.lookupField(element.getFieldName());
          return retraceFieldResult.stream()
              .map(
                  fieldElement ->
                      proxy
                          .builder()
                          .setRetracedClass(fieldElement.getField().getHolderClass())
                          .setRetracedField(fieldElement.getField())
                          .joinAmbiguous(retraceFieldResult.isAmbiguous())
                          .setTopFrame(true)
                          .applyIf(
                              element.hasSourceFile(),
                              builder -> {
                                RetracedSourceFile sourceFile = fieldElement.getSourceFile();
                                builder.setSourceFile(
                                    sourceFile.hasRetraceResult()
                                        ? sourceFile.getSourceFile()
                                        : RetraceUtils.inferSourceFile(
                                            fieldElement.getField().getHolderClass().getTypeName(),
                                            element.getSourceFile(),
                                            classResult.hasRetraceResult()));
                              })
                          .build());
        });
  }

  private Stream<RetraceStackTraceElementProxyImpl<T, ST>> retraceFieldOrReturnType(
      Stream<RetraceStackTraceElementProxyImpl<T, ST>> currentResults, ST element) {
    if (!element.hasFieldOrReturnType()) {
      return currentResults;
    }
    String elementOrReturnType = element.getFieldOrReturnType();
    if (elementOrReturnType.equals("void")) {
      return currentResults.map(
          proxy ->
              proxy
                  .builder()
                  .setRetracedFieldOrReturnType(RetracedTypeReferenceImpl.createVoid())
                  .build());
    } else {
      TypeReference typeReference = Reference.typeFromTypeName(elementOrReturnType);
      RetraceTypeResult retraceTypeResult = retracer.retraceType(typeReference);
      List<Element> retracedElements = retraceTypeResult.stream().collect(Collectors.toList());
      return currentResults.flatMap(
          proxy ->
              retracedElements.stream()
                  .map(
                      retracedResult ->
                          proxy
                              .builder()
                              .setRetracedFieldOrReturnType(retracedResult.getType())
                              .joinAmbiguous(retraceTypeResult.isAmbiguous())
                              .build()));
    }
  }

  private Stream<RetraceStackTraceElementProxyImpl<T, ST>> retracedMethodArguments(
      Stream<RetraceStackTraceElementProxyImpl<T, ST>> currentResults, ST element) {
    if (!element.hasMethodArguments()) {
      return currentResults;
    }
    List<RetraceTypeResult> retracedResults =
        Arrays.stream(element.getMethodArguments().split(","))
            .map(typeName -> retracer.retraceType(Reference.typeFromTypeName(typeName)))
            .collect(Collectors.toList());
    List<List<RetracedTypeReference>> initial = new ArrayList<>();
    initial.add(new ArrayList<>());
    List<List<RetracedTypeReference>> allRetracedArguments =
        ListUtils.fold(
            retracedResults,
            initial,
            (acc, retracedTypeResult) -> {
              List<List<RetracedTypeReference>> newResult = new ArrayList<>();
              retracedTypeResult.forEach(
                  retracedElement ->
                      acc.forEach(
                          oldResult -> {
                            List<RetracedTypeReference> newList = new ArrayList<>(oldResult);
                            newList.add(retracedElement.getType());
                            newResult.add(newList);
                          }));
              return newResult;
            });
    boolean isAmbiguous = allRetracedArguments.size() > 1;
    return currentResults.flatMap(
        proxy ->
            allRetracedArguments.stream()
                .map(
                    retracedArguments ->
                        proxy
                            .builder()
                            .setRetracedMethodArguments(retracedArguments)
                            .joinAmbiguous(isAmbiguous)
                            .build()));
  }

  public static class RetraceStackTraceElementProxyImpl<T, ST extends StackTraceElementProxy<T, ST>>
      implements RetraceStackTraceElementProxy<T, ST> {

    private final ST originalItem;
    private final RetracedClassReference retracedClass;
    private final RetracedMethodReference retracedMethod;
    private final RetracedFieldReference retracedField;
    private final RetracedTypeReference fieldOrReturnType;
    private final List<RetracedTypeReference> methodArguments;
    private final String sourceFile;
    private final int lineNumber;
    private final boolean isAmbiguous;
    private final boolean isTopFrame;
    private final RetraceStackTraceContext context;

    private RetraceStackTraceElementProxyImpl(
        ST originalItem,
        RetracedClassReference retracedClass,
        RetracedMethodReference retracedMethod,
        RetracedFieldReference retracedField,
        RetracedTypeReference fieldOrReturnType,
        List<RetracedTypeReference> methodArguments,
        String sourceFile,
        int lineNumber,
        boolean isAmbiguous,
        boolean isTopFrame,
        RetraceStackTraceContext context) {
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
      this.context = context;
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
    public ST getOriginalItem() {
      return originalItem;
    }

    @Override
    public RetracedClassReference getRetracedClass() {
      return retracedClass;
    }

    @Override
    public RetracedMethodReference getRetracedMethod() {
      return retracedMethod;
    }

    @Override
    public RetracedFieldReference getRetracedField() {
      return retracedField;
    }

    @Override
    public RetracedTypeReference getRetracedFieldOrReturnType() {
      return fieldOrReturnType;
    }

    @Override
    public List<RetracedTypeReference> getMethodArguments() {
      return methodArguments;
    }

    @Override
    public String getSourceFile() {
      return sourceFile;
    }

    private static <T, ST extends StackTraceElementProxy<T, ST>>
        RetraceStackTraceElementProxyImpl<T, ST> create(
            ST originalItem, RetraceStackTraceContext context) {
      return new RetraceStackTraceElementProxyImpl<T, ST>(
          originalItem, null, null, null, null, null, null, -1, false, false, context);
    }

    private Builder<T, ST> builder() {
      Builder<T, ST> builder = new Builder<>(originalItem);
      builder.classContext = retracedClass;
      builder.methodContext = retracedMethod;
      builder.retracedField = retracedField;
      builder.fieldOrReturnType = fieldOrReturnType;
      builder.methodArguments = methodArguments;
      builder.sourceFile = sourceFile;
      builder.lineNumber = lineNumber;
      builder.isAmbiguous = isAmbiguous;
      builder.isTopFrame = isTopFrame;
      builder.context = context;
      return builder;
    }

    @Override
    public int getLineNumber() {
      return lineNumber;
    }

    @Override
    public RetraceStackTraceContext getContext() {
      return context;
    }

    @Override
    public int compareTo(RetraceStackTraceElementProxy<T, ST> other) {
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

    private static class Builder<T, ST extends StackTraceElementProxy<T, ST>> {

      private final ST originalElement;
      private RetracedClassReference classContext;
      private RetracedMethodReference methodContext;
      private RetracedFieldReference retracedField;
      private RetracedTypeReference fieldOrReturnType;
      private List<RetracedTypeReference> methodArguments;
      private String sourceFile;
      private int lineNumber = -1;
      private boolean isAmbiguous;
      private boolean isTopFrame;
      private RetraceStackTraceContext context;

      private Builder(ST originalElement) {
        this.originalElement = originalElement;
      }

      private Builder<T, ST> setRetracedClass(RetracedClassReference retracedClass) {
        this.classContext = retracedClass;
        return this;
      }

      private Builder<T, ST> setRetracedMethod(RetracedMethodReference methodElement) {
        this.methodContext = methodElement;
        return this;
      }

      private Builder<T, ST> setRetracedField(RetracedFieldReference retracedField) {
        this.retracedField = retracedField;
        return this;
      }

      private Builder<T, ST> setRetracedFieldOrReturnType(RetracedTypeReference retracedType) {
        this.fieldOrReturnType = retracedType;
        return this;
      }

      private Builder<T, ST> setRetracedMethodArguments(List<RetracedTypeReference> arguments) {
        this.methodArguments = arguments;
        return this;
      }

      private Builder<T, ST> setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
        return this;
      }

      private Builder<T, ST> setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
        return this;
      }

      private Builder<T, ST> joinAmbiguous(boolean ambiguous) {
        this.isAmbiguous = ambiguous || this.isAmbiguous;
        return this;
      }

      private Builder<T, ST> setTopFrame(boolean topFrame) {
        isTopFrame = topFrame;
        return this;
      }

      private Builder<T, ST> setContext(RetraceStackTraceContext context) {
        this.context = context;
        return this;
      }

      private Builder<T, ST> applyIf(boolean condition, Consumer<Builder<T, ST>> consumer) {
        if (condition) {
          consumer.accept(this);
        }
        return this;
      }

      private RetraceStackTraceElementProxyImpl<T, ST> build() {
        RetracedClassReference retracedClass = classContext;
        if (methodContext != null) {
          retracedClass = methodContext.getHolderClass();
        }
        return new RetraceStackTraceElementProxyImpl<>(
            originalElement,
            retracedClass,
            methodContext,
            retracedField,
            fieldOrReturnType,
            methodArguments,
            sourceFile,
            lineNumber,
            isAmbiguous,
            isTopFrame,
            context);
      }
    }
  }
}
