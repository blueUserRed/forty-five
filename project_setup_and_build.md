
## Project Setup

- Clone this repository to your machine
- Open the repository as a project in IntelliJ
- IntelliJ will set up Gradle, create Indices, etc. which will take a while
- open a terminal, cd into the onj directory, and clone the following repository: https://github.com/blueUserRed/Onj.git
- create the following directory: assets/textures/packed
- execute the texturePacker gradle task
- navigate to FortyFive.kt file, in the create function, uncomment the three lines 
    regarding the CardGenerator. After the game was started successfully once, these lines
    can be commented out again.
- start the game

## Project Build

- run the forty_five [dist] gradle task
- create a temporary directory
- navigate to: forty_five/desktop/build/libs
- copy the desktop.jar file to the temporary directory
- copy the contents of the assets directory to the temporary directory
- delete the saves/savefile.onj file
- delete the logging/forty-five.log file
- _keep in mind that these files are generated again when the game
    is started_
- modify the logging/log_config.onj file
  - change the logTarget to the logging/forty-five.log file
  - change the version tag
- the .jar file contains the contents of the assets directory as well, but they can
    be removed because they are not needed and take up a lot of space
  - open the .jar file with a program like 7zip
  - go through the directories in the .jar, compare the names with the contents of the
      assets directory, and delete if they match
- remove unnecessary assets
  - for example assets that are packed into atlases (e.g. textures/game_screen, 
    textures/title_screen)
  - assets used by the CardGenerator (textures/cards)
- zip the temporary directory and upload
