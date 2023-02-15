
## Code style
 - the kotlin style guide should be followed
 - when in doubt, use the auto formatter
 - When writing identifiers, comments, etc. english should be used
 - files and directories in the assets directory should use snake_case 

## Git
 - when tackling a feature, create a branch with the prefix 'f_' and develop there
 - for small bugfixes, you can use your personal brach with the 'dev_' prefix
 - branch names should use snake_case
 - commit messages should be meaningful, contain the number of the bug fixed when
   appropriate, and be written in english
 - you should commit when:
   - some in itself complete part of the feature is finished
   - you made sure the project compiles and runs (when that is not the case and you,
     for whatever reason commit anyway, specify that in the commit message)
   - when in doubt, it's better to commit too often than to rarely
 - before a merge request to main can be merged, at least one other developer needs to
   review it and approve it
 - when reviewing:
   - check if the style is consistently bad
   - try to understand the logic
   - evaluate if there is an easier way to do things
   - When you don't understand something, or are unsure of some code, ask the developer
   - When changes are necessary, reject the merge request and write a summary of the
     required changes
