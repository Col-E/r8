#!/bin/bash
#
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

zipinfo $1 | grep 'classes.*\.dex' | awk '{printf "%s%s",sep,$4; sep="+"} END{print ""}' | bc
