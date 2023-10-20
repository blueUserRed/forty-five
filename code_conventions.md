
## Code style
 - the kotlin style guide should be followed
 - when in doubt, use the auto formatter
 - When writing identifiers, comments, etc. english should be used
 - files and directories in the assets directory should use snake_case
 - avoid using ``try/catch/throw`` as Control flow. It is
   usually better to either make the return-type of a function that can fail nullable, or
   in cases where an error message is needed, use the Either-type. When writing a
   ``throw`` statement, it should generally be done with the intention of crashing the
   program.
 - avoid Warnings where possible and check quick fixes before committing. When leaving
   a warning in the code purposefully, suppress it and leave a comment if necessary.
 - Don't expose a mutable collection if you don't intend it to be changed. (Same thing
   applies to setters)
 - Calling a kotlin getter/setter should always be a cheap operation. If a getter/setter
   performs expensive operations, it should be changed to a function or the result should
   be cached after the first call.
 - Guard clauses > nesting. It is usually more readable to ``throw/return/continue`` early
   than to add another indentation layer.

## Application specific
 - use the ``@MainThreadOnly`` and the ``@AllThreadsAllowed`` annotations to indicate
   whether a function / constructor / lambda has to be called from the main thread.
   Functions that use the ``@AllThreadAllowed`` annotation should not call functions 
   marked with ``@MainThreadOnly`` or perform actions that require the openGl context.
 - Don't load assets (textures, particles, shaders, etc) manually. Use the ResourceManager
   instead. This prevents accidental memory leaks.

## Git
 - when working on a user story, the branch name should be "ff-xxx-name-of-user-story"
   where xxx is the number of the user story
 - merge request related to a user story should start with "[ff-xxx]"
 - commit messages should be meaningful, contain the number of the bug fixed when
   appropriate, and be written in english
 - you should commit when:
   - some in itself complete part of the feature is finished
   - you made sure the project compiles and runs (when that is not the case and you,
     for whatever reason, commit anyway, specify that in the commit message)
   - when in doubt, it's better to commit too often than to rarely
 - before a merge request to main can be merged, at least one other developer needs to
   review it and approve it
 - when reviewing:
   - check if the style is consistently bad
   - check if these conventions are consistently broken
   - try to understand the logic
   - evaluate if there is an easier way to do things
   - When you don't understand something, or are unsure of some code, ask the developer
   - When changes are necessary, reject the merge request and write a summary of the
     required changes
