
# .forty-five Technical design

### important links:
- LibGdx wiki: https://libgdx.com/wiki/
- YogaLayout Playground https://yogalayout.com/playground/
- Onj repository (including readme): https://github.com/blueUserRed/Onj

### Content

## Creating and styling screens

----

### ScreenBuilder

The ScreenBuilder is an important part of the application. It is used
for converting an .onj file representing a screen into an actual screen
that can be used in LibGdx. The .onj file is validated against
onjschemas/screen.onjschema. The returned screen supports a bunch of
useful features out of the box, e.g. styling or navigating elements
using the keyboard.

### Custom Actors

Components on the screen are referred to as Actor or Widget. The 
Widget class extends the Actor class and adds support for features
related to layouting. Generally, while most functions take an Actor,
most components used by game are Widgets. For almost every Widget that
LibGdx provides by default (e.g. Label, Image) a custom wrapper was 
created that adds features like styling. (-> CustomActors.kt)

### Custom z-index implementation

Because of issues with LibGdx implementation of z-indices a custom
one was created. (-> ZIndexActor/ZIndexGroup interfaces). When you
want to change the z-index of an Actor you should always use the
`fixedZIndex` field instead of the 'zIndex' field. Whenever a z-index
is changed, it is necessary to resort the actors using the
`resortZIndices` function of the parent.

> Important:
> The z-index of the parent takes precedence over the z-index of the
> child. Consider the following structure: <br>
> ``|-A zIndex: 1``<br>
> ``--|-A1 zIndex: 5``<br>
> ``--|-A2 zIndex: 1000``<br>
> ``|-B zIndex: 2``<br>
> In this case B would overlap A2 and A1, because the z-index of A is
> smaller than the z-index of B

### FlexBoxes

For layouting FlexBoxes are used. These work very similarly to the
ones you know from web development. The technology was standardized
by Facebook (Meta?) and is known as [YogaLayout](https://yogalayout.com/).
Ports to both Java and LibGdx are available and used in this project.

### Styling

_relevent Namespace for onj-files: Style_

To allow for the easy and dynamic styling of the screen, a custom 
styling system was created. Its features include assigning
properties of Actors, animating certain properties and conditional
styling (for example applying a style when the user hovers over an
actor).

In order for an Actor to be styled using this system, it must
implement the StyledActor interface. This interface includes a 
`styleManager` property, that is typically initialized to `null` and
set to its proper value by the ScreenBuilder. The StyleManager is 
responsible for keeping track of all properties of an actor and 
changing them when necessary. Additionally, the StyledActor interface
has an `initStyles` function, that allows the actor to add its
properties to the manager. This is usually done using an extension
function like `addBackgroundStyles` or `addFlexBoxStyles`. For the 
complete definitions of all style properties view StyleProperties.kt.

Example of the styling system being used in map_screen.onj:

```json5
$Image {
    // The styles key is an array of multiple style objects
    styles: [
        {
            
            // a style object can assign properties of an actor and
            // define meta-keys for that object (prefixed with style_)
            
            style_priority: 1, // sets the priority for all properties
                               // in that object. The priority decides
                               // which value to apply when multiple
                               // instructions are present
            background: "map_detail_fight_button" // sets the background texture
        },
        {
            style_priority: 2,
            style_condition: hover(), // All styles in this object 
                                      // are only applied when this
                                      // condition is met. hover()
                                      // is true when this actor is
                                      // hovered over
            background: "map_detail_fight_button_hover",
        }
    ],
    name: "start_button",
    reportDimensionsWithScaling: true,
    scaleX: 0.06,
    scaleY: 0.06,
}
```

The above example sets a different background image for a button
whenever it is hovered over. In the normal case, where `hover()` is 
false, the second `backround` is ignored. However, when the users
hovers over the image, the second `backgound` styles overrides the
first one because of its higher priority, changing the texture.

Whenever you need kotlin code to interact with the styling of a screen,
the screen-state system can be used. The OnjScreen class provides
the `enterState` and `leaveState` functions used for controlling the
states of the screen. In your .onj-file, the screen-state can be
queried using the state() function.

Example from map_screen.onj:
```json5
{
    style_priority: 3,
    style_condition: state("displayEventDetail") and not(state("transition away")),
    style_animation: {
        duration: 0.2,
        interpolation: interpolation.linear
    },
    positionLeft: 0.0#points,
},
```

For combining multiple style-conditions, the and/or/not functions
can be used.

> Note: <br>
> Properties like width, height, positionLeft, etc. don't take float,
> but a YogaValue instead. to create a YogaValue in onj, the `#points`
> or `#percent` conversion functions can be used. Most other custom types
> provided by YogaLayout (like YogaFlexDirection) can be accessed using
> global Variables.

### Behaviors

_relevant file: Behaviours.kt_

Behaviours can be used to add more complex behaviours to an Actor.
Most of these have been made obsolete by the style system and once
a better event-handling system is in place, even more will follow.
Currently, behaviours are the only way to change the cursor on hover.

### ScreenController

Sometimes more complicated logic is necessary for a screen. In these
cases, a ScreenController can be used. It provides the `init`, `update`
and `end` functions, that allow the controller to listen to the lifecycle of
a screen and manipulate it.

### Render Pipelines

Using the `useRenderPipeline` function defined in the `FortyFive` object, the
render pipeline can be changed to a custom one. Render Pipelines are useful for
creating various post-processing effects. When the screen is changed, the render
pipeline is reset.

## ResourceManager

----

The ResourceManager is used for keeping track of assets and
loading/disposing them accordingly.

### Some words on assets

LibGdx doesn't rely on the GC of the JVM for collecting unused assets.
Doing so could lead to expensive GCs, especially on Android, which in
turn could lead to issues with latency and framerate. Additionally, the
programmer would run the risk of missing a reference to some heavy object
keeping potential gigabytes worth of unused assets loaded. Instead, all
assets are managed manually in LibGdx. That means, when an object extends
the Disposable interface, the `dispose` function needs to be called at the
end of its lifecycle. If that isn't done, your program will leak memory.
Aside from memory leaks, most of the other fun bugs possible with pointers
are possible here as well, for example use-after-free. These will generally
not cause an exception, but result in undefined behaviour.

To eliminate these sources of bugs, the ResourceManager was created.

### Defining what assets exist

The ResourceManager reads the config/assets.onj file, which defines
all existing assets, their name, their type and where in the file-structure
they lie. If you want to use a new asset in the program, you must make
an entry in this file.

### Using the ResourceManager

In order to use an asset, you must borrow it first. The `borrow` function
expects a ResourceBorrower instance as a parameter. This interface doesn't
actually provide any functionality, instead it signals to the programmer that
a given object can hold assets and hopefully reminds them to give the assets
back when they're done. Assets are identified using a ResourceHandle, which is
just a string. In order to signal that a string is used for identifying
resources the ResourceHandle typealias can be used.

> Note: <br>
> The ResourceManager guarantees that a Resource is loaded after `get` was called
> the first time and that the reference will stay valid until `giveBack` is called.
> However, beyond that, no guarantees about the state of resources are made.

Example class that can borrow resources:
```kotlin

class SomeClass(
    private val textureHandle: String
) : SomeOtherClass(), ResourceBorrower {
    
    fun init() {
        // The ResourceManager is an object (=Singleton) an always available.
        ResourceManager.borrow(this, textureHandle)
    }
    
    fun update() {
        // The generic parameter declares the type the ResourceManager will 
        // be looking for. If no asset with the identifier exists or the type
        // doesn't match, an exception will be thrown.
        val texture = ResourceManager.get<Drawable>(this, textureHandle)
        /*
        ... Do something with texture
        */
    }
    
    fun end() {
        ResourceManager.giveBack(this, textureHandle)
    }
    
}
```

### The inner workings of the ResourceManager

The ResourceManager uses an approach similar to reference counting for keeping
of how often a resource is borrowed. That means a resource will never be loaded
twice and that it will stay as long as some part of the application needs it.
The borrow function indicates to the ResourceManager that a resource will be used
soon, but that doesn't mean that it will be loaded immediately.

When the screen of the application changes, the `changeScreen` function of the
`FortyFive` object will send a message to the ServiceThread that tells it to
start loading resources. This is done to avoid interacting a lot with
file-system from the main thread and to keep the game running smoothly.
This also means that it is generally a good idea to wait as long as possible
before calling the `get` function, in order to give the ServiceThread a chance
at loading asynchronously.

## The encounter (core gameplay)

----

### The GameController

The GameController is a [ScreenController](#screencontroller). Together with
the GameState class it is responsible for managing the flow of the encounter.
The GameController manages and changes the actors on the screen and provides
functions like `shoot` that can be called from other parts of the application.
The GameState class is used to keep track of the current phase of the game
(a phase is for example Free (the main game phase) or EnemyAction).

### GameAnimations

Most animations can be implemented using the LibGdx
[actions](https://libgdx.com/wiki/graphics/2d/scene2d/scene2d). However,
actions are ideal when you want to animate a single actor, for example
when moving some text around. Some animations in the game (for example
the player/enemy turn banner) don't really fit this use case. For that
reason the GameAnimation class exists. It doesn't do much on its own
and leaves a lot of freedom to the programmer, but still restricts all
animations to a common interface, so they can all be controlled by the
GameController.

### Cards

_relevant namespace for onj-files: Card_

The cards are managed by cards/cards.onj file. It records the name of the
card, how much damage it does, how much it costs and description/flavour
texts. In addition, it defines the start deck and what card to draw when
the deck goes empty. The textures of the cards do not include the statistics
(cost, damage) in order to make balancing changes quicker. Instead, the
[TextureGenerator](#the-texturegenerator) is used to write the text onto
the card and pack them in an atlas. This generation process can be toggled
on/off in the FortyFive object.

The GameController uses the cards.onj file to create a CardPrototype for each
type of card. These prototypes are then stored and can be used to create a new
instance of a card at any time.

### Effects

Cards can have effects that manipulate the gameplay in an interesting way.
Each effect needs a trigger that tells it when the effect should be activated.

example 1 (fake bullet):
```json5
effects: [
    // effects are declared using functions defined in the Card namespace
    reserveGain("shot", 4),
    reserveGain("destroy", 4)
    // the fake bullet declares the reserveGain twice with a different trigger,
    // in order to cover all cases in which it leaves the revolver
]
```

example 2 (bullet bullet bullet):
```json5
effects: [
    buffDmg("enter", bSelects.allBullets, 10)
]
```

The bSelect tells the effect which bullets to buff when it is triggered.
The different bSelects are defined at the top of the file in a variable:

```json5
var bSelects = {
    allExceptSelf: bNum([1, 2, 3, 4, 5]),
    allBullets: bSelectByName("bullet"),
    fourButNotSelf: bNum([4])
};
```

The bNum function takes an array of numbers that represent the slots that
should be selected. By default, the bNum function will never select the
bullet triggering the effect, even when its slot is in the array. By adding
the string `'this'` to the array, the effect will always select the triggering
bullet. The bSelectByName selects all bullets that have the given name.

### Trait effects

Trait effects are a special kind of effects, that are different because
they do not need a trigger. They usually describe some property of a card.
Examples are the rotten effect or the left-turning effect.

### Status effects

Bullets cannot have status effects, only the enemy (and in the future
the player) can. However, a bullet can have an effect that gives the enemy
a status effect. Status effects are only active for a given number of 
revolver-rotations, after which the status effect disappears.

## The SaveState

----

The savefile (saves/savefile.onj) stores information that persists for the
entire run. That includes: the collected cards, the health of the player,
some statistics and the current position of the map. After The player dies
or the run ends, the savefile is replaced with the default savefile
(saves/default_savefile.onj). This file is also used if the savefile is not
found or corrupt.

## Utility classes

----

### The TextureGenerator

The TextureGenerator takes a config file and generates the textures specified
by it. Its abilities include drawing text, other textures or simple shapes onto
a preexisting texture. The results are saved as an
[atlas](https://libgdx.com/wiki/tools/texture-packer).

### TemplateString

TemplateStrings can be used to interpolate a string with global values. For
example, this can be done using the TemplateLabel Widget.

Example from loose_screen.onj:
```json5
$TemplateLabel {
    properties: [
        position("absolute"),
        position(0.0, null, 36.0, null)
    ],
    align: "left",
    template: "enemies killed: {stat.lastRun.enemiesDefeated}\nreserves used: {stat.lastRun.usedReserves}",
    font: "red_wing",
    color: color.black,
    fontScale: 0.2
}
```

A TemplateString can also be created in code, using its constructor. The 
constructor takes a map that contains an additional set of parameters to be
interpolated independent of the global ones.

```kotlin
val templateString = TemplateString(
    "{param1}, {param2}",
    mapOf(
        "param1" to 35,
        "param2" to true
    )
)
val interpolatedString = templateString.string
```

There are two ways for binding global parameters:

Using the `updateGlobalParam` function.
Example form DetailMapWidget.kt:

```kotlin
private fun setupMapEvent(event: MapEvent?) {
    event ?: return
    TemplateString.updateGlobalParam("map.curEvent.displayName", event.displayName)
    TemplateString.updateGlobalParam("map.curEvent.description", event.descriptionText)
}
```

Using the `templateParam` property delegate.
When you want to mirror the value of a kotlin property to a global template
parameter, the easiest way is to delegate it.

Example:
```kotlin
// the second parameter is the initial value
var lastRunEnemiesDefeated: Int by templateParam("stat.lastRun.enemiesDefeated", 0)
```

If multiple parameters depend on one property, the `multipleTemplateParam` 
propertey delegate can be used. It works like `templateParam`, but takes
additional parameters of type `Pair<String, (T) -> Any?>`. The first value
is the name of the dependent property, the second one is a lambda that
transforms the value.

Example:
```kotlin
private var remainingCardsToDraw: Int? by multipleTemplateParam(
    "game.remainingCardsToDraw", null,
    "game.remainingCardsToDrawPluralS" to { if (it == 1) "" else "s" }
)
```

### Timeline

Timeline is a utility class used for timing things like animations. An instance
of Timeline is usually created using the `Timeline.timeline` function. This 
functions takes a lambda that constructs the timeline.

Example:

```kotlin
val timeline = Timeline.timeline {
    val animation = getSomeAnimation()
    
    // adds a new action to the timeline
    action { animation.start() }
    
    // this delays all further actions until a condition is true
    delayUntil { animation.isFinished }
    
    // Be careful:
    // this will print immediately anyway, because it is not wrapped in
    // an action!
    println("hi")
    
    // This print will be delayed
    action { println("hi") }
    
    // includes the actions of a different timeline in this timeline
    include(otherTimeline)
    
    // delays the timeline for 500ms
    delay(500)
    
    // includes some other timeline based on condition, but defers the execution
    // of both the condition and the timeline construction until this point in
    // the timeline is reached. This can be useful if the condition
    // or the timeline to be included is dependent on previous actions. 
    includeLater(
        { getSomeTimeline() },
        { someCondition }
    )
    
}
```

### The TexturePacker

LibGdx recommends packing textures into
[atlases](https://libgdx.com/wiki/tools/texture-packer), in order to
increase performance. To automate this process, a gradle task was written,
which reads the toPack.txt file that specifies which directories should be
packed. The finished atlases are put into the textures/packed directory.

### BetterShader

While GLSL provides a preprocessor out of the box, it is not very powerful.
At fist glance it might look like the C preprocessor, but important instructions
like `#include` are not supportet. To make writing shaders easier, a custom
preprocessor was written.
It has the following features:
 - sections: Sections allow you to separate the vertex shader from the fragment
   shader. This allows you to write a shader in just one file. Additionally, an
   export section can be declared which is used when the file is imported.
 - include: Includes a file by dumping its source code into the including file <br>
   Example: ``%include shaders/includes/noise_utils.glsl``
 - uniforms: You can declare special uniforms which are bound automatically by the
   BetterShader class. <br>
   Example: ``%uniform u_time``
 - constArgs: You can declare a constArg with a type in a shader. The value this
   argument takes on is always the same and can be defined in assets.onj. <br>
   Example: ``%constArg ca_speed float``

## Maps

----

### DetailMap

A DetailMap is the type of map that the player can navigate across. It
consists of multiple nodes, which are connected by edges. The Widget used
to display this map is the DetailMapWidget. In addition, DetailMaps can 
define decorations. These are textures that are rendered on the map, that
don't affect the gameplay.

### MapEvents

A node on a DetailMap can define an event. Events can provide a name and a 
description, which are displayed in the sidebar. An event can choose not to
show the sidebar. Events can also define a function which is executed when
the user attempts to start an event.

### Areas and Roads

DetailMaps are separated into two categories: areas and roads. Areas are
defined statically and always persist over multiple runs, for the entire
playthrough. Roads are generated dynamically, and are reset as soon as a
run is over. Roads can be found in the maps/roads directory. The static
definitions for areas can be found in maps/area_definitions, the current
version of the map (where the player might have already completed events)
is stored in maps/areas.

### WorldView

The WorldView is a static image that shows the entire structure made up
of areas and roads to the player. To make the process of changing location
signs easier, the WorldView is drawn by the
[TextureGenerator](#the-texturegenerator). Additionally, a player icon is
displayed on the part of the map where the player is currently at. The
locations of the player icon can be configured in maps/map_config.onj.

### The MapGenerator

TODO
