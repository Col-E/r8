# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

def CheckDoNotMerge(input_api, output_api):
  for l in input_api.change.FullDescriptionText().splitlines():
    if l.lower().startswith('do not merge'):
      msg = 'Your cl contains: \'Do not merge\' - this will break WIP bots'
      return [output_api.PresubmitPromptWarning(msg, [])]
  return []

def CheckChangeOnUpload(input_api, output_api):
  results = []
  results.extend(CheckDoNotMerge(input_api, output_api))
  return results
