// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.retrace.MappingSupplierBase;
import com.android.tools.r8.retrace.RetraceInvalidStackTraceLineDiagnostics;
import com.android.tools.r8.retrace.RetraceStackFrameAmbiguousResult;
import com.android.tools.r8.retrace.RetraceStackFrameAmbiguousResultWithContext;
import com.android.tools.r8.retrace.RetraceStackFrameResult;
import com.android.tools.r8.retrace.RetraceStackFrameResultWithContext;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxy;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxyResult;
import com.android.tools.r8.retrace.RetraceStackTraceResult;
import com.android.tools.r8.retrace.RetracedFieldReference;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedTypeReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.StackTraceElementProxyRetracer;
import com.android.tools.r8.retrace.StackTraceLineParser;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RetraceBase<T, ST extends StackTraceElementProxy<T, ST>> {

  private final StackTraceLineParser<T, ST> stackTraceLineParser;
  private final MappingSupplierBase<?> mappingSupplier;
  private final DiagnosticsHandler diagnosticsHandler;
  protected final boolean isVerbose;

  protected RetraceBase(
      StackTraceLineParser<T, ST> stackTraceLineParser,
      MappingSupplierBase<?> mappingSupplier,
      DiagnosticsHandler diagnosticsHandler,
      boolean isVerbose) {
    this.stackTraceLineParser = stackTraceLineParser;
    this.mappingSupplier = mappingSupplier;
    this.diagnosticsHandler = diagnosticsHandler;
    this.isVerbose = isVerbose;
  }

  protected List<ST> parse(List<T> stackTrace) {
    ListUtils.forEachWithIndex(
        stackTrace,
        (line, lineNumber) -> {
          if (line == null) {
            diagnosticsHandler.error(
                RetraceInvalidStackTraceLineDiagnostics.createNull(lineNumber));
            throw new RetraceAbortException();
          }
        });
    return ListUtils.map(stackTrace, stackTraceLineParser::parse);
  }

  protected ST parse(T obfuscated) {
    return stackTraceLineParser.parse(obfuscated);
  }

  protected void registerUses(List<ST> parsed) {
    parsed.forEach(this::registerUses);
  }

  protected void registerUses(ST parsed) {
    parsed.registerUses(mappingSupplier, diagnosticsHandler);
  }

  protected RetraceStackTraceResult<T> retraceStackTraceParsedWithRetracer(
      Retracer retracer, List<ST> stackTrace, RetraceStackTraceContext context) {
    RetraceStackTraceElementProxyEquivalence<T, ST> equivalence =
        new RetraceStackTraceElementProxyEquivalence<>(isVerbose);
    StackTraceElementProxyRetracer<T, ST> proxyRetracer =
        StackTraceElementProxyRetracer.createDefault(retracer);
    List<RetraceStackFrameAmbiguousResult<T>> finalResult = new ArrayList<>();
    RetraceStackTraceContext finalContext =
        ListUtils.fold(
            stackTrace,
            context,
            (newContext, stackTraceLine) -> {
              List<Pair<RetraceStackTraceElementProxy<T, ST>, RetraceStackFrameResult<T>>>
                  resultsForLine = new ArrayList<>();
              Box<List<T>> currentList = new Box<>();
              Set<Wrapper<RetraceStackTraceElementProxy<T, ST>>> seen = new HashSet<>();
              List<RetraceStackTraceContext> contexts = new ArrayList<>();
              RetraceStackTraceElementProxyResult<T, ST> retraceResult =
                  proxyRetracer.retrace(stackTraceLine, newContext);
              retraceResult.stream()
                  .forEach(
                      retracedElement -> {
                        if (retracedElement.isTopFrame() || !retracedElement.hasRetracedClass()) {
                          if (seen.add(equivalence.wrap(retracedElement))) {
                            currentList.set(new ArrayList<>());
                            resultsForLine.add(
                                Pair.create(
                                    retracedElement,
                                    RetraceStackFrameResultWithContextImpl.create(
                                        currentList.get(), RetraceStackTraceContext.empty())));
                            contexts.add(retracedElement.getContext());
                          } else {
                            currentList.clear();
                          }
                        }
                        if (currentList.isSet()) {
                          currentList
                              .get()
                              .add(stackTraceLine.toRetracedItem(retracedElement, isVerbose));
                        }
                      });
              resultsForLine.sort(Comparator.comparing(Pair::getFirst));
              finalResult.add(
                  RetraceStackFrameAmbiguousResultWithContextImpl.create(
                      ListUtils.map(resultsForLine, Pair::getSecond),
                      RetraceStackTraceContext.empty()));
              if (contexts.isEmpty()) {
                return retraceResult.getResultContext();
              }
              return contexts.size() == 1 ? contexts.get(0) : RetraceStackTraceContext.empty();
            });
    return RetraceStackTraceResultImpl.create(finalResult, finalContext);
  }

  protected RetraceStackFrameAmbiguousResultWithContext<T> retraceFrameWithRetracer(
      Retracer retracer, ST parsedFrame, RetraceStackTraceContext context) {
    Map<RetraceStackTraceElementProxy<T, ST>, List<T>> ambiguousBlocks = new HashMap<>();
    List<RetraceStackTraceElementProxy<T, ST>> ambiguousKeys = new ArrayList<>();
    StackTraceElementProxyRetracer<T, ST> proxyRetracer =
        StackTraceElementProxyRetracer.createDefault(retracer);
    Box<RetraceStackTraceContext> contextBox = new Box<>(context);
    proxyRetracer.retrace(parsedFrame, context).stream()
        .forEach(
            retracedElement -> {
              if (retracedElement.isTopFrame() || !retracedElement.hasRetracedClass()) {
                ambiguousKeys.add(retracedElement);
                ambiguousBlocks.put(retracedElement, new ArrayList<>());
              }
              ambiguousBlocks
                  .get(ListUtils.last(ambiguousKeys))
                  .add(parsedFrame.toRetracedItem(retracedElement, isVerbose));
              contextBox.set(retracedElement.getContext());
            });
    Collections.sort(ambiguousKeys);
    List<RetraceStackFrameResult<T>> retracedList = new ArrayList<>();
    ambiguousKeys.forEach(
        key ->
            retracedList.add(
                RetraceStackFrameResultWithContextImpl.create(
                    ambiguousBlocks.get(key), RetraceStackTraceContext.empty())));
    return RetraceStackFrameAmbiguousResultWithContextImpl.create(retracedList, contextBox.get());
  }

  protected RetraceStackFrameResultWithContext<T> retraceLineWithRetracer(
      Retracer retracer, ST parsedFrame, RetraceStackTraceContext context) {
    StackTraceElementProxyRetracer<T, ST> proxyRetracer =
        StackTraceElementProxyRetracer.createDefault(retracer);
    Box<RetraceStackTraceContext> contextBox = new Box<>(context);
    List<T> result =
        proxyRetracer.retrace(parsedFrame, context).stream()
            .map(
                retraceFrame -> {
                  contextBox.set(retraceFrame.getContext());
                  return parsedFrame.toRetracedItem(retraceFrame, isVerbose);
                })
            .collect(Collectors.toList());
    return RetraceStackFrameResultWithContextImpl.create(result, contextBox.get());
  }

  private static class RetraceStackTraceElementProxyEquivalence<
          T, ST extends StackTraceElementProxy<T, ST>>
      extends Equivalence<RetraceStackTraceElementProxy<T, ST>> {

    private final boolean isVerbose;

    public RetraceStackTraceElementProxyEquivalence(boolean isVerbose) {
      this.isVerbose = isVerbose;
    }

    @Override
    protected boolean doEquivalent(
        RetraceStackTraceElementProxy<T, ST> one, RetraceStackTraceElementProxy<T, ST> other) {
      if (one == other) {
        return true;
      }
      if (testNotEqualProperty(
              one,
              other,
              RetraceStackTraceElementProxy::hasRetracedClass,
              r -> r.getRetracedClass().getTypeName())
          || testNotEqualProperty(
              one,
              other,
              RetraceStackTraceElementProxy::hasSourceFile,
              RetraceStackTraceElementProxy::getSourceFile)) {
        return false;
      }
      assert one.getOriginalItem() == other.getOriginalItem();
      if (isVerbose
          || (one.getOriginalItem().hasLineNumber() && one.getOriginalItem().getLineNumber() > 0)) {
        if (testNotEqualProperty(
            one,
            other,
            RetraceStackTraceElementProxy::hasLineNumber,
            RetraceStackTraceElementProxy::getLineNumber)) {
          return false;
        }
      }
      if (one.hasRetracedMethod() != other.hasRetracedMethod()) {
        return false;
      }
      if (one.hasRetracedMethod()) {
        RetracedMethodReference oneMethod = one.getRetracedMethod();
        RetracedMethodReference otherMethod = other.getRetracedMethod();
        if (oneMethod.isKnown() != otherMethod.isKnown()) {
          return false;
        }
        // In verbose mode we check the signature, otherwise we only check the name
        if (!oneMethod.getMethodName().equals(otherMethod.getMethodName())) {
          return false;
        }
        if (isVerbose
            && ((oneMethod.isKnown()
                    && !oneMethod
                        .asKnown()
                        .getMethodReference()
                        .toString()
                        .equals(otherMethod.asKnown().getMethodReference().toString()))
                || (!oneMethod.isKnown()
                    && !oneMethod.getMethodName().equals(otherMethod.getMethodName())))) {
          return false;
        }
      }
      if (one.hasRetracedField() != other.hasRetracedField()) {
        return false;
      }
      if (one.hasRetracedField()) {
        RetracedFieldReference oneField = one.getRetracedField();
        RetracedFieldReference otherField = other.getRetracedField();
        if (oneField.isKnown() != otherField.isKnown()) {
          return false;
        }
        if (!oneField.getFieldName().equals(otherField.getFieldName())) {
          return false;
        }
        if (isVerbose
            && ((oneField.isKnown()
                    && !oneField
                        .asKnown()
                        .getFieldReference()
                        .toString()
                        .equals(otherField.asKnown().getFieldReference().toString()))
                || (oneField.isUnknown()
                    && !oneField.getFieldName().equals(otherField.getFieldName())))) {
          return false;
        }
      }
      if (one.hasRetracedFieldOrReturnType() != other.hasRetracedFieldOrReturnType()) {
        return false;
      }
      if (one.hasRetracedFieldOrReturnType()) {
        RetracedTypeReference oneFieldOrReturn = one.getRetracedFieldOrReturnType();
        RetracedTypeReference otherFieldOrReturn = other.getRetracedFieldOrReturnType();
        if (!compareRetracedTypeReference(oneFieldOrReturn, otherFieldOrReturn)) {
          return false;
        }
      }
      if (one.hasRetracedMethodArguments() != other.hasRetracedMethodArguments()) {
        return false;
      }
      if (one.hasRetracedMethodArguments()) {
        List<RetracedTypeReference> oneMethodArguments = one.getRetracedMethodArguments();
        List<RetracedTypeReference> otherMethodArguments = other.getRetracedMethodArguments();
        if (oneMethodArguments.size() != otherMethodArguments.size()) {
          return false;
        }
        for (int i = 0; i < oneMethodArguments.size(); i++) {
          if (compareRetracedTypeReference(
              oneMethodArguments.get(i), otherMethodArguments.get(i))) {
            return false;
          }
        }
      }
      return true;
    }

    private boolean compareRetracedTypeReference(
        RetracedTypeReference one, RetracedTypeReference other) {
      return one.isVoid() == other.isVoid()
          && (one.isVoid() || one.getTypeName().equals(other.getTypeName()));
    }

    @Override
    protected int doHash(RetraceStackTraceElementProxy<T, ST> proxy) {
      return 0;
    }

    private <V extends Comparable<V>> boolean testNotEqualProperty(
        RetraceStackTraceElementProxy<T, ST> one,
        RetraceStackTraceElementProxy<T, ST> other,
        Function<RetraceStackTraceElementProxy<T, ST>, Boolean> predicate,
        Function<RetraceStackTraceElementProxy<T, ST>, V> getter) {
      return Comparator.comparing(predicate)
              .thenComparing(getter, Comparator.nullsFirst(V::compareTo))
              .compare(one, other)
          != 0;
    }
  }
}
