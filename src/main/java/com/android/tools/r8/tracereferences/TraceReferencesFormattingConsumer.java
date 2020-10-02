// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.PackageReference;

class TraceReferencesFormattingConsumer implements TraceReferencesConsumer {

  public enum OutputFormat {
    /** Format used with the -printusage flag */
    PRINTUSAGE,
    /** Keep rules keeping each of the traced references */
    KEEP_RULES,
    /**
     * Keep rules with <code>allowobfuscation</code> modifier keeping each of the traced references
     */
    KEEP_RULES_WITH_ALLOWOBFUSCATION
  }

  private final OutputFormat format;
  private final TraceReferencesResult.Builder builder = TraceReferencesResult.builder();
  private boolean finishedCalled = false;

  public TraceReferencesFormattingConsumer(OutputFormat format) {
    this.format = format;
  }

  @Override
  public void acceptType(TracedClass type) {
    assert !finishedCalled;
    builder.acceptType(type);
  }

  @Override
  public void acceptField(TracedField field) {
    assert !finishedCalled;
    builder.acceptField(field);
  }

  @Override
  public void acceptMethod(TracedMethod method) {
    assert !finishedCalled;
    builder.acceptMethod(method);
  }

  @Override
  public void acceptPackage(PackageReference pkg) {
    assert !finishedCalled;
    builder.acceptPackage(pkg);
  }

  @Override
  public void finished() {
    assert !finishedCalled;
    finishedCalled = true;
  }

  public String get() {
    TraceReferencesResult result = builder.build();
    Formatter formatter;
    switch (format) {
      case PRINTUSAGE:
        formatter = new PrintUsesFormatter();
        break;
      case KEEP_RULES:
        formatter = new KeepRuleFormatter(false);
        break;
      case KEEP_RULES_WITH_ALLOWOBFUSCATION:
        formatter = new KeepRuleFormatter(true);
        break;
      default:
        throw new Unreachable();
    }
    formatter.format(result);
    return formatter.get();
  }
}
