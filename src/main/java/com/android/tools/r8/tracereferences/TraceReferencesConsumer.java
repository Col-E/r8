// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.PackageReference;

/** Consumer interface for recording references */
@KeepForApi
public interface TraceReferencesConsumer {

  /**
   * Interface for asking for the access flags for a traced reference when the definition is present
   */
  @KeepForApi
  interface AccessFlags {
    boolean isStatic();

    boolean isPublic();

    boolean isProtected();

    boolean isPrivate();
  }

  /**
   * Interface for asking for additional class information for a traced class when the definition is
   * found.
   */
  @KeepForApi
  interface ClassAccessFlags extends AccessFlags {
    boolean isInterface();

    boolean isEnum();
  }

  @KeepForApi
  interface FieldAccessFlags extends AccessFlags {}

  @KeepForApi
  interface MethodAccessFlags extends AccessFlags {}

  /** Interface implemented by all references reported */
  @KeepForApi
  interface TracedReference<T, F> {
    /** Returns if the reference does not have a definition in the program traced. */
    boolean isMissingDefinition();

    /** Returns the reference traced. */
    T getReference();

    /** Returns the context from which this was referenced. */
    DefinitionContext getReferencedFromContext();

    /**
     * Returns the access flags for the reference traced. If the definition is not found (<code>
     * isMissingDefinition()</code> returns <code>true</code>) the access flags are not known and
     * this returns <code>null</code>.
     */
    F getAccessFlags();
  }

  @KeepForApi
  interface TracedClass extends TracedReference<ClassReference, ClassAccessFlags> {}

  @KeepForApi
  interface TracedField extends TracedReference<FieldReference, FieldAccessFlags> {}

  @KeepForApi
  interface TracedMethod extends TracedReference<MethodReference, MethodAccessFlags> {}

  /**
   * Callback when class has been traced.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then trace references guaranties to exit with an error.
   *
   * @param tracedClass Traced class
   * @param handler Diagnostics handler for reporting.
   */
  void acceptType(TracedClass tracedClass, DiagnosticsHandler handler);

  /**
   * Callback when class has been traced.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then trace references guaranties to exit with an error.
   *
   * @param tracedField Traced field
   * @param handler Diagnostics handler for reporting.
   */
  void acceptField(TracedField tracedField, DiagnosticsHandler handler);

  /**
   * Callback when class has been traced.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then trace references guaranties to exit with an error.
   *
   * @param tracedMethod Traced method
   * @param handler Diagnostics handler for reporting.
   */
  void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler);

  /**
   * Callback when package which is required for package private access has been traced.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then trace references guaranties to exit with an error.
   *
   * @param pkg Traced package
   * @param handler Diagnostics handler for reporting.
   */
  default void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {}

  /**
   * Tracing has finished. There will be no more calls to any of the <code>acceptXXX</code> methods.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then trace references guaranties to exit with an error.
   *
   * @param handler Diagnostics handler for reporting.
   */
  default void finished(DiagnosticsHandler handler) {}

  static TraceReferencesConsumer emptyConsumer() {
    return ForwardingConsumer.EMPTY_CONSUMER;
  }

  /** Forwarding consumer to delegate to an optional existing consumer. */
  @KeepForApi
  class ForwardingConsumer implements TraceReferencesConsumer {

    private static final TraceReferencesConsumer EMPTY_CONSUMER = new ForwardingConsumer(null);

    private final TraceReferencesConsumer consumer;

    public ForwardingConsumer(TraceReferencesConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptType(tracedClass, handler);
      }
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptField(tracedField, handler);
      }
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptMethod(tracedMethod, handler);
      }
    }

    @Override
    public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptPackage(pkg, handler);
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.finished(handler);
      }
    }
  }
}
