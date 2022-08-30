// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

/** API for defining a method rule for an ART profile. */
// TODO(b/237043695): @Keep this when adding a public API for passing ART profiles to the compiler.
public interface ArtProfileMethodRuleBuilder {

  ArtProfileMethodRuleBuilder setMethodReference(MethodReference methodReference);

  ArtProfileMethodRuleBuilder setMethodRuleInfo(
      Consumer<ArtProfileMethodRuleInfoBuilder> methodRuleInfoBuilderConsumer);
}
