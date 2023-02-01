![logo](./assets/textures/title_screen/logo.png)

# .fourty-five Technisches Design

------

## Aufbauen und Stylen von Screens

------

_relevantes package: screen.general_

### ScreenBuilder

_relevanter Namespace für onj-Dateien: Screen_

Der ScreenBuilder ist ein zentrales Element der Applikation. Er nimmt eine onj-Datei
und baut aus ihr einen verwendbaren Screen (siehe OnjScreen bzw. StyleableOnjScreen
Klasse). Der Konstruktor von ScreenBuilder nimmt ein FileHandle das zu der .onj Datei
zeigt und stellt die .build() funktion zu Verfügung, die den Screen tatsächlich baut.
OnjDateien werden gegen folgendes Schema validiert: onjschemas/screen2.onjschema.

### Custom Actors
Komponenten auf einem Screen werden Actor oder Widget genannt. Widget extended die 
Actor Klasse und hat zusätzliche Funktion für Layouting, z.B. die
.invalidate() funktion, die das Layout des Widgets als ungültig markiert. Für Elemente
wie Labels, Images, etc. stellt Libgdx bereits Widgets zur Verfügung, da diese aber 
nicht alle gebrauchten Features supporten, gibt es für praktische jedes Libgdx Widget 
einen eigenen Wrapper, der normalerweise in der CustomActors.kt Datei zu finden ist.
 

### Eigene Z-Index Implementation
Da die Libgdx Implementation von Z-Indexen unintuitiv ist / nicht immer gut funktioniert,
wurde eine eigene programmiert (siehe ZIndexActor/ZIndexGroup Interface in der 
CustomActors.kt Datei). Das heißt, wenn ein Z-Index zugewiesen werden soll, unbedingt
das .fixedZIndex field verwenden, nicht das .zIndex field! Wenn ein ZIndex geändert wird,
ist es notwendig die .resortZIndices() funktion des parents aufzurufen.

> wichtig: <br>
> Der Z-Index des Parents hat Vorrang gegenüber den Z-Index des Childs.
> z.B in der folgenden Struktur: <br>
> ``|-A zIndex: 1``<br>
> ``--|-A1 zIndex: 5``<br>
> ``--|-A2 zIndex: 1000``<br>
> ``|-B zIndex: 2``<br>
> In diesem Fall würde B A1 und A2 überlappen, da der ZIndex von A kleiner als der von
> B ist

### FlexBoxen
Zum Layouten werden FlexBoxen (die sehr ähnlich wie den aus dem Web bekannten
funktionieren) verwendet. Diese wurden von Facebook als
[YogaLayout](https://yogalayout.com/) standardisiert, zu Java und 
anschließend zu Libgdx geportet. Für mehr Infos darüber wie die Properties der FlexBox
geändert werden können, siehe Styling.

Beispiel: 
````json5
root: $Box {
    properties: [
        // Für mehr Information zu Styles siehe Styles
        alignItems("flex end"),
        flexDirection("column"),
        relHeight(100.0)
    ]

// children ist eine infix funktion die im Screen Namespace definiert ist.
// Sie fügt das nachfolgende Array in das Objekt mit dem key 'children' ein
} children [
    $Image {
        textureName: "texture",
        scaleX: 0.07,
        scaleY: 0.07
    }
]
````

### Styling

_relevanter Namespace für onj-Dateien: Style_

Um das dynamischere Designen von Screens zu ermöglichen, wurde ein System für das
Styling von Komponenten entwickelt. Dieses ist aktuell noch relativ limitiert, aber
mächtiger als das von libgdx und kann ausgebaut werden. Zum Beispiel erlaubt das 
System Styles wiederzuverwenden, simple Animationen und Änderungen wenn z.B. gehovert
wird.

Ein Objekt kann auf zwei Arten gestyled werden: entweder direkt über den Properties-Key
oder indem ein vorgefertigter Style zugewiesen wird.

````json5
$Label {
    properties: [ // direkt zugewiesene StyleProperties
        // Die Funktionen für das Erstellen der StyleProperties
        // sind im Style Namespace definiert
        relWidth(100.0) // steht für relative width, angabe in prozenz
    ],
    // styles werden entweder im Assets-key oder in einer extra-Datei definiert
    // alle properties der Styles werden dann für dieses Element angewandt 
    styles: [ "myStyle", "myOtherStyle" ]
}
````

Nicht jede Style-Property kann auf jedes Element angewandt werden. So kann z.B.
``flexDirection`` nur mit einer FlexBox verwendet werden.

Beispiel für einen animierten Style mit hover Bedingung aus title_screen.onj:
````json5
styles: [
    {
        name: "button",
        properties: [
            relWidth(10.0),
            fontScaleTo(buttonBaseScale, 0.1, interpolation.elastic),
            // hover() erstellt eine StyleCondition, die nur wahr ist wenn über ein element
            // gehovert wird. Das self() gibt an das über das Element selbst gehovert werden
            // muss. Die condition infix funktion verknüpft die styleProperty mit der
            // StyleCondition
            fontScaleTo(buttonHoverScale, 0.1, interpolation.elastic) condition hover(self()),
            margin(0.0, 0.0, 0.3, 0.0)
        ]
    }
]
````

> Notiz: <br>
> Da das Styling System noch sehr neu ist, sind noch nicht alle notwendigen
> StyleProperties implementiert

### Behaviours
Behaviours werden verwendet, um spezielle Aktionen zu implementieren, die ausgeführt
werden, wenn man über einen Actor hovert oder auf einen klickt. Diese sind in der
Behaviours.kt Datei zu finden. Zusätzlich befindet sich hier das BehaviourFactory
Objekt, das von dem ScreenBuilder verwendet wird, um Behaviours zu erstellen. Um ein
neues Behaviour hinzuzufügen, muss eine Klasse die Behaviour extended erstellt werden,
ein Eintrag in dem BehaviourFactory Object geschrieben werden, und das screen2.onjschema
muss um das Behaviour erweitert werden.

### ScreenController
Für Fälle in dem die Funktionen des ScreenBuilders nicht ausreichen, können
ScreenController verwendet werden. Diese können Kotlin-Code verwenden, um den Screen
weiter zu manipulieren.

## ResourceManager

------

Der ResourceManager wird zum Verwalten von Assets verwendet. Hier wird ein Approach
ähnlich zu Reference-Counting verwendet.

### Randnotiz zu Assets
Libgdx vertraut nicht auf GC für das Verwalten des Speichers für Assets, da das sehr
ineffizient währe. Stattdessen muss der Speicher für alle Assets mittels der
.dispose() funktion im Disposable interface manuell freigegeben werden. Wird das nicht
gemacht, entsteht ein Memory-Leak. Um potenzielle Bugs zu vermeiden und diesen Prozess
zu automatisieren, wurde der ResourceManager erstellt.

### Definieren welche Assets existieren
Der ResourceManager liest die config/assets.onj Datei, die definiert, welche Assets
existieren, welchen Namen sie haben, welchen Typ sie haben und wo in der Dateistruktur
sie liegen. Damit ein Asset im Programm zur Verfügung steht, muss zuerst in dieser Datei
ein Eintrag hinzugefügt werden.

### Funktion des ResourceManager
Um ein Asset zu verwenden, muss es zuerst ausgeborgt werden. Das dürfen nur Klassen, 
die das ResourceBorrower interface implementieren. Danach kann das Asset mittels der
.get() funktion geholt werden. Nachdem das Asset nicht mehr benötigt wird, muss es 
zurückgegeben werden. Das Objekt, das die Resource ausgeborgt hat, muss sie auch 
zurückgeben. Intern merkt sich der ResourceManager welches Objekt sich welche Resource
ausgeborgt hat und ladet/disposed sie dementsprechend. Assets werden über ein Handle
(=String) identifiziert.

### Verwendung
Der ResourceManager is ein ``object`` (=Singleton) und damit im gesamten Programm
erreichbar.
Beispiel:
````kotlin

class SomeClass(
    private val textureHandle: String
) : SomeOtherClass(), ResourceBorrower {
    
    fun init() {
        ResourceManager.borrow(this, textureHandle)
    }
    
    fun update() {
        // Der generische Typ Parameter gibt den Typ des Assets an.
        // Ein Asset kann mehrere Varianten mit verschieden Typen haben,
        // z.B. Eine Textur kann als Texture, TextureRegion oder Drawable
        // geholt werden
        val texture = ResourceManager.get<Drawable>(this, textureHandle)
        /*
        ... Do something with texture
        */
    }
    
    fun end() {
        ResourceManager.giveBack(this, textureHandle)
    }
    
}
````

## Struktur des Spiels

------

_relevantes package: game_

### Die Game Components

_relevantes package: screen.gameComponents_

Die GameComponents sind Actors für spiel-spezifische Elemente wie der Revolver,
die CardHand, die CoverStacks, etc. Sie werden von ScreenBuilder instanziiert und 
vom GameController verwaltet.

### Der GameController
Die Hauptklasse die den Großteil der Spiellogik ausführt. Sie merkt sich den aktuellen
Stand des Kampfes, führt Aktionen (wie z.B. den Revolver schießen) durch und verwaltet
die Game Components. Bei dem GameController handelt es sich um einen ScreenController
(siehe ScreenController). Außerdem beinhaltet er ein enum names Gamephase, bei dem es 
sich um eine finite state machine handelt. Diese verwaltet die Spielphasen (Zug des 
Spielers, Zug des Gegners, Karten ziehen, etc.).

### SaveState und das savefile
Das savefile (saves/savefile.onj) speichert den Fortschritt des aktuellen Run des
Spielers. Das inkludiert Information wie: Leben, welche Karten ausgesucht wurden aber
auch Statistiken wie bis jetzt verwendete Reserves und besiegte Gegner. Das SaveState
Objekt verwaltet dieses savefile, stellt die Information zur Verfügung und erlaubt es
sie zu verändern.

### GraphicsConfig
Das GraphicsConfig Objekt liest Information wie Länge von Animationen, Geschwindigkeiten
von Animation, etc. aus einem Config-file ein und stellt sie dem restlichen Programm
zur Verfügung. Außerdem stellt es einige Convenience-Funktionen zur Verfügung die gleich
die Animation erstellen.

>Notiz: <br>
> Ich bin nicht hunderprotzentig zufrieden mit dem Code in der Klasse, 
> vielleicht fällt mir irgendwann mal was besseres ein

### GameAnimations
Die meisten Animationen sind mittels LibGdx Actions implementiert, da diese aber
relativ limitiert sind, sind für einige Effekte GameAnimations nötig. Das betrifft vor
allem Effekte die über den ganzen Screen gehen, wie z.B. die Banner-Animation oder der
Blut-Effekt, wenn der Spieler Schaden nimmt. Diese Animationen werden von der
statt vom Screen von der GameController Klasse verwaltet.

### Karten

_relevanter namespace für onj-dateien: Card_

Die Karten werden über die cards/cards.onj Datei verwaltet. Wenn Dinge wie Anzahl der 
Karten, Schaden oder Kosten geändert werden, bitte auch card_generator_config.onj
beachten (siehe Der CardGenerator). Anhand der in der Config-Datei angegeben Stats
werden CardPrototypes erstellt, die dann Karten dieses Types erzeugen. Z.B. ein 
CardPrototype für die Karte Incendiary Bullet wird erstellt, da der Spieler diese Karte
aber zweimal im Deck hat, wird dieser Prototype verwendet um zwei separate incendiary
bullets zu erstellen.

### Effekte und StatusEffekte

_relevanter namespace für onj-dateien: Card_

Karten können Effekte haben, die sich in irgendeiner Form auf das Spiel auswirken und
es interessanter machen. Ein Effekt muss immer einen Trigger haben, der angibt, wann
er auslöst. Das kann z.B. beim Start der Runde, nach dem Spielen der Karte oder vor 
dem Schießen sein. 

Beispiele für Effekte:

Effekte der FakeBullet:
```
effects: [
    // Die FakeBullet hat zwei reserveGain Effekte, einer löst aus wenn sie geschossen
    // wird, der ander wenn sich durch eine andere Quelle zerstört wird. Das ist im 
    // zweiten Parameter der Funktion definiert. Der letzte Parameter gibt an wie viele
    // Reserves der Spieler durch den Effekt bekommt.
    reserveGain("shot", 4),
    reserveGain("destroy", 4)
]
```

Effekte der BulletBulletBullet:
````json5
effects: [
    buffDmg("enter", bSelects.allBullets, 10)
]
````
Um festzustellen, welche Karten gebufft werden sollen, wird ein sogenanntes bSelect
verwendet. Diese sind in der cards.onj Datei in einer Variable definiert.
````json5
var bSelects = {
    allExceptSelf: bNum([1, 2, 3, 4, 5]),
    allBullets: bSelectByName("bullet"),
    fourButNotSelf: bNum([4])
};
````
Die bNum Funktion nimmt einen Array mit den Slot-Zahlen die gebufft werden sollen.
Das default-Verhalten ist das bNum nie die den Effekt auslösende Bullet buffen kann,
wenn dies aber passieren soll, kann der String 'this' in den Array hinzugefügt werden.

bSelectByName wählt alle Bullets aus, die den angegebenen Namen haben.

Effekte können aber auch StatusEffekte auslösen. Ein solcher StatusEffekt ist z.B. der
Burning Status, der den Schaden um 50% erhöht. StatusEffekte sind immer nur eine 
begrenzte Zeit aktiv, diese ist Revolver-Turns angegeben. Beispiel: Wenn der Gegner den
Effekt Burning(3) hat und der Spieler den Revolver schießt (auch wenn er leer schießt)
wird der Effekt zu Burning(2) verringert.

> Notiz: <br>
> Aktuell kann nur der Gegner von StatusEffekten betroffen sein, es ist aber geplant
> das auch der Spieler StatusEffekte haben kann

### Trait Effekte
Trait Effekte sind spezielle Effekte, die eher eine Eigenschaft der Karte beschreiben, 
als wirklich ein Effekt zu sein. Beispiele dafür sind der rotten Effekt (durch den die
Bullet bei jeder Revolver-Rotation an Schaden verliert) oder der left-turning Effekt
(durch den die Bullet den Revolver nach links statt nach rechts dreht).

### Der Gegner

Das Verhalten des Gegners ist aktuell sehr simpel. In der game_config.onj Datei
werden die Aktionen des Gegners definiert.

Beispiel aus game_config.onj (Auszug):
````json5
 $DamagePlayerEnemyAction {
    weight: 4,
    min: 5,
    max: 10,
    ...(graphicsConfig.enemyConfig.damagePlayerIcon)
},

$AddCoverEnemyAction {
    weight: 2,
    min: 1,
    max: 10,
    ...(graphicsConfig.enemyConfig.addCoverIcon)
},
````

Hier werden die möglichen Aktionen mit ihren Stats und jeweils einem Weight definiert.
Der Gegner wählt dann unter Berücksichtigung der Weights eine zufällige Aktion aus.

> Notiz: <br>
> Spätestens wenn es verschiedene Gegnertypen geben wird, wird das Gegnerverhalten
> komplexer werden


### Der CardGenerator

Um den Schreiben des Schadens und der Kosten auf die Karte zu automatisieren wurde der
CardGenerator geschrieben. Dieser wird durch die card_generator_config.onj Datei 
konfiguriert. Er kann gestartet werden, indem die drei Zeilen in der .create() Funktion
des FourtyFive Objektes einkommentiert werden.

Wenn Schaden, CoverValue oder Kosten einer Karte geändert werden, ist es notwending,
dass der CardGenerator einmal ausgeführt wird.

Wenn eine neue Karte hinzugefügt oder eine alte entfernt wird, ist es notwendig, dass
die config-Datei geupdatet wird und der CardGenerator einmal ausgeführt wird.

## Utility Klassen

------

_relevantes package: utils_

### Logger
LibGdx hat zwar einen Logger eingebaut, dieser kann aber zum Beispiel nicht zu Dateien
schreiben, deswegen wurde ein eigener implementiert. Dieser ist unter FourtyFiveLogger
Objekt erreichbar. Dieser kann mittels einer Config-Datei konfiguriert werden (siehe
Assets und Config files). 

### TemplateString
TemplateStrings können verwendet werden, wenn Strings mit Parametern interpoliert werden
müssen. Diese können z.B. mit dem TemplateLabel Actor verwendet werden.

Beispiel aus loose_screen.onj:
````json5
$TemplateLabel {
    properties: [
        position("absolute"),
        position(0.0, null, 36.0, null)
    ],
    align: "left",
    // Die geschwungenen Klammern makieren eine Stelle die durch einen Parameter ersetzt
    // werden muss
    template: "enemies killed: {stat.lastRun.enemiesDefeated}\nreserves used: {stat.lastRun.usedReserves}",
    font: "red_wing",
    color: color.black,
    fontScale: 0.2
}
````

Parameter können auf verschiedene Weisen mit Werten verbunden werden.

Über den TemplateString Konstruktor:
`````kotlin
val templateString = TemplateString(
    "{param1}, {param2}",
    mapOf(
        "param1" to 35,
        "param2" to true
    )
)
val interpolatedString = templateString.string
`````
Dieser Approach ist vor allem für Situationen gedacht, in denen die Parameter nur
für einen TemplateString gebraucht werden oder sich nicht mit der Zeit ändern.

Eine andere Möglichkeit ist es, dass templateParam property delegate zu verwenden.
Das ist vor allem nützlich, wenn der Wert sich mit der Zeit ändert.

Beispiel aus SaveState.kt:
````kotlin

    // Das erste Argument ist der Name des Parameters, der zweite
    // der ursprüngliche Wert der Property
    var lastRunEnemiesDefeated: Int by templateParam("stat.lastRun.enemiesDefeated", 0)

    // Zusätzlich kann die templateParam funktion ein lambda nehmen, dass ausgeführt
    // wird, wenn der Wert gesetzt wird.
````

Wenn mehrere Parameter von einem Wert abhängen, kann die multipleTemplateParam
Funktion verwendet werden. Die ersten beiden Argumente funktionieren wie bei der 
templateParam Funktion, allerdings nimmt sie auch beliebig viele zusätzliche Werte vom
Typ ``Pair<String, (T) -> Any?>``. Der erste Wert des Pairs ist der Name des Parameters,
der zweite erlaubt es den Wert zu einem anderen zu mappen, der dann als Parameter
gebunden wird.

Beispiel aus GameController.kt
````kotlin
    private var remainingCardsToDraw: Int? by multipleTemplateParam(
        "game.remainingCardsToDraw", null,
        "game.remainingCardsToDrawPluralS" to { if (it == 1) "" else "s" }
    )
````

### Timeline
Timeline ist eine Utility Klasse die für das Timen von z.B. Animation verwendet werden
kann. Eine Instanz der Timeline Klasse wir üblicherweise mit der Timeline.timeline()
funktion erzeugt. Diese Funktion nimmt ein Lambda das angibt wie die Timeline gebaut
werden soll.

Beispiel:
`````kotlin
val timeline = Timeline.timeline {
    val animation = getSomeAnimation()
    
    // die action Funktion für eine neue Aktion zur Timeline hinzu die einfach ein
    // lambda ausführt
    action { animation.start() }

    // delayUntil fügt eine Aktion hinzu, die die nachfolgenden Aktionen stoppt,
    // bis die Bedingung in dem Lambda true ist
    delayUntil { animation.isFinished }

    // Achtung: Diese Zeile wird trotzdem sofort ausgeführt, da sie nicht Teil einer
    // Aktion ist!
    println("hi")
    
    // Dieses println würde erst ausgeführt werden wenn die vorherige Animation
    // fertig ist
    action { println("hi") }
    
    // include fügt alle Aktionen einer anderen Timeline zu dieser hinzu
    include(otherTimeline)
    
    // delay stoppt nachfolgende Aktionen für eine gewisse Anzahl an ms
    delay(500)
    
    // includeLater funktioniert wie include mit einer Bedingung die angibt, ob die 
    // timeline inkludiert werden soll. Außerdem sind die timeline als auch die 
    // Bedingung in lambdas gewrappt, die erst ausgeführt werden, wenn diese Aktion 
    // an der Reihe ist. Das ist in Situationen nützlich, bei denen eine der
    // vorangehenden Aktionen die Timeline oder Bedingung beeinflussen.
    includeLater(
        { getSomeTimeline() },
        { someCondition }
    )
    
}
`````

## Assets und Config files

------



## Coding Conventions

------
