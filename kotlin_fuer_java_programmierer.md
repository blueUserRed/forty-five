
# Kotlin für Leute die kleine Kinder fressen

------

Das ist noch sehr unfertig, weiß auch nicht ob ich es fertig schreiben werde

Der Sinn dieses Dokumentes ist es, eine Kurzeinführung in Kotlin bzw. etwas zum 
Nachschlagen bei Fragen zu sein. Dieses Dokument ist nicht vollständig, im Zweifel 
Google befragen.

## Unterschiede im grundlegenden Syntax

````kotlin

// Funktionen werden mit dem fun-Keyword deklariert
fun aFunction() {
    
    // Variablen werden entweder mit val oder var deklariert
    // entspricht let und const in javascript
    val variable = true // Strichpunkte sind nicht notwendig
    
    // Typen von Variablen werden inferred, können aber auch explizit angegeben werden
    val myString: String = ""
    // Typen werden prinzipiell immer nach der Stuktur mit einem Doppelpunkt angegeben
    
    // ifs schauen aus wie in java
    if (variable) {
        println("was true")
    } else {
        println("was false")
    }
    // while, do/while und try/catch auch
    
    // Variablen können mit $ in Strings interpoliert werden
    val four = 4
    println("my favourite number is $four")
    
    // Wenn notwendig, können geschwungene Klammern verwendet werden
    println("the number after $four is ${four + 1}")
    
}

````

## For loop und ranges

Kotlin hat keinen Standard C-Style for-loop, sondern kann nur über Iterables iterieren,
ähnlich wie in python.

```kotlin

val list = listOf( // listOf erstellt einfach eine liste
    "hi", "hi2", "h3", "hi4"
)

for (item in list) {
    println(item)
}

// Oder über eine Zahlenrange

// der Doppelpunkt erschafft ein range von 0 (inklusiv) zu 10 (inklusiv)
for (i in 0..10) {
    println(i)
}

// Wird eine Zahlenrange gebraucht, beidem die zweite Zahl exklusiv ist, 
// kann die unitl infix funktion verwendet werden
for (i in 0..(list.size)) println(i)

// über jedes zweite Element loopen
for (i in 0..(list.size) step 2) println(i)

```

## Funktion

````kotlin

// normal Funktion
fun concatStrings(s1: String, s2: String): String {
    return s1 + s2
}

// generische Funktion
fun <T> getFirstElement(collection: Collection<T>): T {
    return collection[0]
}

// Wenn eine Funktion sofort returnt, kann auch ein '=' nach die Funktion geschrieben
// werden.
// Diese Funktion ist äquivalent zur ersten:
fun concatStrings(s1: String, s2: String): String = s1 + s2

// extension functions
// Mit extension functions kann eine Funktion zu einer bereits existierenden Klasse
// hinzugefügt werden. (Achtung: Die Funktion hat trotzdem keinen Zugriff auf 
// private member)
fun String.print() {
    println(this)
}

fun useExtensionFunction() {
    "Hello World".print()
}
````

## When (=Switch)
````kotlin
val someVar = "hi"

when (someVar) {
    // breaks sind nicht notwendig
    "hi" -> println("bye")
    "bye" -> {
        prinln("hi")
    }
    else -> println("I didn't understand that")
}
````

## Strukturen als Expressions benutzen
````kotlin
fun example() {
    
    val variable = if (1 == 1) {
        "everything normal"
    } else {
        "the universe is broken"
    }
    
    // kotlin hat keine ternaries, da einfach ein if verwendet werden kann
    
    // try kann auch als Expression verwendet werden
    
    val someVar = try {
        someFunctionThatCanFail()
    } catch (e: SomeException) {
        null
    }
    
    // when
    
    val monthOfYear = when (month) {
        "January" -> 1
        "February" -> 2
        "March" -> 3
        //....
    }
    
}
````

## Collections
Anders als Java unterscheidet Kotlin zwischen immutable Collections (die sich nicht
ändern können) und mutable Collections (die geändert werden können).

````kotlin

// Diese map ist immutable
val myMap: Map<String, Int> = mapOf()
// Diese map kann verändert werden
val myMutableMap: MutableMap<String, Int> = mutableMapOf()

myMutableMap["key"] = 1 // das währe mit myMap nicht möglich

````
