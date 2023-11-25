#!/usr/bin/env python3
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import gradle
import jdk
import os
import subprocess
import sys
import utils

ASM_VERSION = '9.6'
ASM_JAR = os.path.join(utils.DEPENDENCIES_DIR, 'org', 'ow2', 'asm', 'asm',
                       ASM_VERSION, 'asm-' + ASM_VERSION + '.jar')
ASM_UTIL_JAR = os.path.join(utils.DEPENDENCIES_DIR, 'org', 'ow2', 'asm',
                            'asm-util', ASM_VERSION,
                            'asm-util-' + ASM_VERSION + '.jar')


def run(args):
    cmd = []
    cmd.append(jdk.GetJavaExecutable())
    cp = ":".join([ASM_JAR, ASM_UTIL_JAR])
    print(cp)
    cmd.extend(['-cp', cp])
    cmd.append('org.objectweb.asm.util.ASMifier')
    cmd.extend(args)
    utils.PrintCmd(cmd)
    result = subprocess.check_output(cmd).decode('utf-8')
    print(result)
    return result


def main():
    help = True
    args = []
    for arg in sys.argv[1:]:
        if arg == "--no-debug":
            args.append("-debug")
        elif arg in ("-help", "--help", "-debug"):
            help = True
            break
        else:
            help = False
            args.append(arg)
    if help:
        print("asmifier.py [--no-debug] <classfile>*")
        print(
            "  --no-debug    Don't include local variable information in output."
        )
        return
    try:
        run(args)
    except subprocess.CalledProcessError as e:
        # In case anything relevant was printed to stdout, normally this is already
        # on stderr.
        print(e.output)
        return e.returncode


if __name__ == '__main__':
    sys.exit(main())
