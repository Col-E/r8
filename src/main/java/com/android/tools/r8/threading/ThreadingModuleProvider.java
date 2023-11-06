// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;

/**
 * Interface to obtain a threading module.
 *
 * <p>The provider is loaded via reflection so its interface must be kept.
 */
@UsedByReflection(
    kind = KeepItemKind.CLASS_AND_MEMBERS,
    memberAccess = {MemberAccessFlags.PUBLIC})
public interface ThreadingModuleProvider {

  ThreadingModule create();
}
