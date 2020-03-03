# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os import path
from subprocess import Popen, PIPE, STDOUT

FMT_CMD = path.join(
    'third_party',
    'google-java-format',
    'google-java-format-google-java-format-1.7',
    'scripts',
    'google-java-format-diff.py')

def CheckDoNotMerge(input_api, output_api):
  for l in input_api.change.FullDescriptionText().splitlines():
    if l.lower().startswith('do not merge'):
      msg = 'Your cl contains: \'Do not merge\' - this will break WIP bots'
      return [output_api.PresubmitPromptWarning(msg, [])]
  return []

def CheckFormatting(input_api, output_api):
  results = []
  for f in input_api.AffectedFiles():
    path = f.LocalPath()
    if not path.endswith('.java'):
      continue
    proc = Popen(FMT_CMD, stdin=PIPE, stdout=PIPE, stderr=STDOUT)
    (stdout, stderr) = proc.communicate(input=f.GenerateScmDiff())
    if len(stdout) > 0:
      results.append(output_api.PresubmitError(stdout))
  if len(results) > 0:
    results.append(output_api.PresubmitError(
        """Please fix the formatting by running:

  git diff -U0 $(git cl upstream) | %s -p1 -i

or bypass the checks with:

  cl upload --bypass-hooks
  """ % FMT_CMD))
  return results

def CheckChange(input_api, output_api):
  results = []
  results.extend(CheckFormatting(input_api, output_api))
  results.extend(CheckDoNotMerge(input_api, output_api))
  return results

def CheckChangeOnCommit(input_api, output_api):
  return CheckChange(input_api, output_api)

def CheckChangeOnUpload(input_api, output_api):
  return CheckChange(input_api, output_api)
