
> If you have any issues, questions or need help, you can join our discord:
> https://discord.gg/2caBPyXK9B

## Project Setup

- Clone this repository to your machine
- Open the repository as a project in IntelliJ
- IntelliJ will set up Gradle, create Indices, etc. which will take a while
- open a terminal, cd into the onj directory, and clone the following repository: https://github.com/blueUserRed/Onj.git
- create the ``assets/textures/packed`` directory
- execute the texturePacker gradle task
- download the current release of .Forty-Five and copy the contents of the ``large_assets`` directory to `assets/large_assets`
- start the game

## Project Build (to .jar)

- run the forty_five [dist] gradle task
- create a temporary directory
- navigate to: forty_five/desktop/build/libs
- copy the desktop.jar file to the temporary directory
- copy the contents of the assets directory to the temporary directory
- delete the saves/savefile.onj file
- delete the saves/perma_savefile.onj file
- delete the logging/forty-five.log file
- _keep in mind that these files are generated again when the game
    is started_
- modify the logging/log_config.onj file
  - change the version tag
  - for development versions, the version tag is usually "b" followed by the timestamp
    in the format "yymmdd"
  - for release version (i.e. versions that are meant to be played by non-team-members) use
    semantic versioning
- the .jar file contains the contents of the assets directory as well, but they can
    be removed because they are not needed and take up a lot of space
  - open the .jar file with a program like 7zip
  - go through the directories in the .jar, compare the names with the contents of the
      assets directory, and delete if they match
- remove unnecessary assets
  - for example assets that are packed into atlases (e.g. textures/game_screen, 
    textures/title_screen)
- zip the temporary directory
- rename it to "forty-five-" followed by the version tag
- upload

## Project Build (to .exe)
- follow the previous instructions to create a jar file.
- Download launch4j
- Create a stripped down jre for the game using the following command:
````powershell
jlink `
  --strip-debug `
  --no-man-pages `
  --no-header-files `
  --output /jre `
  --add-modules java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.prefs,java.xml,jdk.incubator.foreign,jdk.unsupported
````
- The needed modules might change when new functionality is added. The ``jdeps`` utility that is packed with the jdk
  can be used to analyze the dependencies of an executable jar.
- Copy the created jre to a folder named ``jre`` in the same directory as the jar.
- open the launch4j GUI
- set the path to the .jar file and the output path
- Optionally, configure a path to an icon
- Under the JRE-Tab, set "JRE paths" to "./jre"
- click the build button
- delete the jar
- zip and upload
