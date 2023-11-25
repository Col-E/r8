// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

/** API for defining a method rule for an ART profile. */
@KeepForApi
public interface ArtProfileMethodRuleBuilder {

  ArtProfileMethodRuleBuilder setMethodReference(MethodReference methodReference);

  ArtProfileMethodRuleBuilder setMethodRuleInfo(
      Consumer<ArtProfileMethodRuleInfoBuilder> methodRuleInfoBuilderConsumer);
}
