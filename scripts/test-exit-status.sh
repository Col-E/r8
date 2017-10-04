# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

UUID=$1
shift
eval "$@"
echo $? > /data/r8-tests-$UUID/exitStatus
