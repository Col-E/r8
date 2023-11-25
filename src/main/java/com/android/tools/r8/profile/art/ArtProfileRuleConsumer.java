// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;

/**
 * API for consuming rules from an ART profile. The compiler will call the methods {@link
 * #acceptClassRule} and {@link #acceptMethodRule} for each class and method rule (respectively) in
 * the profile.
 */
@KeepForApi
public interface ArtProfileRuleConsumer {

  /** Provides information about a specific class rule to the consumer. */
  void acceptClassRule(ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo);

  /** Provides information about a specific method rule to the consumer. */
  void acceptMethodRule(MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo);
}
