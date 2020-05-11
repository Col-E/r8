// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.utils.Timing;
import java.util.function.Predicate;

/**
 * One that assumes. Inherited tracker/optimization insert necessary variants of {@link Assume}.
 */
public interface Assumer {

  void insertAssumeInstructions(IRCode code, Timing timing);

  void insertAssumeInstructionsInBlocks(
      IRCode code,
      BasicBlockIterator blockIterator,
      Predicate<BasicBlock> blockTester,
      Timing timing);
}
