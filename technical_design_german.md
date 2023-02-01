![logo](./assets/textures/title_screen/logo.png)

# .fourty-five Technisches Design

## Aufbauen und Stylen von Screens

_siehe screen.general package_

### ScreenBuilder

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

### Styling
TODO

## ResourceManager
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

## Assets und Config files

## Coding Conventions
