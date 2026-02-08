
# Working with timelines

### Usecase
In .Forty-Five, there are a lot of instances where actions occur sequentially, and an action may
take an unknown amount of time. For example, consider what happens when the player presses the shoot button
on the encounter screen:
- shoot sound plays
- on-shot effects for the bullet in slot 5 triggers (lets say silver bullet is in slot 5)
- the bullet animates its position and scaling to show that an effect is active
- orb animations plays to indicate that a card is being drawn from the stack
- card is put in the hand of the player
- silver bullet is put in the stack
- the enemy is damaged
- the revolver rotates

All of these actions need to happen sequentially when the revolver shoots. And it is hard to know what
happens ahead of time, because there may be all kinds of bullet effects, status effects, encounter
modifier, etc. that further alter the sequence of events. Manually keeping track of everything would
be a nightmare.

Timelines are a utility class used to represent sequences of events like this. They allow the code to build
up sequences action by action and then execute everything at the right time.

### Creating timelines

Timelines are usually created using the ``Timeline.timeline {}`` function. This functions provides a simple
DSL for writing timelines.

````kotlin
Timeline.timeline {
    action {
        // this code runs when the action is reached
        println("hello")
    }
    delay(1000) // waits for one second
    action {
        // this code runs last, when both the first action and the
        // delay have finished
        println("world")
    }
    // including a timeline adds all actions of that timeline
    // to this one
    include(someOtherTimeline)
}
````

### The biggest sources of errors

When working with timelines, it is extremely important to closely keep track of when exactly
which piece of code runs.

````kotlin
Timeline.timeline {
    print("1") // functions here run immediately when the timeline is created!
    action { print("3") } // runs as soon as the timeline is started
    delay(100)
    print("2") // also runs when the timeline is constructed, directly after 1!
    action { print("4") } // runs after the timeline was started and the delay is over
}
````
outputs: ``1234``

Especially when writing timelines for the Encounter, it is also important
to consider when each variable is declared and if the data could be outdated by the time it's used.

**Bad!!:**
````kotlin
fun getSomeTimeline(controller: GameController, card: Card) = Timeline.timeline {
    include(controller.drawCardsTimeline(2))
    // this is evaluated immediately when the timeline is created, not when
    // it is run and definitely not when this part in the timeline is reached!
    val cardsInHand = controller.cardsInHand.size
    // the damagePlayerTimeline is also created immediately when the
    // timeline is created, resulting in an incorrect damage value
    // when this part of the timeline is reached
    include(controller.damagePlayerTimeline(damage = cardsInHand))
}
````

**Good:**
````kotlin
fun getSomeTimeline(controller: GameController, card: Card) = Timeline.timeline {
    include(controller.drawCardsTimeline(2))
    later {
        // later delays the execution of the block until this part of the
        // timeline is reached
        val cardsInHand = controller.cardsInHand.size
        include(controller.damagePlayerTimeline(damage = cardsInHand))
    }
    
    // Alternative:
 
    // includeLater includes a timeline, but delays the creation of the timeline   
    includeLater({ controller.damagePlayerTimeline(damage = controller.cardsInHand.size) })
}
````
**Bad!!:**
````kotlin
fun getSomeTimeline(controller: GameController, card: Card) = Timeline.timeline {
    later {
        val cardsInHand = controller.cardsInHand.size
        include(controller.controller.drawCardsTimeline(cardsInHand / 2))
        val inSlot5 = controller.revolver.getCardInSlot(5)
        if (inSlot5 != null) include(controller.bounceBulletTimeline(inSlot5))
        
        // note that inSlot5 is initialized when 'later' is reached, but used
        // after the drawCardsTimeline finished (even though it appears later in the code!).
        // At first this doesn't seem to be an issue, because drawing cards doesn't change
        // the bullet in slot 5, but remember that pretty much every function in the
        // controller can trigger all kinds of effects, that could easily modify the
        // state of the revolver. Don't ever assume that including a timeline doesn't
        // mess with the internal state!
    }
}
````
**Good:**
````kotlin
fun getSomeTimeline(controller: GameController, card: Card) = Timeline.timeline {
    later {
        val cardsInHand = controller.cardsInHand.size
        include(controller.controller.drawCardsTimeline(cardsInHand / 2))
    }
    // Splitting everything up into two separate 'later' calls solves this issue, because the 
    // second later waits for the first
    later {
        val inSlot5 = controller.revolver.getCardInSlot(5)
        if (inSlot5 != null) include(controller.bounceBulletTimeline(inSlot5))
    }
}
````

### Usage of timelines with the GameController

The GameController, the effect system and the modifier system make heavy use
of timelines to manage what is going on in the game. The GameController keeps 
tack of exactly one main timeline. _All_ actions that somehow affect, depend, or
change the gamestate happen on this timeline. This ensures all action and animations
are always properly sequenced, the behaviour is predictable and that no race
conditions occur. The ``appendMainTimeline`` function can be used to append
to this timeline, but you will rarely need to call this function yourself.
Usually, when working with bullet effects or encounter modifier you just
override functions that return a timeline, and some other logic handles 
appending it.

In addition to the main timeline the GameController also has
potentially multiple animation timelines. Animations timelines run in 
parallel to each other and the main timeline and should only ever be used for
unimportant things that don't affect any state like, as the name suggests, animations.
Often animations are handled on the main timeline anyway, because often
actions on the main timeline are supposed to wait for the animation to finish.

### Other usages of timelines

Although the GameController is the most heavy user of timelines, timelines are
useful everywhere where there are a lot of different animations to handle. If
you want to use timelines on a screen you can either code the logic yourself
(Remember to call `updateTimeline` every frame) or you can use the
``TimelineController``, a ScreenController that manages timelines in a
similar way to the GameController.
