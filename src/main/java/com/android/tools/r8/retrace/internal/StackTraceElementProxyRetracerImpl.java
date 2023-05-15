// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceClassResult;
import com.android.tools.r8.retrace.RetraceFieldElement;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxy;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxyResult;
import com.android.tools.r8.retrace.RetraceThrownExceptionElement;
import com.android.tools.r8.retrace.RetraceTypeElement;
import com.android.tools.r8.retrace.RetraceTypeResult;
import com.android.tools.r8.retrace.RetracedClassReference;
import com.android.tools.r8.retrace.RetracedFieldReference;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSingleFrame;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.RetracedTypeReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.StackTraceElementProxyRetracer;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StackTraceElementProxyRetracerImpl<T, ST extends StackTraceElementProxy<T, ST>>
    implements StackTraceElementProxyRetracer<T, ST> {

  private final Retracer retracer;

  public StackTraceElementProxyRetracerImpl(Retracer retracer) {
    this.retracer = retracer;
  }

  @Override
  public RetraceStackTraceElementProxyResult<T, ST> retrace(
      ST element, RetraceStackTraceContext context) {
    RetraceStackTraceElementProxyResultImpl<T, ST> currentResult =
        RetraceStackTraceElementProxyResultImpl.Builder.<T, ST>create()
            .setResultStream(Stream.of(RetraceStackTraceElementProxyImpl.create(element, context)))
            .setResultContext(RetraceStackTraceContext::empty)
            .build();
    if (!element.hasClassName()
        && !element.hasFieldOrReturnType()
        && !element.hasMethodArguments()) {
      return currentResult;
    }
    currentResult = retraceFieldOrReturnType(currentResult, element);
    currentResult = retracedMethodArguments(currentResult, element);
    if (element.hasClassName()) {
      RetraceClassResult classResult = retracer.retraceClass(element.getClassReference());
      if (element.hasMethodName()) {
        currentResult = retraceMethod(currentResult, element, classResult, context);
      } else if (element.hasFieldName()) {
        currentResult = retraceField(currentResult, element, classResult);
      } else {
        currentResult = retraceClassOrType(currentResult, classResult);
      }
    }
    return currentResult;
  }

  private RetraceStackTraceElementProxyResultImpl<T, ST> retraceClassOrType(
      RetraceStackTraceElementProxyResultImpl<T, ST> currentResult,
      RetraceClassResult classResult) {
    return currentResult
        .builder()
        .setResultStream(
            currentResult.stream()
                .flatMap(
                    proxy ->
                        // We assume, since no method was defined for this stack trace element, that
                        // this was a thrown exception.
                        classResult.lookupThrownException(proxy.getContext()).stream()
                            .map(
                                thrownExceptionElement ->
                                    buildProxyForRewrittenThrownExceptionElement(
                                        classResult, proxy, thrownExceptionElement))))
        .build();
  }

  private RetraceStackTraceElementProxyImpl<T, ST> buildProxyForRewrittenThrownExceptionElement(
      RetraceClassResult classResult,
      RetraceStackTraceElementProxyImpl<T, ST> proxy,
      RetraceThrownExceptionElement thrownExceptionElement) {
    return proxy
        .builder()
        .setRetracedClass(thrownExceptionElement.getRetracedClass())
        .joinAmbiguous(classResult.isAmbiguous())
        .setTopFrame(true)
        .setContext(thrownExceptionElement.getContext())
        .apply(setSourceFileOnProxy(thrownExceptionElement::getSourceFile))
        .build();
  }

  private RetraceStackTraceElementProxyResultImpl<T, ST> retraceMethod(
      RetraceStackTraceElementProxyResultImpl<T, ST> currentResult,
      ST element,
      RetraceClassResult classResult,
      RetraceStackTraceContext context) {
    Box<RetraceStackTraceContext> resultingContext = new Box<>(RetraceStackTraceContext.empty());
    RetraceStackTraceElementProxyResultImpl.Builder<T, ST> resultBuilder =
        currentResult.builder().setResultContext(resultingContext::get);
    return resultBuilder
        .setResultStream(
            currentResult.stream()
                .flatMap(
                    proxy -> {
                      RetraceFrameResult frameResult =
                          classResult.lookupFrame(
                              context,
                              element.hasLineNumber()
                                  ? OptionalInt.of(element.getLineNumber())
                                  : OptionalInt.empty(),
                              element.getMethodName());
                      if (frameResult.isEmpty()) {
                        return classResult.stream()
                            .map(
                                classElement ->
                                    proxy
                                        .builder()
                                        .setTopFrame(true)
                                        .joinAmbiguous(classResult.isAmbiguous())
                                        .setRetracedClass(classElement.getRetracedClass())
                                        .applyIf(
                                            element.hasLineNumber(),
                                            b -> b.setLineNumber(element.getLineNumber()))
                                        .apply(setSourceFileOnProxy(classElement::getSourceFile))
                                        .build());
                      }
                      return frameResult.stream()
                          .flatMap(
                              frameElement -> {
                                resultingContext.set(frameElement.getRetraceStackTraceContext());
                                return frameElement
                                    .streamRewritten(context)
                                    .map(
                                        singleFrame ->
                                            buildProxyForRewrittenFrameElement(
                                                element,
                                                proxy,
                                                frameResult,
                                                frameElement,
                                                singleFrame));
                              });
                    }))
        .build();
  }

  private RetraceStackTraceElementProxyImpl<T, ST> buildProxyForRewrittenFrameElement(
      ST element,
      RetraceStackTraceElementProxyImpl<T, ST> proxy,
      RetraceFrameResult frameResult,
      RetraceFrameElement frameElement,
      RetracedSingleFrame singleFrame) {
    boolean isTopFrame = singleFrame.getIndex() == 0;
    RetracedMethodReference method = singleFrame.getMethodReference();
    return proxy
        .builder()
        .setRetracedClass(method.getHolderClass())
        .setRetracedMethod(method)
        .joinAmbiguous(frameResult.isAmbiguous())
        .setTopFrame(isTopFrame)
        .setContext(frameElement.getRetraceStackTraceContext())
        .applyIf(
            element.hasLineNumber(),
            builder ->
                builder.setLineNumber(method.getOriginalPositionOrDefault(element.getLineNumber())))
        .apply(setSourceFileOnProxy(() -> frameElement.getSourceFile(method)))
        .build();
  }

  private RetraceStackTraceElementProxyResultImpl<T, ST> retraceField(
      RetraceStackTraceElementProxyResultImpl<T, ST> currentResult,
      ST element,
      RetraceClassResult classResult) {
    return currentResult
        .builder()
        .setResultStream(
            currentResult.stream()
                .flatMap(
                    proxy -> {
                      RetraceFieldResult retraceFieldResult =
                          classResult.lookupField(element.getFieldName());
                      return retraceFieldResult.stream()
                          .map(
                              fieldElement ->
                                  buildProxyForRewrittenFieldElement(
                                      proxy, retraceFieldResult, fieldElement));
                    }))
        .build();
  }

  private RetraceStackTraceElementProxyImpl<T, ST> buildProxyForRewrittenFieldElement(
      RetraceStackTraceElementProxyImpl<T, ST> proxy,
      RetraceFieldResult retraceFieldResult,
      RetraceFieldElement fieldElement) {
    return proxy
        .builder()
        .setRetracedClass(fieldElement.getField().getHolderClass())
        .setRetracedField(fieldElement.getField())
        .joinAmbiguous(retraceFieldResult.isAmbiguous())
        .setTopFrame(true)
        .apply(setSourceFileOnProxy(fieldElement::getSourceFile))
        .build();
  }

  private Consumer<RetraceStackTraceElementProxyImpl.Builder<T, ST>> setSourceFileOnProxy(
      Supplier<RetracedSourceFile> sourceFile) {
    return proxy -> {
      ST original = proxy.originalElement;
      if (!original.hasSourceFile()) {
        return;
      }
      proxy.setSourceFile(sourceFile.get());
    };
  }

  private RetraceStackTraceElementProxyResultImpl<T, ST> retraceFieldOrReturnType(
      RetraceStackTraceElementProxyResultImpl<T, ST> currentResult, ST element) {
    if (!element.hasFieldOrReturnType()) {
      return currentResult;
    }
    RetraceStackTraceElementProxyResultImpl.Builder<T, ST> resultBuilder = currentResult.builder();
    String elementOrReturnType = element.getFieldOrReturnType();
    if (elementOrReturnType.equals("void")) {
      return resultBuilder
          .setResultStream(
              currentResult.stream()
                  .map(
                      proxy ->
                          buildProxyForRewrittenReturnType(
                              proxy, RetracedTypeReferenceImpl.createVoid(), proxy.isAmbiguous())))
          .build();
    } else {
      TypeReference typeReference = Reference.typeFromTypeName(elementOrReturnType);
      RetraceTypeResult retraceTypeResult = retracer.retraceType(typeReference);
      List<RetraceTypeElement> retracedElements =
          retraceTypeResult.stream().collect(Collectors.toList());
      return resultBuilder
          .setResultStream(
              currentResult.stream()
                  .flatMap(
                      proxy ->
                          retracedElements.stream()
                              .map(
                                  retracedResult ->
                                      buildProxyForRewrittenReturnType(
                                          proxy,
                                          retracedResult.getType(),
                                          retraceTypeResult.isAmbiguous()))))
          .build();
    }
  }

  private RetraceStackTraceElementProxyImpl<T, ST> buildProxyForRewrittenReturnType(
      RetraceStackTraceElementProxyImpl<T, ST> proxy,
      RetracedTypeReference type,
      boolean isAmbiguous) {
    return proxy.builder().setRetracedFieldOrReturnType(type).joinAmbiguous(isAmbiguous).build();
  }

  private RetraceStackTraceElementProxyResultImpl<T, ST> retracedMethodArguments(
      RetraceStackTraceElementProxyResultImpl<T, ST> currentResult, ST element) {
    if (!element.hasMethodArguments()) {
      return currentResult;
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
    return currentResult
        .builder()
        .setResultStream(
            currentResult.stream()
                .flatMap(
                    proxy ->
                        allRetracedArguments.stream()
                            .map(
                                retracedArguments ->
                                    proxy
                                        .builder()
                                        .setRetracedMethodArguments(retracedArguments)
                                        .joinAmbiguous(isAmbiguous)
                                        .build())))
        .build();
  }

  static class RetraceStackTraceElementProxyImpl<T, ST extends StackTraceElementProxy<T, ST>>
      implements RetraceStackTraceElementProxy<T, ST> {

    private final ST originalItem;
    private final RetracedClassReference retracedClass;
    private final RetracedMethodReference retracedMethod;
    private final RetracedFieldReference retracedField;
    private final RetracedTypeReference fieldOrReturnType;
    private final List<RetracedTypeReference> methodArguments;
    private final RetracedSourceFile sourceFile;
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
        RetracedSourceFile sourceFile,
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
    public boolean hasRetracedFieldOrReturnType() {
      return fieldOrReturnType != null;
    }

    @Override
    public boolean hasRetracedMethodArguments() {
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
    public List<RetracedTypeReference> getRetracedMethodArguments() {
      return methodArguments;
    }

    @Override
    public String getSourceFile() {
      if (sourceFile == null) {
        assert originalItem.getSourceFile() == null;
        return null;
      }
      return sourceFile.getOrInferSourceFile(
          originalItem.getSourceFile() == null ? "" : originalItem.getSourceFile());
    }

    @Override
    public RetracedSourceFile getRetracedSourceFile() {
      return sourceFile;
    }

    private static <T, ST extends StackTraceElementProxy<T, ST>>
        RetraceStackTraceElementProxyImpl<T, ST> create(
            ST originalItem, RetraceStackTraceContext context) {
      return new RetraceStackTraceElementProxyImpl<T, ST>(
          originalItem, null, null, null, null, null, null, -1, false, false, context);
    }

    Builder<T, ST> builder() {
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
      if (this == other) {
        return 0;
      }
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
      private RetracedSourceFile sourceFile;
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

      private Builder<T, ST> setSourceFile(RetracedSourceFile sourceFile) {
        assert sourceFile != null;
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

      private Builder<T, ST> apply(Consumer<Builder<T, ST>> consumer) {
        consumer.accept(this);
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
