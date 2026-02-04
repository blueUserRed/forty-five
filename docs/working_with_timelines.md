
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
