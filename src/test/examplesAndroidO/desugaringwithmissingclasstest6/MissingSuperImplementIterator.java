// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaringwithmissingclasstest6;

import desugaringwithmissingclasslib3.C;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class MissingSuperImplementIterator extends C implements Iterator {

  @Override
  public boolean hasNext() {
    return false;
  }

  @Override
  public Object next() {
    throw new NoSuchElementException();
  }
}
