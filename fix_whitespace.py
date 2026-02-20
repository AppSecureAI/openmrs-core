#!/usr/bin/env python3
with open('api/src/main/java/org/openmrs/util/databasechange/BooleanConceptChangeSet.java', 'r') as f:
    lines = f.readlines()

# Add three tabs to blank lines 290, 297, and 302 (0-indexed: 289, 296, 301)
for line_num in [289, 296, 301]:
    if lines[line_num].strip() == '':
        lines[line_num] = '\t\t\t\n'

with open('api/src/main/java/org/openmrs/util/databasechange/BooleanConceptChangeSet.java', 'w') as f:
    f.writelines(lines)

print("Fixed trailing whitespace on lines 290, 297, and 302")
