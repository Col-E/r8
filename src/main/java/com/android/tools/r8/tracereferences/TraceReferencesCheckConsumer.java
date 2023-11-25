// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.diagnostic.internal.MissingClassInfoImpl;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionsDiagnosticImpl;
import com.android.tools.r8.diagnostic.internal.MissingFieldInfoImpl;
import com.android.tools.r8.diagnostic.internal.MissingMethodInfoImpl;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.PackageReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link TraceReferencesConsumer.ForwardingConsumer}, which forwards all callbacks to the wrapped
 * {@link TraceReferencesConsumer}.
 *
 * <p>This consumer collects the set of missing definitions and reports a {@link
 * com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic} as an error, if any missing
 * definitions were found.
 */
@KeepForApi
public class TraceReferencesCheckConsumer extends TraceReferencesConsumer.ForwardingConsumer {

  private final Map<ClassReference, Map<Object, DefinitionContext>> missingClassesContexts =
      new ConcurrentHashMap<>();
  private final Map<FieldReference, Map<Object, DefinitionContext>> missingFieldsContexts =
      new ConcurrentHashMap<>();
  private final Map<MethodReference, Map<Object, DefinitionContext>> missingMethodsContexts =
      new ConcurrentHashMap<>();

  public TraceReferencesCheckConsumer(TraceReferencesConsumer consumer) {
    super(consumer);
  }

  @Override
  public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
    super.acceptType(tracedClass, handler);
    if (tracedClass.isMissingDefinition()) {
      Map<Object, DefinitionContext> missingClassContexts =
          missingClassesContexts.computeIfAbsent(
              tracedClass.getReference(), ignore -> new ConcurrentHashMap<>());
      DefinitionContextUtils.accept(
          tracedClass.getReferencedFromContext(),
          classContext -> missingClassContexts.put(classContext.getClassReference(), classContext),
          fieldContext -> missingClassContexts.put(fieldContext.getFieldReference(), fieldContext),
          methodContext ->
              missingClassContexts.put(methodContext.getMethodReference(), methodContext));
    }
  }

  @Override
  public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
    super.acceptField(tracedField, handler);
    if (tracedField.isMissingDefinition()) {
      Map<Object, DefinitionContext> missingFieldContexts =
          missingFieldsContexts.computeIfAbsent(
              tracedField.getReference(), ignore -> new ConcurrentHashMap<>());
      DefinitionContextUtils.accept(
          tracedField.getReferencedFromContext(),
          classContext -> missingFieldContexts.put(classContext.getClassReference(), classContext),
          fieldContext -> missingFieldContexts.put(fieldContext.getFieldReference(), fieldContext),
          methodContext ->
              missingFieldContexts.put(methodContext.getMethodReference(), methodContext));
    }
  }

  @Override
  public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
    super.acceptMethod(tracedMethod, handler);
    if (tracedMethod.isMissingDefinition()) {
      Map<Object, DefinitionContext> missingMethodContexts =
          missingMethodsContexts.computeIfAbsent(
              tracedMethod.getReference(), ignore -> new ConcurrentHashMap<>());
      DefinitionContextUtils.accept(
          tracedMethod.getReferencedFromContext(),
          classContext -> missingMethodContexts.put(classContext.getClassReference(), classContext),
          fieldContext -> missingMethodContexts.put(fieldContext.getFieldReference(), fieldContext),
          methodContext ->
              missingMethodContexts.put(methodContext.getMethodReference(), methodContext));
    }
  }

  @Override
  public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
    super.acceptPackage(pkg, handler);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    super.finished(handler);
    if (!isEmpty()) {
      handler.error(buildDiagnostic());
    }
  }

  private boolean isEmpty() {
    return missingClassesContexts.isEmpty()
        && missingFieldsContexts.isEmpty()
        && missingMethodsContexts.isEmpty();
  }

  private MissingDefinitionsDiagnostic buildDiagnostic() {
    MissingDefinitionsDiagnosticImpl.Builder diagnosticBuilder =
        MissingDefinitionsDiagnosticImpl.builder();
    missingClassesContexts.forEach(
        (reference, referencedFrom) ->
            diagnosticBuilder.addMissingDefinitionInfo(
                MissingClassInfoImpl.builder()
                    .setClass(reference)
                    .addReferencedFromContexts(referencedFrom.values())
                    .build()));
    missingFieldsContexts.forEach(
        (reference, referencedFrom) ->
            diagnosticBuilder.addMissingDefinitionInfo(
                MissingFieldInfoImpl.builder()
                    .setField(reference)
                    .addReferencedFromContexts(referencedFrom.values())
                    .build()));
    missingMethodsContexts.forEach(
        (reference, referencedFrom) ->
            diagnosticBuilder.addMissingDefinitionInfo(
                MissingMethodInfoImpl.builder()
                    .setMethod(reference)
                    .addReferencedFromContexts(referencedFrom.values())
                    .build()));
    return diagnosticBuilder.build();
  }
}
