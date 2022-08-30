// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.references.ClassReference;

/** API for defining a class rule for an ART profile. */
// TODO(b/237043695): @Keep this when adding a public API for passing ART profiles to the compiler.
public interface ArtProfileClassRuleBuilder {

  ArtProfileClassRuleBuilder setClassReference(ClassReference classReference);
}
