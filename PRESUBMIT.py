# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os import path
from subprocess import check_output, Popen, PIPE, STDOUT

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

def CheckFormatting(input_api, output_api, branch):
  results = []
  for f in input_api.AffectedFiles():
    path = f.LocalPath()
    if not path.endswith('.java'):
      continue
    diff = check_output(
        ['git', 'diff', '--no-prefix', '-U0', branch, '--', path])
    proc = Popen(FMT_CMD, stdin=PIPE, stdout=PIPE, stderr=STDOUT)
    (stdout, stderr) = proc.communicate(input=diff)
    if len(stdout) > 0:
      results.append(output_api.PresubmitError(stdout))
  if len(results) > 0:
    results.append(output_api.PresubmitError(
        """Please fix the formatting by running:

  git diff -U0 $(git cl upstream) | %s -p1 -i

or fix formatting, commit and upload:

  git diff -U0 $(git cl upstream) | %s -p1 -i && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks
  """ % (FMT_CMD, FMT_CMD)))
  return results

def CheckDeterministicDebuggingChanged(input_api, output_api, branch):
  for f in input_api.AffectedFiles():
    path = f.LocalPath()
    if not path.endswith('InternalOptions.java'):
      continue
    diff = check_output(
        ['git', 'diff', '--no-prefix', '-U0', branch, '--', path])
    if 'DETERMINISTIC_DEBUGGING' in diff:
      return [output_api.PresubmitError(diff)]
  return []

def CheckForAddedDisassemble(input_api, output_api):
  results = []
  for (file, line_nr, line) in input_api.RightHandSideLines():
    if file.LocalPath().endswith('.java') and '.disassemble()' in line:
      results.append(
          output_api.PresubmitError(
              'Test call to disassemble\n%s:%s %s' % (file.LocalPath(), line_nr, line)))
  return results

def CheckForCopyRight(input_api, output_api, branch):
  results = []
  for f in input_api.AffectedSourceFiles(None):
    # Check if it is a new file.
    if f.OldContents():
      continue
    contents = f.NewContents()
    if (not contents) or (len(contents) == 0):
      continue
    if not CopyRightInContents(f, contents):
      results.append(
          output_api.PresubmitError('Could not find Copyright in file: %s' % f))
  return results

def CopyRightInContents(f, contents):
  expected = ('#' if f.LocalPath().endswith('.py') else '//') + ' Copyright'
  for content_line in contents:
    if expected in content_line:
      return True
  return False

def CheckChange(input_api, output_api):
  branch = (
      check_output(['git', 'cl', 'upstream'])
          .strip()
          .replace('refs/heads/', ''))
  results = []
  results.extend(CheckDoNotMerge(input_api, output_api))
  results.extend(CheckFormatting(input_api, output_api, branch))
  results.extend(
      CheckDeterministicDebuggingChanged(input_api, output_api, branch))
  results.extend(CheckForAddedDisassemble(input_api, output_api))
  results.extend(CheckForCopyRight(input_api, output_api, branch))
  return results

def CheckChangeOnCommit(input_api, output_api):
  return CheckChange(input_api, output_api)

def CheckChangeOnUpload(input_api, output_api):
  return CheckChange(input_api, output_api)
