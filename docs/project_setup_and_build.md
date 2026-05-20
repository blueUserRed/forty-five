
> If you have any issues, questions or need help, you can join our discord:
> https://discord.gg/2caBPyXK9B

## Project Setup

- execute the following code on your machine
  ```shell
  git clone https://github.com/blueUserRed/forty-five.git
  cd ./forty-five
  cd ./onj
  git clone https://github.com/blueUserRed/Onj.git
- copy the contents of the [share](https://drive.google.com/drive/folders/1Iy0t3AdodBcZgzSG0qMmR7aoWCS9SOT6?usp=sharing) into ``assets/blobs``.
  You may need to create the `blobs` directory. The paths should look like this: (`assets/blobs/animations/`, `àssets/blobs/cards`, etc.)
- Open the repository as a project in [Intellij](https://www.jetbrains.com/idea/download/) (scroll down for Community Version, it is free and has everything you need)
- IntelliJ will set up Gradle, create indices, etc. which will take a while (a few minutes)
- Depending on where you copied the assets from, you may need to generate the drop shadows
  - In Intellij, in the right sidebar, open gradle (the elephant)
  - Go to forty-five => desktop => Tasks => bake => createDropShadows
  - Double click "createDropShadows"
- start the game
  - In Intellij, in the right sidebar, open gradle (the elephant)
  - Go to forty-five => desktop => Tasks => development => run
  - Double click "run"

## Install Onj Plugin

> [!NOTE]
> This step is entirely optional and not needed for running or building the project

Forty-Five makes use of a custom markup/configuration language called [onj](https://github.com/blueUserRed/Onj).
To support syntax highlighting and basic error checking and refactorings we created a simple plugin
for Intellij. This plugin is not necessary, but it makes working with onj files somewhat easier. 
This is not an officially released plugin and has a lot of "quick and dirty" hacks, but it works.

To install:
- download the folder with the highest semantic version from the [share](https://drive.google.com/drive/folders/1tHX0UaqP9MQ53ThTZuBPx7OdHr3ptvKl?usp=sharing)
- unzip the zip folder
- the relevant jar file is ``Onj_Plugin/lib/Onj_Plugin-x.x.x.jar``
- in Intellij, open the plugins tab in the settings
- in the top middle of the window, click the settings icon and select "install plugin from disk"
- select the jar file

## Project Build

> [!NOTE]
> The build steps are _not_ necessary when you just try to run the game. They describe how to
> create a finished build that can be easily shared with others

### Project Build (automatically using build script)

Because the build process is quite lengthy and annoying, I wrote a script to automate it. I wrote it 
mainly for myself, meaning it might take some time and changes to get it working on other machines.
The script uses kotlin scripting (.kts) and running it requires the kotlin compiler (kotlinc) to be installed.
Also, the script requires 7zip and launch4j to be installed and available in the path
(try running ``7z`` and ``launch4jc`` in the console).

Use this command in the root directory of the project to run the script:
````shell
kontlinc scripts/build.main.kts
````

### Project Build (to .jar)

- run the forty_five [dist] gradle task
- create a temporary directory
- navigate to: forty_five/desktop/build/libs
- copy the desktop.jar file to the temporary directory
- copy the contents of the assets directory to the temporary directory
- delete the profiles is assets/profiles
- delete the assets/profiles/global_save.onj file
- delete the logging/forty-five.log file
- _keep in mind that these files are generated again when the game
    is started_
- modify the logging/log_config.onj file
  - change the version tag
  - for development versions, the version tag is usually "b" followed by the timestamp
    in the format "yymmdd"
- the .jar file contains the contents of the assets directory as well, but they can
    be removed because they are not needed and take up a lot of space
  - open the .jar file with a program like 7zip
  - go through the directories in the .jar, compare the names with the contents of the
      assets directory, and delete if they match
- zip the temporary directory
- rename it to "forty-five-" followed by the version tag
- upload

### Project Build (to .exe)
- follow the previous instructions to create a jar file.
- Download launch4j
- Create a stripped down jre for the game using the following command:
- Copy the jre from the ``assets/blobs`` directory in the temporary directory
- If you want/need to create your own jre (f.e. because you upgraded the java version) see the section below
- open the launch4j GUI
- set the path to the .jar file and the output path
- Optionally, configure a path to an icon
- Under the JRE-Tab, set "JRE paths" to "./jre"
- click the build button
- delete the jar
- zip and upload

### Create jre

````powershell
jlink `
  --strip-debug `
  --no-man-pages `
  --no-header-files `
  --output /jre `
  --add-modules java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.prefs,java.xml,jdk.unsupported
````
- The needed modules might change when new functionality is added. The ``jdeps`` utility that is packed with the jdk
  can be used to analyze the dependencies of an executable jar.
